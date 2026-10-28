import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.services)
}

// Release signing values live in local.properties (not in git) or env vars:
//   axpense.storeFile, axpense.storePassword, axpense.keyAlias, axpense.keyPassword
//   AXPENSE_STORE_FILE, AXPENSE_STORE_PASSWORD, AXPENSE_KEY_ALIAS, AXPENSE_KEY_PASSWORD
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    localProps.getProperty(prop) ?: System.getenv(env)

val releaseStoreFile = signingValue("axpense.storeFile", "AXPENSE_STORE_FILE")

android {
    namespace = "com.apex.axpense"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.apex.axpense"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("axpense.storePassword", "AXPENSE_STORE_PASSWORD")
                keyAlias = signingValue("axpense.keyAlias", "AXPENSE_KEY_ALIAS")
                keyPassword = signingValue("axpense.keyPassword", "AXPENSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and resources and shortens names (much smaller APK).
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
} 
// androidComponents {
//     onVariants(selector().withBuildType("release")) { variant ->
//         variant.outputs.forEach { output ->
//             output.outputFileName.set("axpense-v${variant.versionCode.get()}.apk")
//         }
//     }
// }

kotlin {
    jvmToolchain(17)
}

ksp {
    // Export Room schemas so future migrations can be written and tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation.compose)

  // Room
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.room.ktx)

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)

  // Background work
  implementation(libs.androidx.work.runtime.ktx)

  // Firebase pulls in Fragment 1.1.0, which fails the release lint check for the ActivityResult API.
  implementation(libs.androidx.fragment)
}
