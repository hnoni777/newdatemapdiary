# Kakao Map SDK
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# Glide
-keep public class * extends com.github.bumptech.glide.module.AppGlideModule
-keep public class * extends com.github.bumptech.glide.module.LibraryGlideModule
-keep class com.github.bumptech.glide.** { *; }

# EXIF
-keep class androidx.exifinterface.** { *; }

# AndroidX Core (needed for some reflection)
-keep class androidx.core.app.CoreComponentFactory { *; }

# Kakao SDK v2
-keep class com.kakao.sdk.** { *; }
-keep interface com.kakao.sdk.** { *; }
-dontwarn com.kakao.sdk.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Native Libraries (needed for Kakao Maps)
-keep class com.kakao.vectormap.internal.** { *; }
-keepnames class com.kakao.vectormap.internal.** { *; }

# Suppress R8 warnings for optional dependencies
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**