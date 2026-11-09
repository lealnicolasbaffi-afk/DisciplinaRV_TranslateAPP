plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.2.0"
}

android {
    namespace = "br.edu.tradutoroffline"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "br.edu.tradutoroffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("Configuration/AndroidManifest.xml")
            java.setSrcDirs(listOf("Sources"))
            res.setSrcDirs(listOf("Resources/res"))
            assets.setSrcDirs(listOf("Resources/assets"))
        }
        getByName("test") {
            java.setSrcDirs(listOf("Tests"))
            resources.setSrcDirs(listOf("Tests/fixtures"))
        }
        getByName("androidTest") {
            java.setSrcDirs(listOf("DeviceTests"))
            // Downloaded integration fixtures belong only to the test APK.
            assets.setSrcDirs(listOf("work/models"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    testOptions.unitTests.isReturnDefaultValues = false
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.alphacephei:vosk-android:0.3.75")
    // Explicit AAR, as required by Vosk's Android demo and published POM.
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
