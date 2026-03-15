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
        versionCode = 144
        versionName = "1.0.144"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // ✅ 배포용 디버그 빌드에서도 용량 최적화를 위해 활성화합니다.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
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

    // 🖼️ Glide for Premium Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // 🔗 QR Code Generation (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // 🕵️ EXIF Metadata for Backup & Restore
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // 💰 Google Play Billing Library (Premium Stickers)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
