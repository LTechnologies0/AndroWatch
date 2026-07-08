plugins {
    id("com.android.library")
}

android {
    namespace = "ltechnologies.onionphone.androwatch.collector.tier.passive"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":collector:engine"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
