package com.bernettorlando.pixeldoubletap.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bernettorlando.pixeldoubletap.service.ScreenLockAccessibilityService
import com.bernettorlando.pixeldoubletap.system.PixelSystemHelper
import com.bernettorlando.pixeldoubletap.ui.MainActivity

class ScreenOffTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val locked = ScreenLockAccessibilityService.requestLockNow()
        if (!locked) {
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(openAppIntent)
            }
        }
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val enabled = PixelSystemHelper.isAccessibilityServiceEnabled(this)
        tile.state = if (enabled) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "Tap to sleep" else "Setup needed"
        }
        tile.updateTile()
    }
}
