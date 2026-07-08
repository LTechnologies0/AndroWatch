plugins {
    id("com.android.library")
}

android {
    namespace = "ltechnologies.onionphone.androwatch.collector.assembly"
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
    api(project(":collector:tier-passive"))
    api(project(":collector:tier-permissioned"))
    api(project(":collector:tier-advanced"))
    testImplementation("junit:junit:4.13.2")
}
