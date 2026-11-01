param(
  [switch]$Clean,
  [switch]$Install
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$env:NODE_ENV = "production"

$root = Split-Path -Parent $PSScriptRoot
$android = Join-Path $root "android"
$output = Join-Path $root "dist"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gradle = Join-Path $android "gradlew.bat"
$gradleFile = Join-Path $android "app\build.gradle"
$version = (Select-String -Path $gradleFile -Pattern 'versionName\s+"([^"]+)"').Matches[0].Groups[1].Value

Push-Location $android
try {
  if ($Clean) { & $gradle clean }
  & $gradle assembleRelease
} finally {
  Pop-Location
}

$apk = Get-ChildItem (Join-Path $android "app\build\outputs\apk\release") -Filter "*arm64-v8a-release.apk" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $apk) { throw "No se encontro el APK arm64 de release." }

New-Item -ItemType Directory -Force -Path $output | Out-Null
$target = Join-Path $output "Stone-$version-arm64-v8a.apk"
Copy-Item -LiteralPath $apk.FullName -Destination $target -Force

if ($Install) {
  if (!(Test-Path $adb)) { throw "ADB no se encontro en $adb" }
  & $adb install -r $target
}

Write-Host "APK listo: $target"
