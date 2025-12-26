# Script to upload TypeQ25 APK release to GitHub
# Usage: .\upload_release.ps1 -GitHubToken "your_token_here"
param(
    [Parameter(Mandatory=$false)]
    [string]$GitHubToken = $env:GITHUB_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$RepoOwner = "",
    
    [Parameter(Mandatory=$false)]
    [string]$RepoName = "",
    
    [Parameter(Mandatory=$false)]
    [string]$Version = "0.1-alpha",
    
    [Parameter(Mandatory=$false)]
    [string]$Tag = "v0.1-alpha"
)

$APKPath = "app\build\outputs\apk\release\app-release.apk"

# Check if APK exists
if (-not (Test-Path $APKPath)) {
    Write-Host "ERROR: APK not found at $APKPath" -ForegroundColor Red
    Write-Host "Please build the release APK first with: .\gradlew assembleRelease" -ForegroundColor Yellow
    exit 1
}

# Check for GitHub token
if ([string]::IsNullOrEmpty($GitHubToken)) {
    Write-Host "ERROR: GitHub Token is required!" -ForegroundColor Red
    Write-Host "Usage: .\upload_release.ps1 -GitHubToken 'your_token_here'" -ForegroundColor Yellow
    Write-Host "Or set environment variable: `$env:GITHUB_TOKEN = 'your_token_here'" -ForegroundColor Yellow
    exit 1
}

# Try to get repo info from git if not provided
if ([string]::IsNullOrEmpty($RepoOwner) -or [string]::IsNullOrEmpty($RepoName)) {
    try {
        $gitRemote = git remote get-url origin 2>$null
        if ($gitRemote -match 'github\.com[:/](.+?)/(.+?)(?:\.git)?$') {
            if ([string]::IsNullOrEmpty($RepoOwner)) {
                $RepoOwner = $Matches[1]
            }
            if ([string]::IsNullOrEmpty($RepoName)) {
                $RepoName = $Matches[2] -replace '\.git$', ''
            }
            Write-Host "Detected repository: $RepoOwner/$RepoName" -ForegroundColor Green
        }
    } catch {
        Write-Host "Could not detect repository from git remote" -ForegroundColor Yellow
    }
}

# Verify repo info
if ([string]::IsNullOrEmpty($RepoOwner) -or [string]::IsNullOrEmpty($RepoName)) {
    Write-Host "ERROR: Repository information is missing!" -ForegroundColor Red
    Write-Host "Please provide: -RepoOwner 'username' -RepoName 'repo-name'" -ForegroundColor Yellow
    exit 1
}

# Prepare API headers
$headers = @{
    "Authorization" = "token $GitHubToken"
    "Accept" = "application/vnd.github.v3+json"
}

# Create release body
$releaseBody = @{
    tag_name = $Tag
    name = "TypeQ25 $Version"
    body = "Release Alpha 0.1 - First alpha release of TypeQ25 keyboard`n`n**Installation:**`n1. Download the APK`n2. Enable 'Install from unknown sources' in Android settings`n3. Install the APK`n4. Go to Settings → System → Languages & input → Virtual keyboard → Manage keyboards`n5. Enable 'TypeQ25 Physical Keyboard'"
    draft = $false
    prerelease = $true
} | ConvertTo-Json

Write-Host "Creating GitHub release..." -ForegroundColor Yellow
try {
    $releaseResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$RepoOwner/$RepoName/releases" -Method Post -Headers $headers -Body $releaseBody
    Write-Host "Release created successfully!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to create release" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message -ForegroundColor Red
    }
    exit 1
}

# Prepare upload URL
$uploadUrl = $releaseResponse.upload_url -replace '\{.*\}', ''
$uploadUrl += "?name=app-release.apk"

# Read APK file
Write-Host "Uploading APK..." -ForegroundColor Yellow
try {
    $fileBytes = [System.IO.File]::ReadAllBytes($APKPath)
    
    $uploadHeaders = @{
        "Authorization" = "token $GitHubToken"
        "Content-Type" = "application/vnd.android.package-archive"
    }
    
    $uploadResponse = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $fileBytes
    Write-Host "APK uploaded successfully!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to upload APK" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message -ForegroundColor Red
    }
    exit 1
}

Write-Host "`n=== Release published successfully! ===" -ForegroundColor Green
Write-Host "Release URL: $($releaseResponse.html_url)" -ForegroundColor Cyan
Write-Host "APK Download: $($uploadResponse.browser_download_url)" -ForegroundColor Cyan
Write-Host "`nYou can now share this release with users!" -ForegroundColor Yellow

