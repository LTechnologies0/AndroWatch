plugins {
    id("com.android.library")
}

android {
    namespace = "ltechnologies.onionphone.androwatch.collector.engine"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

apply(from = rootProject.file("gradle/onionphone-sources.gradle"))

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":collector:contract"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.tracing:tracing-ktx:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
