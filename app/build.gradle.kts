plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.hnoni777.newdatemapdiary"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hnoni777.newdatemapdiary"
        minSdk = 26
        targetSdk = 35
        versionCode = 172
        versionName = "1.15.3 Beta"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/user/Documents/herewithyou.jks")
            storePassword = "godqhr3216!"
            keyAlias = "key0"
            keyPassword = "godqhr3216!"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // ✅ [코부장 긴급 수선] IDE에서의 원활한 설치와 디버깅을 위해 true로 변경
            isDebuggable = true 
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ❌ Compose 절대 없음
    buildFeatures {
        viewBinding = true
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

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")


    // ✅ 카카오 지도 SDK (Vector Map, 공식)
    implementation("com.kakao.maps.open:android:2.13.0")

    // 🔗 카카오톡 공유 SDK (지도 초대장 보내기)
    implementation("com.kakao.sdk:v2-share:2.18.0")

    // 🖼️ Glide for Premium Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // 🔗 QR Code Generation (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // 🕵️ EXIF Metadata for Backup & Restore
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // 💰 Google Play Billing Library (Premium Stickers)
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // 🤖 ML Kit Selfie Segmentation (Face Stickers)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mlkit:face-detection:16.1.6")
}
