plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.petcareapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.petcareapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
     // Import the BoM for the Firebase platform
     implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
     // Add the dependency for the Firebase Authentication library
     implementation("com.google.firebase:firebase-auth")
    // Add the dependency for the Realtime Database library
    //implementation("com.google.firebase:firebase-database")
    implementation ("com.google.firebase:firebase-firestore")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    //implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}