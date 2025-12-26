#Requires -Version 7.0
<#
.SYNOPSIS
    Builds and installs an APK on an Android device via ADB.

.DESCRIPTION
    This script builds an APK using Gradle and installs it on a connected Android device using ADB.
    It checks for device connectivity and handles errors gracefully.

.PARAMETER ApkPath
    Path to the APK file to install. Defaults to: app\build\outputs\apk\debug\app-debug.apk

.PARAMETER AdbPath
    Path to the ADB executable. If not specified, the script will search for ADB automatically.

.PARAMETER BuildType
    Build type for Gradle (debug or release). Defaults to: debug

.PARAMETER SkipBuild
    Skip the build step and only install the existing APK.

.PARAMETER GradleWrapperPath
    Path to the Gradle wrapper script. Defaults to: .\gradlew.bat

.EXAMPLE
    .\install_apk.ps1
    
.EXAMPLE
    .\install_apk.ps1 -BuildType release
    
.EXAMPLE
    .\install_apk.ps1 -SkipBuild -ApkPath "custom\path\app.apk"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    
    [Parameter(Mandatory = $false)]
    [string]$AdbPath = "",
    
    [Parameter(Mandatory = $false)]
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",
    
    [Parameter(Mandatory = $false)]
    [switch]$SkipBuild,
    
    [Parameter(Mandatory = $false)]
    [string]$GradleWrapperPath = ".\gradlew.bat"
)

$ErrorActionPreference = "Stop"

function Write-Success {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Green
}

function Write-ErrorMessage {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Cyan
}

function Find-AdbPath {
    param([string]$UserSpecifiedPath)
    
    # Try user-specified path first
    if ($UserSpecifiedPath -and (Test-Path -Path $UserSpecifiedPath -PathType Leaf)) {
        return $UserSpecifiedPath
    }
    
    Write-Info "Searching for ADB executable..."
    
    # Common ADB locations
    $possiblePaths = @(
        # Standalone Platform Tools (installed separately)
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\Android SDK\platform-tools\adb.exe",
        "$env:ProgramFiles(x86)\Android\android-sdk\platform-tools\adb.exe",
        
        # Android Studio SDK locations
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        
        # Old path (duplicated platform-tools) - kept for backward compatibility
        "$env:LOCALAPPDATA\platform-tools\platform-tools\adb.exe",
        
        # Check if ADB is in PATH
        (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
    )
    
    foreach ($path in $possiblePaths) {
        if ($path -and (Test-Path -Path $path -PathType Leaf)) {
            Write-Info "Found ADB at: $path"
            return $path
        }
    }
    
    return $null
}

function Invoke-ProjectBuild {
    param(
        [string]$BuildType,
        [string]$GradleWrapperPath
    )
    
    Write-Info "`nBuilding APK ($BuildType)..."
    Write-Info "This may take a few minutes..."
    
    $commonGradleArgs = @("-Dorg.gradle.vfs.watch=false")

    # Clean previous build output
    Write-Info "Cleaning previous build..."
    & $GradleWrapperPath $commonGradleArgs "clean" 2>&1 | Out-Host
    
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Clean step completed with warnings. Continuing with build..."
    }
    
    # Build APK
    Write-Info "Assembling APK..."
    $buildTypeCapitalized = $BuildType.Substring(0,1).ToUpper() + $BuildType.Substring(1)
    & $GradleWrapperPath $commonGradleArgs "assemble$buildTypeCapitalized" 2>&1 | Out-Host
    
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMessage "Build failed. Please check the errors above."
        exit 1
    }
    
    Write-Success "✓ Build completed successfully!"
    
    # Return the expected APK path
    return "app\build\outputs\apk\$BuildType\app-$BuildType.apk"
}

try {
    $defaultApkPath = "app\build\outputs\apk\debug\app-debug.apk"
    $isDefaultPath = ($ApkPath -eq $defaultApkPath)
    
    # Build APK if not skipped
    if (-not $SkipBuild) {
        # Validate Gradle wrapper
        if (-not (Test-Path -Path $GradleWrapperPath -PathType Leaf)) {
            Write-ErrorMessage "Gradle wrapper not found at: $GradleWrapperPath"
            Write-Info "Please ensure you are running this script from the project root directory."
            exit 1
        }
        
        # Build the APK
        $builtApkPath = Invoke-ProjectBuild -BuildType $BuildType -GradleWrapperPath $GradleWrapperPath
        
        # Update ApkPath if using default path, otherwise keep user-specified path
        if ($isDefaultPath) {
            $ApkPath = $builtApkPath
        }
    }
    
    # Validate APK path
    if (-not (Test-Path -Path $ApkPath -PathType Leaf)) {
        Write-ErrorMessage "APK not found at: $ApkPath"
        if ($SkipBuild) {
            Write-Info "APK not found. Try running without -SkipBuild to build it first."
        } else {
            Write-Info "Build completed but APK not found at expected location."
            Write-Info "Please check the build output above for errors."
        }
        exit 1
    }

    # Find and validate ADB path
    $resolvedAdbPath = Find-AdbPath -UserSpecifiedPath $AdbPath
    
    if (-not $resolvedAdbPath) {
        Write-ErrorMessage "ADB not found!"
        Write-Info ""
        Write-Info "ADB (Android Debug Bridge) is required to install APKs."
        Write-Info "You can install it in several ways:"
        Write-Info ""
        Write-Info "Option 1: Install Platform Tools standalone:"
        Write-Info "  Download from: https://developer.android.com/tools/releases/platform-tools"
        Write-Info "  Extract to: $env:LOCALAPPDATA\Android\Sdk\platform-tools"
        Write-Info ""
        Write-Info "Option 2: Use Android Studio SDK Manager:"
        Write-Info "  Go to: Tools, then SDK Manager, then SDK Tools tab, select Android SDK Platform-Tools"
        Write-Info ""
        Write-Info "Option 3: Specify ADB path manually:"
        Write-Info "  .\install_apk.ps1 -AdbPath `"C:\path\to\adb.exe`""
        Write-Info ""
        exit 1
    }
    
    $AdbPath = $resolvedAdbPath

    # Check device connection
    Write-Info "Checking device connection..."
    $devices = & $AdbPath devices 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMessage "Failed to execute ADB. Please check if ADB is properly installed."
        exit 1
    }
    
    Write-Host $devices
    
    # Check if any device is connected
    $deviceList = $devices | Select-String "device$" | Where-Object { $_ -notmatch "List of devices" }
    
    if ($deviceList.Count -eq 0) {
        Write-ErrorMessage "No Android device found. Please connect a device and enable USB debugging."
        exit 1
    }

    # Install APK
    Write-Info "`nInstalling APK: $ApkPath"
    & $AdbPath install -r $ApkPath 2>&1 | Out-Host
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "`n✓ APK installed successfully!"
        Write-Info "You can now test the app on your device."
    } else {
        Write-ErrorMessage "`n✗ Installation failed. Check the errors above."
        exit 1
    }
}
catch {
    Write-ErrorMessage "An error occurred: $($_.Exception.Message)"
    exit 1
}

