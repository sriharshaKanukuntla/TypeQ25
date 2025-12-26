# Script to upload TypeQ25 APK release to GitHub using GitHub CLI (gh)
# Usage: .\upload_release_gh.ps1
param(
    [Parameter(Mandatory=$false)]
    [string]$Version = "0.1-alpha",
    
    [Parameter(Mandatory=$false)]
    [string]$Tag = "v0.1-alpha",
    
    [Parameter(Mandatory=$false)]
    [string]$Title = "TypeQ25 0.1-alpha",
    
    [Parameter(Mandatory=$false)]
    [string]$Notes = "Release Alpha 0.1 - First alpha release of TypeQ25 keyboard`n`n**Installation:**`n1. Download the APK`n2. Enable 'Install from unknown sources' in Android settings`n3. Install the APK`n4. Go to Settings → System → Languages & input → Virtual keyboard → Manage keyboards`n5. Enable 'TypeQ25 Physical Keyboard'"
)

$APKPath = "app\build\outputs\apk\release\app-release.apk"

# Check if APK exists
if (-not (Test-Path $APKPath)) {
    Write-Host "ERROR: APK not found at $APKPath" -ForegroundColor Red
    Write-Host "Please build the release APK first with: .\gradlew assembleRelease" -ForegroundColor Yellow
    exit 1
}

# Try to find gh in common locations (checked in order)
$ghPaths = @(
    "C:\Program Files\GitHub CLI\gh.exe",  # Most common Windows installation path
    "gh",                                    # If in PATH
    "$env:ProgramFiles\GitHub CLI\gh.exe",
    "$env:ProgramFiles(x86)\GitHub CLI\gh.exe",
    "$env:LOCALAPPDATA\GitHub CLI\gh.exe",
    "$env:USERPROFILE\.local\bin\gh.exe"
)

$ghCmd = $null
foreach ($path in $ghPaths) {
    if (Test-Path $path) {
        $ghCmd = $path
        Write-Host "Found GitHub CLI at: $path" -ForegroundColor Green
        break
    }
    # Also try Get-Command for PATH-located commands
    if ($path -eq "gh" -and (Get-Command $path -ErrorAction SilentlyContinue)) {
        $ghCmd = $path
        Write-Host "Found GitHub CLI in PATH" -ForegroundColor Green
        break
    }
}

if (-not $ghCmd) {
    Write-Host "ERROR: GitHub CLI (gh) not found!" -ForegroundColor Red
    Write-Host "Please make sure GitHub CLI is installed and available in PATH." -ForegroundColor Yellow
    Write-Host "After installation, you may need to restart your terminal." -ForegroundColor Yellow
    Write-Host "`nAlternatively, use the REST API script: .\upload_release.ps1" -ForegroundColor Cyan
    exit 1
}

# Check authentication
Write-Host "Checking GitHub authentication..." -ForegroundColor Yellow
$authStatus = & $ghCmd auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "You are not authenticated with GitHub CLI." -ForegroundColor Yellow
    Write-Host "Running 'gh auth login'..." -ForegroundColor Yellow
    & $ghCmd auth login
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Authentication failed!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host $authStatus -ForegroundColor Green
}

# Check if tag already exists
Write-Host "Checking if tag $Tag already exists..." -ForegroundColor Yellow
$existingTags = & $ghCmd release list --limit 100 2>$null | Select-String $Tag
if ($existingTags) {
    Write-Host "Tag $Tag already exists. Do you want to update it? (y/n)" -ForegroundColor Yellow
    $response = Read-Host
    if ($response -ne 'y' -and $response -ne 'Y') {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
    # Delete existing release if it exists
    Write-Host "Deleting existing release..." -ForegroundColor Yellow
    & $ghCmd release delete $Tag --yes 2>$null
}

# Create release with APK
Write-Host "Creating release $Tag..." -ForegroundColor Yellow
Write-Host "Title: $Title" -ForegroundColor Cyan
Write-Host "APK: $APKPath" -ForegroundColor Cyan

$notesFile = [System.IO.Path]::GetTempFileName()
Set-Content -Path $notesFile -Value $Notes

try {
    & $ghCmd release create $Tag `
        "$APKPath" `
        --title $Title `
        --notes-file $notesFile `
        --prerelease
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n=== Release published successfully! ===" -ForegroundColor Green
        
        # Get release URL
        $repo = & $ghCmd repo view --json nameWithOwner -q .nameWithOwner
        Write-Host "Release URL: https://github.com/$repo/releases/tag/$Tag" -ForegroundColor Cyan
        Write-Host "`nYou can now share this release with users!" -ForegroundColor Yellow
    } else {
        Write-Host "ERROR: Failed to create release" -ForegroundColor Red
        exit 1
    }
} finally {
    # Clean up temp file
    Remove-Item $notesFile -ErrorAction SilentlyContinue
}

