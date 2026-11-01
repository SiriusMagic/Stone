param(
  [switch]$Install
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$env:NODE_ENV = "production"

$root = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $root "android"
$apkDir = Join-Path $androidDir "app\build\outputs\apk\release"
$adb = "C:\Users\ZIRAKUSA\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$packageName = "com.zirakusa.stone"

Push-Location $androidDir
try {
  .\gradlew assembleRelease
} finally {
  Pop-Location
}

$apk = Get-ChildItem $apkDir -Filter "*arm64-v8a-release.apk" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if ($null -eq $apk) {
  throw "No se encontro un APK release arm64-v8a en $apkDir"
}

if ($Install) {
  & $adb install -r $apk.FullName
}

Write-Host ""
Write-Host "APK listo:"
Write-Host $apk.FullName
