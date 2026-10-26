# The page calls these by name through addJavascriptInterface (the fallback bridge on WebViews without
# WebMessageListener) - shrinking must not rename or drop them.
-keepclassmembers class io.voxtrics.sdk.internal.JavascriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}
