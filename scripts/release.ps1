[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$VersionName,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$VersionCode,

    [string]$ReleaseNotes = "",
    [switch]$SkipGitee,
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

function Require-Environment([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing environment variable: $Name"
    }
    return $value
}

function Invoke-GiteeApi {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null
    )
    $headers = @{ Authorization = "token $script:giteeToken" }
    $params = @{ Method = $Method; Uri = $Uri; Headers = $headers; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 5)
    }
    return Invoke-RestMethod @params
}

function Set-GradleProperty([string]$Path, [string]$Name, [string]$Value) {
    $content = Get-Content -LiteralPath $Path -Raw
    $pattern = "(?m)^$([regex]::Escape($Name))=.*$"
    if ($content -notmatch $pattern) {
        throw "Property not found: $Name"
    }
    $content = [regex]::Replace($content, $pattern, "$Name=$Value")
    [IO.File]::WriteAllText($Path, $content, [Text.UTF8Encoding]::new($false))
}

if ((git status --porcelain)) {
    throw "Working tree is not clean. Commit or stash changes before releasing."
}

$versionFile = Join-Path $repoRoot "gradle.properties"
Set-GradleProperty $versionFile "app.versionCode" $VersionCode
Set-GradleProperty $versionFile "app.versionName" $VersionName

$tag = "v$VersionName"
if (git tag --list $tag) {
    throw "Git tag already exists: $tag"
}

$gradle = Join-Path $repoRoot "tools/gradle-8.4/bin/gradle.bat"
if (-not (Test-Path $gradle)) {
    $gradle = "gradle"
}

Require-Environment "RELEASE_STORE_PASSWORD" | Out-Null
Require-Environment "RELEASE_KEY_ALIAS" | Out-Null
Require-Environment "RELEASE_KEY_PASSWORD" | Out-Null
if (-not $SkipGitee) {
    $script:giteeToken = Require-Environment "GITEE_TOKEN"
}

& $gradle ":app:assembleRelease" "--no-daemon"
if ($LASTEXITCODE -ne 0) { throw "Release build failed." }

$apk = Join-Path $repoRoot "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apk)) { throw "Release APK not found: $apk" }
$publishedName = "CallPulse-$tag-release.apk"
$sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$notes = if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) { "CallPulse $VersionName`n`nSHA-256: $sha256" } else { "$ReleaseNotes`n`nSHA-256: $sha256" }

git add gradle.properties
git commit -m "发布 CallPulse $VersionName"
if ($LASTEXITCODE -ne 0) { throw "Version commit failed." }
git tag -a $tag -m "CallPulse $VersionName"
if ($LASTEXITCODE -ne 0) { throw "Tag creation failed." }

if (-not $SkipPush) {
    git push origin master
    if ($LASTEXITCODE -ne 0) { throw "Source push failed." }
    git push origin $tag
    if ($LASTEXITCODE -ne 0) { throw "Tag push failed." }
}

if (-not $SkipGitee) {
    $releaseUri = "https://gitee.com/api/v5/repos/klsing/call-pulse/releases"
    $release = Invoke-GiteeApi "Post" $releaseUri @{
        tag_name = $tag
        name = "CallPulse $VersionName"
        body = $notes
        prerelease = $false
        target_commitish = "master"
    }

    $uploadUri = "https://gitee.com/api/v5/repos/klsing/call-pulse/releases/$($release.id)/attach_files"
    $client = [Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("token", $script:giteeToken)
    $form = [Net.Http.MultipartFormDataContent]::new()
    $stream = [IO.File]::OpenRead($apk)
    try {
        $fileContent = [Net.Http.StreamContent]::new($stream)
        $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new("application/vnd.android.package-archive")
        $form.Add($fileContent, "file", $publishedName)
        $response = $client.PostAsync($uploadUri, $form).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Gitee APK upload failed: $($response.StatusCode) $($response.Content.ReadAsStringAsync().GetAwaiter().GetResult())"
        }
    } finally {
        $stream.Dispose()
        $form.Dispose()
        $client.Dispose()
    }
}

Write-Host "Released CallPulse $VersionName ($tag)"
Write-Host "APK: $publishedName"
Write-Host "SHA-256: $sha256"
