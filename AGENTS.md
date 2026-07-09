# AGENTS.md

## Cursor Cloud specific instructions

AndroWatch is an Android (Kotlin/Jetpack Compose) app; it is a multi-module Gradle
project (`app` shell + `core:*` + `collector:*` + `feature:*`). Standard commands and
architecture live in `README.md`, `docs/`, and `.github/workflows/ci.yml` — refer to
those rather than duplicating here.

The Cloud VM is already provisioned by the startup/update flow: OpenJDK 17 and 21, the
Android SDK at `$HOME/android-sdk` (with `ANDROID_SDK_ROOT`/`ANDROID_HOME`/`PATH`
exported in `~/.bashrc`), and a generated `local.properties` (gitignored) pointing
`sdk.dir` at that SDK.

- **JDK: use 17.** CI builds AndroWatch with Temurin 17, and the modules pin
  `JavaVersion.VERSION_17` / `jvmTarget = 17`. Run Gradle with
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`. (The default shell `java` is 21;
  Gradle picks up whatever `JAVA_HOME` you export.)
- **Build (debug APK):** `./gradlew :app:assembleDebug -Ponionphone.devAbi=arm64-v8a --no-daemon`
  → `app/build/outputs/apk/debug/app-debug.apk`. The `-Ponionphone.devAbi` flag is the
  CI convention (see `gradle/abi-release.gradle`); `assembleDebug` also works without it.
- **Unit tests:** `./gradlew test --no-daemon` (Robolectric/JUnit; completes in well
  under a minute).
- **Lint:** `./gradlew :app:lintDebug --no-daemon` (may abort on pre-existing findings;
  reports under `app/build/reports/`).

Non-obvious caveats:
- `compileSdk`/`targetSdk` are **37**; the installed SDK platforms are `android-37.0`
  and `android-37.1` (there is no plain `platforms;android-37`). AGP 9.1.1 resolves
  these correctly, so do not "fix" the version.
- This is a headless VM with **no `/dev/kvm`**, so a hardware-accelerated Android
  emulator cannot run. Verify runtime behavior via unit/Robolectric tests and by
  inspecting the built APK (e.g. `$ANDROID_SDK_ROOT/build-tools/37.0.0/aapt dump badging <apk>`),
  which is how CI validates the app.
