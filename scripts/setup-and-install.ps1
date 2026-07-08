#Requires -Version 5.1
<#
.SYNOPSIS
  Setup complet AndroWatch : Java, SDK, ADB → compile → choix téléphone → install.
.EXAMPLE
  .\scripts\setup-and-install.ps1
  .\scripts\setup-and-install.ps1 -SkipSetup    # compile + install seulement
#>
[CmdletBinding()]
param(
    [switch]$SkipSetup,
    [switch]$SkipTests,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\AndroidEnv.ps1')

Write-Host ''
Write-Host '========================================' -ForegroundColor Magenta
Write-Host '  AndroWatch - install pour debutants' -ForegroundColor Magenta
Write-Host '========================================' -ForegroundColor Magenta
Write-Host ''

try {
    if (-not $SkipSetup) {
        Write-Step "Étape 1/4 — Java (JDK 21)"
        Ensure-Java

        Write-Step "Étape 2/4 — Android SDK + ADB"
        $sdk = Ensure-AndroidSdk
        Write-ProjectSdkConfig -RepoRoot $RepoRoot -SdkRoot $sdk -JdkHome $env:JAVA_HOME
    } else {
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
    }

    Write-Step "Étape 3/4 — Compilation"
    Push-Location $RepoRoot
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) { throw "gradlew.bat introuvable. Ouvre le dossier AndroWatch." }

    $userProps = Join-Path $RepoRoot 'gradle.properties.user'
    if (Test-Path $userProps) {
        $extra = Get-Content $userProps -Raw
        if ($extra -match 'org\.gradle\.java\.home=(.+)') {
            $env:JAVA_HOME = $Matches[1].Trim()
            $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
        }
    }

    & $gradlew assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Compilation échouée" }
    Write-Ok "APK debug compilé"

    if (-not $SkipTests) {
        & $gradlew test --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Tests échoués" }
        Write-Ok "Tests OK"
    }

    Write-Step "Étape 4/4 — Installation sur ton téléphone"
    $serial = Select-AdbDevice -PreferredSerial $DeviceSerial
    if (-not $serial) { exit 1 }

    $apk = Join-Path $RepoRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Install-ApkToDevice -ApkPath $apk -Serial $serial

    Write-Host ""
    Write-Host "Terminé. Lance « AndroWatch » sur le téléphone." -ForegroundColor Green
} catch {
    Write-Fail $_.Exception.Message
    exit 1
} finally {
    Pop-Location -ErrorAction SilentlyContinue
}
