plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dan.ose11p"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dan.ose11p"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "5.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
    testImplementation("junit:junit:4.13.2")
}