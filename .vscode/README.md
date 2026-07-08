# VS Code / Cursor — AndroWatch

## 1. Extensions

Au premier lancement, clique **Install** quand VS Code propose les extensions recommandées.

## 2. Terminal intégré

```powershell
.\scripts\setup-and-install.ps1
```

Ou double-clic **`INSTALL.bat`** à la racine du projet.

## 3. Raccourci clavier build

`Ctrl+Shift+B` → **AndroWatch: Setup complet + install téléphone**

## 4. Install rapide (déjà compilé)

Tâche : **AndroWatch: Install sur téléphone (choix appareil)**

## 5. Téléphone

- Options développeur → Débogage USB **ON**
- Branche câble USB
- Accepte popup RSA sur le téléphone
- Le script liste les appareils et te demande lequel choisir

## 6. Variables utiles (optionnel)

Le script remplit tout seul. Manuel :

| Variable | Exemple |
|----------|---------|
| `JAVA_HOME` | `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot` |
| `ANDROID_HOME` | `%USERPROFILE%\Android\Sdk` |

Fichiers générés (gitignored) : `local.properties`, `gradle.properties.user`
