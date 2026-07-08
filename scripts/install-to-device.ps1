#Requires -Version 5.1
<#
.SYNOPSIS
  Compile (si besoin) + menu choix téléphone + install APK.
.EXAMPLE
  .\scripts\install-to-device.ps1
  .\scripts\install-to-device.ps1 -NoBuild
#>
[CmdletBinding()]
param(
    [switch]$NoBuild,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\AndroidEnv.ps1')

$jdk = Find-Jdk21
if ($jdk) {
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$env:PATH"
}
$sdk = Find-SdkRoot
if ($sdk) {
    $env:ANDROID_HOME = $sdk
    $pt = Join-Path $sdk 'platform-tools'
    if (Test-Path $pt) { $env:PATH = "$pt;$env:PATH" }
}

Push-Location $RepoRoot
try {
    if (-not $NoBuild) {
        Write-Step 'Compilation debug'
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Compilation echouee' }
    }

    $serial = Select-AdbDevice -PreferredSerial $DeviceSerial
    if (-not $serial) { exit 1 }

    $apk = Join-Path $RepoRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Install-ApkToDevice -ApkPath $apk -Serial $serial
} finally {
    Pop-Location
}
