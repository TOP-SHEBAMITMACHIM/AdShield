# VpnService is instantiated by the system.
-keep class com.adshield.app.vpn.AdVpnService { *; }
-keep class com.adshield.app.tile.AdShieldTileService { *; }
-keep class com.adshield.app.boot.BootReceiver { *; }

# WebView JavaScript interfaces are called from JS.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
