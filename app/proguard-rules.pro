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