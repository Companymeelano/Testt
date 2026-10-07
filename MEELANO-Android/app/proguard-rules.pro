# Meelano Native Android: no custom rules required.

# Phase-7T: R8 enabled. jTDS reflects over its own internals — keep it whole.
-keep class net.sourceforge.jtds.** { *; }
-dontwarn net.sourceforge.jtds.**
# org.json is platform API; nothing to keep. SQL strings are runtime data, not code.
-keepclassmembers class ir.meelano.android.** {
    @android.webkit.JavascriptInterface <methods>;
}
