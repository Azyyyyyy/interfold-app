# Idempotent setup for the dedicated interfold-it-fcm Firebase project used by
# friend-front integration tests. Writes credentials under %USERPROFILE%\.interfold
# (never the repo). VAPID remains console-only.
param(
    [string]$ProjectId = "interfold-it-fcm",
    [string]$AndroidPackage = "app.interfold.InterfoldApp",
    [string]$IosBundle = "app.interfold.InterfoldApp"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$gcloud = Join-Path $env:LOCALAPPDATA "Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
if (-not (Test-Path $gcloud)) {
    $gcloudCmd = Get-Command gcloud -ErrorAction SilentlyContinue
    if ($gcloudCmd) { $gcloud = $gcloudCmd.Source }
    else { throw "gcloud not found. Install Google Cloud SDK and re-open the shell." }
}
$firebase = Get-Command firebase -ErrorAction SilentlyContinue
if (-not $firebase) { throw "firebase-tools not found. npm install -g firebase-tools" }

$outDir = Join-Path $env:USERPROFILE ".interfold"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$saPath = Join-Path $outDir "it-fcm-sa.json"
$webPath = Join-Path $outDir "it-fcm-web.json"
$androidPath = Join-Path $outDir "it-fcm-android.json"
$iosPath = Join-Path $outDir "it-fcm-ios.plist"

Write-Host "Ensuring Firebase project $ProjectId..."
$listed = & firebase projects:list --json 2>$null | ConvertFrom-Json
$exists = $false
if ($listed) {
    $exists = @($listed.result) + @($listed) | Where-Object { $_.projectId -eq $ProjectId } | Select-Object -First 1
}
if (-not $exists) {
    & firebase projects:create $ProjectId --display-name "Interfold IT FCM"
    if ($LASTEXITCODE -ne 0) { throw "firebase projects:create failed" }
}

function Ensure-App([string]$Platform, [string]$Extra) {
    $apps = & firebase apps:list $Platform --project $ProjectId --json 2>$null | ConvertFrom-Json
    if ($apps -and $apps.result -and @($apps.result).Count -gt 0) { return }
    if ($Platform -eq "WEB") { & firebase apps:create WEB "it-web" --project $ProjectId }
    elseif ($Platform -eq "ANDROID") { & firebase apps:create ANDROID $Extra --project $ProjectId }
    elseif ($Platform -eq "IOS") { & firebase apps:create IOS $Extra --project $ProjectId }
}

Ensure-App WEB ""
Ensure-App ANDROID $AndroidPackage
Ensure-App IOS $IosBundle

function Write-SdkConfig([string]$Platform, [string]$Dest) {
    $tmp = Join-Path $env:TEMP "it-fcm-$Platform-$PID.txt"
    & firebase apps:sdkconfig $Platform --project $ProjectId | Set-Content -Path $tmp -Encoding utf8
    if (Test-Path $tmp) { Copy-Item $tmp $Dest -Force }
}

Write-SdkConfig WEB $webPath
Write-SdkConfig ANDROID $androidPath
Write-SdkConfig IOS $iosPath

& $gcloud services enable fcm.googleapis.com firebase.googleapis.com --project $ProjectId

$saEmail = "firebase-adminsdk-fbsvc@$ProjectId.iam.gserviceaccount.com"
$saList = & $gcloud iam service-accounts list --project $ProjectId --format="value(email)"
if (-not ($saList -match [regex]::Escape($saEmail))) {
    $found = ($saList | Where-Object { $_ -like "firebase-adminsdk*@$ProjectId.iam.gserviceaccount.com" } | Select-Object -First 1)
    if ($found) { $saEmail = $found }
    else {
        & $gcloud iam service-accounts create firebase-adminsdk-fbsvc --project $ProjectId --display-name "Firebase Admin SDK"
        $saEmail = "firebase-adminsdk-fbsvc@$ProjectId.iam.gserviceaccount.com"
    }
}
if (-not (Test-Path $saPath)) {
    & $gcloud iam service-accounts keys create $saPath --iam-account $saEmail --project $ProjectId
}

$webRaw = if (Test-Path $webPath) { Get-Content $webPath -Raw } else { "" }
$hasVapid = $webRaw -match '"vapid_key"\s*:\s*"B'
if (-not $hasVapid) {
    Write-Host ""
    Write-Host "VAPID is console-only. Firebase console -> Project settings -> Cloud Messaging -> Web Push certificates -> Generate key pair."
    Write-Host "Paste vapid_key into $webPath then re-run this script."
    throw "vapid_key missing from $webPath"
}

Write-Host ""
Write-Host "Set these for Gradle ITs (do not commit the JSON files):"
Write-Host "`$env:INTERFOLD_IT_FCM_SERVICE_ACCOUNT_JSON = Get-Content '$saPath' -Raw"
Write-Host "`$env:INTERFOLD_IT_FIREBASE_WEB_JSON = Get-Content '$webPath' -Raw"
Write-Host "./gradlew :shared:desktopIntegrationTest `"-Dinterfold.backend.image=interfold-api:dev`""
