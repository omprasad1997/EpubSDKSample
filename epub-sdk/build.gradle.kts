plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("maven-publish")
}

android {
    namespace = "com.example.omireadersdk.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "com.github.yourusername"
                artifactId = "omireader-sdk"
                version    = "1.0.0"
            }
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.xpp3)
    testImplementation(libs.kotlinx.coroutines.test)
}