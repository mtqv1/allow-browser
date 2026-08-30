# Keep WebView classes that are referenced via reflection
-keep class com.aalu.browser.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
