plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.eggplant.detector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eggplant.detector"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        buildConfigField(
            "String",
            "CLOUD_API_BASE_URL",
            (providers.gradleProperty("EGGPLANT_API_BASE_URL").orNull ?: "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            (providers.gradleProperty("EGGPLANT_SUPABASE_URL").orNull ?: "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            (providers.gradleProperty("EGGPLANT_SUPABASE_PUBLISHABLE_KEY").orNull ?: "").asBuildConfigString(),
        )
    }

    ndkVersion = "28.2.13676358"

    buildTypes {
        release {
            // Produces a directly installable thesis-demo APK without committing credentials.
            // Replace this local debug key with a private release key before store distribution.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("demo") {
            initWith(getByName("release"))
            // Thesis distribution build: release-equivalent behavior, explicitly
            // debug-signed so the APK is installable without committing a key.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)
    // Room 2.8.4's migration test runtime uses serialization-json 1.8.1.
    // Align core to avoid loading the incompatible Lifecycle transitive 1.7.3 runtime.
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
