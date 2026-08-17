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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":collector:contract"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.tracing:tracing-ktx:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
