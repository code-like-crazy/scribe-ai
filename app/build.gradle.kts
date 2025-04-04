import java.util.Properties
import java.io.FileInputStream

// Load properties from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties") // Reference root project's file
if (localPropertiesFile.exists()) {
    try {
        localProperties.load(FileInputStream(localPropertiesFile))
    } catch (e: Exception) {
         println("Warning: Could not read local.properties file: ${e.message}")
    }
} else {
    println("Warning: local.properties file not found at ${localPropertiesFile.absolutePath}. API keys might be missing.")
}

// Function to safely get property, returning an empty string if not found
fun getApiKeyProperty(key: String): String {
    return localProperties.getProperty(key, "") // Provide default empty string
}


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")  
}

android {
    namespace = "com.example.scribeai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scribeai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Retrieve API key from local.properties and add to BuildConfig
        // Use the function defined above to safely get the property
        val geminiApiKey = getApiKeyProperty("GEMINI_API_KEY")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // Enable View Binding and BuildConfig
    buildFeatures {
        viewBinding = true
        buildConfig = true // Enable BuildConfig generation
    }
}

dependencies {
    // Existing dependencies...
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx) // Add activity-ktx for viewModels delegate
    implementation(libs.androidx.constraintlayout)

    // === Add these new dependencies ===
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit (OCR)
    implementation(libs.google.mlkit.text.recognition)

    // Room Database
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Glide (Image loading)
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // Testing (existing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // Gemini AI
    implementation(libs.google.ai.generativeai)
}
