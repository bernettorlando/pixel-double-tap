# Keep AccessibilityService, TileService, and AppWidgetProvider entrypoints intact under R8
-keep class com.bernettorlando.pixeldoubletap.service.ScreenLockAccessibilityService { *; }
-keep class com.bernettorlando.pixeldoubletap.tile.ScreenOffTileService { *; }
-keep class com.bernettorlando.pixeldoubletap.widget.TransparentTapWidgetProvider { *; }
-keepclassmembers class com.bernettorlando.pixeldoubletap.prefs.TapPreferences {
    private android.content.SharedPreferences$OnSharedPreferenceChangeListener listener;
}
