# Fonctions partagées — env Java / SDK / ADB pour AndroWatch

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "OK  $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "!!  $Message" -ForegroundColor Yellow
}

function Write-Fail([string]$Message) {
    Write-Host "XX  $Message" -ForegroundColor Red
}

function Test-Command([string]$Name) {
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Winget {
    if (-not (Test-Command winget)) {
        throw "winget introuvable. Installe 'App Installer' depuis le Microsoft Store puis relance."
    }
}

function Install-WingetPackage([string]$Id, [string]$Label) {
    Ensure-Winget
    $q = winget list --id $Id --accept-source-agreements 2>$null
    if ($LASTEXITCODE -eq 0 -and $q -match [regex]::Escape($Id)) {
        Write-Ok "$Label déjà installé ($Id)"
        return
    }
    Write-Step "Installation $Label ($Id)…"
    winget install --id $Id -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        throw "Échec winget pour $Id"
    }
    Write-Ok "$Label installé"
}

function Find-Jdk21 {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*"
    ) | Where-Object { $_ }

    foreach ($c in $candidates) {
        if ($c -match '\*$') {
            $parent = $c -replace '\*$', ''
            $hit = Get-ChildItem -Path $parent -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | Select-Object -First 1
            if ($hit) { return $hit.FullName }
        } elseif (Test-Path (Join-Path $c 'bin\java.exe')) {
            return $c
        }
    }

    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) {
        $ver = & java -version 2>&1 | Out-String
        if ($ver -match 'version "21') {
            return (Split-Path (Split-Path $java.Source))
        }
    }
    return $null
}

function Ensure-Java {
    $jdk = Find-Jdk21
    if (-not $jdk) {
        Install-WingetPackage 'Microsoft.OpenJDK.21' 'OpenJDK 21'
        $jdk = Find-Jdk21
    }
    if (-not $jdk) {
        throw "JDK 21 introuvable après installation. Redémarre le terminal."
    }
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$env:PATH"
    Write-Ok "JAVA_HOME = $jdk"
    & "$jdk\bin\java.exe" -version
}

function Find-SdkRoot {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path $_) }
    return $candidates | Select-Object -First 1
}

function Find-SdkManager([string]$SdkRoot) {
    $paths = @(
        Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat',
        Join-Path $SdkRoot 'cmdline-tools\bin\sdkmanager.bat'
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }
    $found = Get-ChildItem -Path (Join-Path $SdkRoot 'cmdline-tools') -Recurse -Filter 'sdkmanager.bat' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($found) { return $found.FullName }
    return $null
}

function Ensure-AndroidCmdlineTools([string]$SdkRoot) {
    if (Find-SdkManager $SdkRoot) { return }

    Write-Step "Téléchargement Android command-line tools…"
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $zip = Join-Path $env:TEMP 'android-cmdline-tools.zip'
    $url = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    $extract = Join-Path $env:TEMP 'android-cmdline-tools'
    if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $extract -Force
    $dest = Join-Path $SdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Move-Item (Join-Path $extract 'cmdline-tools') $dest
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    Write-Ok "cmdline-tools installés"
}

function Ensure-AndroidSdk {
    Install-WingetPackage 'Google.PlatformTools' 'ADB (platform-tools)'

    $sdk = Find-SdkRoot
    if (-not $sdk) {
        $sdk = Join-Path $env:USERPROFILE 'Android\Sdk'
        New-Item -ItemType Directory -Force -Path $sdk | Out-Null
    }

    Ensure-AndroidCmdlineTools $sdk
    $sdkmanager = Find-SdkManager $sdk
    if (-not $sdkmanager) { throw "sdkmanager introuvable dans $sdk" }

    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk

    $yes = Join-Path $env:TEMP 'sdk-yes.txt'
    Set-Content -Path $yes -Value 'y' -NoNewline
    Write-Step "Installation SDK platform-35 + build-tools…"
    Get-Content $yes | & $sdkmanager --sdk_root=$sdk 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' | Out-Host
    Remove-Item $yes -Force -ErrorAction SilentlyContinue

  # ADB : winget platform-tools + SDK platform-tools — priorité au plus récent dans PATH
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    $adbPaths = @(
        $(if ($adbCmd) { $adbCmd.Source } else { $null }),
        (Join-Path $sdk 'platform-tools\adb.exe')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

    if ($adbPaths) {
        $adbDir = Split-Path $adbPaths[0]
        if ($env:PATH -notlike "*$adbDir*") {
            $env:PATH = "$adbDir;$env:PATH"
        }
    }

    Write-Ok "ANDROID_HOME = $sdk"
    return $sdk
}

function Write-ProjectSdkConfig([string]$RepoRoot, [string]$SdkRoot, [string]$JdkHome) {
    $localProps = Join-Path $RepoRoot 'local.properties'
    $escaped = $SdkRoot -replace '\\', '\\\\'
    Set-Content -Path $localProps -Value "sdk.dir=$escaped`n" -Encoding utf8
    Write-Ok "local.properties → $SdkRoot"

    $userProps = Join-Path $RepoRoot 'gradle.properties.user'
    Set-Content -Path $userProps -Value @(
        "org.gradle.java.home=$($JdkHome -replace '\\','\\')"
    ) -Encoding utf8
    Write-Ok "gradle.properties.user → JDK (gitignored)"
}

function Repair-AdbConnection {
    if (-not (Test-Command adb)) { return $false }
    Write-Warn 'ADB stale - redemarrage serveur...'
    adb kill-server 2>$null | Out-Null
    Start-Sleep -Seconds 1
    adb start-server 2>$null | Out-Null
    Start-Sleep -Seconds 1
    return $true
}

function Get-AdbDeviceLines {
    if (-not (Test-Command adb)) { return @() }
    return @(adb devices 2>$null |
        Select-Object -Skip 1 |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ })
}

function Test-InteractiveConsole {
    if (-not [Environment]::UserInteractive) { return $false }
    try {
        $hostUi = $Host.UI.RawUI
        return $null -ne $hostUi
    } catch {
        return $false
    }
}

function Get-AdbDevices {
    $result = @()
    foreach ($line in Get-AdbDeviceLines) {
        if ($line -match '^([^\s]+)\s+(\S+)') {
            $serial = $Matches[1]
            $state = $Matches[2]
            if ($state -eq 'device') {
                $model = (adb -s $serial shell getprop ro.product.model 2>$null).Trim()
                $brand = (adb -s $serial shell getprop ro.product.manufacturer 2>$null).Trim()
                $android = (adb -s $serial shell getprop ro.build.version.release 2>$null).Trim()
                $label = if ($model) { "$brand $model (Android $android)" } else { $serial }
                $result += [PSCustomObject]@{ Serial = $serial; Label = $label; State = 'device' }
            } else {
                $result += [PSCustomObject]@{ Serial = $serial; Label = $serial; State = $state }
            }
        }
    }
    return $result
}

function Select-AdbDevice([string]$PreferredSerial) {
    Write-Step 'Appareils USB / emulateur detectes'

    $forced = $PreferredSerial
    if (-not $forced) { $forced = $env:ANDROID_SERIAL }
    if ($forced) {
        $state = (adb -s $forced get-state 2>$null)
        if ($state) { $state = $state.Trim() }
        if ($state -eq 'device') {
            Write-Ok ('Serial force: {0}' -f $forced)
            return $forced
        }
        $shownState = if ($state) { $state } else { 'unknown' }
        Write-Warn ('Serial {0} pas pret (etat: {1})' -f $forced, $shownState)
    }

    $devices = @()
    for ($try = 1; $try -le 5; $try++) {
        $all = @(Get-AdbDevices)
        $devices = @($all | Where-Object { $_.State -eq 'device' })
        if ($devices.Count -gt 0) { break }

        $blocked = @($all | Where-Object { $_.State -ne 'device' })
        if ($blocked.Count -gt 0) {
            Write-Warn 'Etats ADB non prets:'
            foreach ($b in $blocked) {
                Write-Host ('  {0} -> {1}' -f $b.Serial, $b.State) -ForegroundColor Yellow
            }
            $states = @($blocked | ForEach-Object { $_.State })
            if (($states -contains 'offline') -or ($states -contains 'unauthorized')) {
                Repair-AdbConnection | Out-Null
                adb wait-for-device 2>$null | Out-Null
                Start-Sleep -Seconds 2
            }
        } else {
            Write-Warn ('Scan {0}/5: aucun appareil ADB' -f $try)
            Start-Sleep -Seconds 2
        }
    }

    $devices = @(Get-AdbDevices | Where-Object { $_.State -eq 'device' })
    if ($devices.Count -eq 0) {
        Write-Fail 'Aucun appareil pret (etat device). Debogage USB ON, popup RSA OK.'
        Write-Host '' -ForegroundColor Yellow
        Write-Host 'Checklist:' -ForegroundColor Yellow
        Write-Host '  1. Debogage USB ON (options developpeur)' -ForegroundColor Yellow
        Write-Host '  2. Cable donnees, pas charge seule' -ForegroundColor Yellow
        Write-Host '  3. Accepter popup RSA sur le telephone' -ForegroundColor Yellow
        Write-Host '  4. Si offline: adb kill-server; adb start-server' -ForegroundColor Yellow
        Write-Host '  5. Relance: .\scripts\install-to-device.ps1 -DeviceSerial TON_SERIAL' -ForegroundColor Yellow
        return $null
    }

    if ($devices.Count -eq 1) {
        Write-Ok ('Un seul appareil: {0} [{1}]' -f $devices[0].Label, $devices[0].Serial)
        return $devices[0].Serial
    }

    Write-Host ''
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host ('  [{0}] {1}' -f ($i + 1), $devices[$i].Label) -ForegroundColor White
        Write-Host ('       serial: {0}' -f $devices[$i].Serial) -ForegroundColor DarkGray
    }
    Write-Host ''

    if (-not (Test-InteractiveConsole)) {
        Write-Fail 'Plusieurs appareils mais pas de console interactive.'
        Write-Host 'Relance: .\scripts\install-to-device.ps1 -DeviceSerial SERIAL' -ForegroundColor Yellow
        return $null
    }

    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $raw = Read-Host ('Choisis le numero (1-{0})' -f $devices.Count)
        $n = 0
        if ($raw -and [int]::TryParse($raw.Trim(), [ref]$n) -and $n -ge 1 -and $n -le $devices.Count) {
            return $devices[$n - 1].Serial
        }
        Write-Warn 'Entree invalide.'
    }
    return $null
}

function Install-ApkToDevice([string]$ApkPath, [string]$Serial, [string]$AppLabel = 'AndroWatch') {
    if (-not (Test-Path $ApkPath)) { throw "APK absent: $ApkPath" }
    Write-Step ('Installation sur {0}...' -f $Serial)
    adb -s $Serial install -r $ApkPath
    if ($LASTEXITCODE -ne 0) {
        if ((adb -s $Serial get-state 2>$null) -match 'offline') {
            Repair-AdbConnection | Out-Null
            adb -s $Serial install -r $ApkPath
        }
    }
    if ($LASTEXITCODE -ne 0) { throw ('adb install a echoue (serial {0})' -f $Serial) }
    Write-Ok ('{0} installe sur {1}' -f $AppLabel, $Serial)
}
