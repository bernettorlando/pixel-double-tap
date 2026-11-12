package com.bernettorlando.pixeldoubletap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.bernettorlando.pixeldoubletap.R
import com.bernettorlando.pixeldoubletap.prefs.TapPreferences
import com.bernettorlando.pixeldoubletap.service.ScreenLockAccessibilityService
import com.bernettorlando.pixeldoubletap.ui.MainActivity

class TransparentTapWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            bindWidgetViews(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TAP) {
            handleWidgetTap(context)
            // Ensure PendingIntent stays fresh across system restarts
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TransparentTapWidgetProvider::class.java)
            )
            for (id in ids) {
                bindWidgetViews(context, manager, id)
            }
        }
    }

    private fun handleWidgetTap(context: Context) {
        val now = SystemClock.uptimeMillis()
        val timeout = TapPreferences.getInstance(context)
            .settings.value.doubleTapTimeoutMs.toLong()

        val delta = now - lastWidgetTapUptimeMs
        if (lastWidgetTapUptimeMs > 0L && delta in 55L..timeout) {
            lastWidgetTapUptimeMs = 0L
            val locked = ScreenLockAccessibilityService.requestLockNow()
            if (!locked) {
                Toast.makeText(
                    context,
                    "Enable Pixel Double Tap in Accessibility Settings first",
                    Toast.LENGTH_SHORT
                ).show()
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } else {
            lastWidgetTapUptimeMs = now
        }
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.bernettorlando.pixeldoubletap.ACTION_WIDGET_TAP"

        @Volatile
        private var lastWidgetTapUptimeMs: Long = 0L

        private fun bindWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val tapIntent = Intent(context, TransparentTapWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_transparent_tap).apply {
                setOnClickPendingIntent(R.id.widget_tap_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
