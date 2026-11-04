package com.mounsokdara.zyphvert.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder for the background conversion service (declared in the manifest as a
 * `dataSync` foreground service). Nothing starts it yet; the real implementation
 * (notification + FFmpeg job runner) lands with the FFmpeg pipeline milestone.
 */
class ConversionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No work to do yet; never leave a started-but-idle service running.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
