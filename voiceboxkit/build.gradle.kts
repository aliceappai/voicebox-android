plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.voicebox.voiceboxkit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Required for Robolectric to load Android resources in unit tests.
            isIncludeAndroidResources = true
        }
    }

    // Required so JitPack / Maven can publish a clean `release` AAR.
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    // Material Components — needed for the View-side BottomSheetDialogFragment API.
    implementation(libs.material)

    // Compose API surface.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// JitPack coordinate -> com.github.aliceappai:voicebox-android:<tag>
// (Maven Central can be added later as an additional publication.)
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.voicebox"
            artifactId = "voiceboxkit"
            version = "1.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
