# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep JavascriptInterface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep our Room DB entities and classes
-keep class com.example.data.** { *; }

# Keep Jsoup classes and libraries
-keep class org.jsoup.** { *; }
