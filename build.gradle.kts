plugins {
    id("com.android.application") version "9.3.0" apply false
    id("com.android.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.dokka") version "2.2.0"
}

subprojects {
    pluginManager.withPlugin("com.android.library") {
        apply(plugin = "org.jetbrains.dokka")
    }
    pluginManager.withPlugin("com.android.application") {
        apply(plugin = "org.jetbrains.dokka")
    }
}

dependencies {
    dokka(project(":app"))
    dokka(project(":core:model"))
    dokka(project(":core:permission"))
    dokka(project(":collector:contract"))
    dokka(project(":collector:engine"))
    dokka(project(":collector:tier-passive"))
    dokka(project(":collector:tier-permissioned"))
    dokka(project(":collector:tier-advanced"))
    dokka(project(":collector:assembly"))
    dokka(project(":feature:export"))
}

dokka {
    dokkaPublications.html {
        moduleName.set("AndroWatch")
        moduleVersion.set(
            providers.gradleProperty("VERSION_NAME").orElse("0.1.0"),
        )
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

tasks.register("installToPhone") {
    group = "androwatch"
    description = "Build debug APK and install on the connected device via ADB"
    dependsOn(":app:installDebug")
}

tasks.register("buildAndInstall") {
    group = "androwatch"
    description = "Assemble debug APK then install on the connected device"
    dependsOn(":app:assembleDebug", ":app:installDebug")
}
