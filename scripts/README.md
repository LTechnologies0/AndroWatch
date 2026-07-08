# Installation express (débutants)

## Double-clic (Windows)

1. **`INSTALL.bat`** — installe Java + SDK + compile + te demande sur quel téléphone installer
2. **`INSTALL-PHONE.bat`** — recompile + menu téléphone seulement

## PowerShell

```powershell
cd AndroWatch
.\scripts\setup-and-install.ps1           # tout depuis zéro
.\scripts\setup-and-install.ps1 -SkipSetup   # compile + install si déjà setup
.\scripts\install-to-device.ps1 -NoBuild     # install APK existant
```

## Choix du téléphone

Si plusieurs appareils / émulateurs branchés :

```
  [1] Google Pixel 8 (Android 16)
       serial: ABC123
  [2] sdk_gphone64_x86_64 (Android 14)
       serial: emulator-5554

Choisis le numéro (1-2): 
```

Un seul appareil → sélection auto.

## Traductions (i18n)

```powershell
pip install deep-translator
python scripts/i18n/generate_translations.py
```

Interruption (Ctrl+C) → relance même commande : nettoie dossiers partiels auto.
Option `--workers 2` si erreurs Google Translate (rate limit).


- Windows 10/11
- `winget` (Microsoft Store → App Installer)
- Internet (télécharge JDK, SDK, dépendances Gradle)

## VS Code / Cursor

Voir [.vscode/README.md](.vscode/README.md) — `Ctrl+Shift+B` pour build + install.
