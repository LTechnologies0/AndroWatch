plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ltechnologies.onionphone.androwatch"
    compileSdk = 37

    defaultConfig {
        applicationId = "ltechnologies.onionphone.androwatch"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                rootProject.file("gradle/privacy-logging.pro"),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
apply(from = rootProject.file("gradle/abi-release.gradle"))

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(project(":core:model"))
    implementation(project(":core:permission"))
    implementation(project(":collector:assembly"))
    implementation(project(":feature:export"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

apply(from = rootProject.file("gradle/release-signing.gradle"))

tasks.register<Exec>("adbInstallDebug") {
    group = "androwatch"
    description = "Reinstall debug APK via adb install -r (requires adb in PATH)"
    dependsOn("assembleDebug")
    doFirst {
        val debugDir = layout.buildDirectory.get().dir("outputs/apk/debug").asFile
        val apkFile = debugDir.listFiles()
            ?.filter { it.extension == "apk" }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No debug APK found in ${debugDir.absolutePath}")
        commandLine("adb", "install", "-r", apkFile.absolutePath)
    }
}
