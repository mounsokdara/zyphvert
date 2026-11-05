package com.mounsokdara.zyphvert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mounsokdara.zyphvert.MainActivity
import com.mounsokdara.zyphvert.R
import com.mounsokdara.zyphvert.data.JobHistory
import com.mounsokdara.zyphvert.data.JobStatus
import com.mounsokdara.zyphvert.jobs.ConversionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps conversions alive while the app is in the background and shows progress in a notification. */
@AndroidEntryPoint
class ConversionForegroundService : Service() {
    @Inject lateinit var manager: ConversionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) manager.cancelAll()
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Starting…", null),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        scope.launch {
            JobHistory.jobs.collectLatest { jobs ->
                val running = jobs.firstOrNull { it.status == JobStatus.Running }
                val waiting = jobs.count { it.status == JobStatus.Queued }
                if (running == null && waiting == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val title = running?.let { "${it.toolTitle}: ${it.inputLabel}" } ?: "Waiting to start"
                    val text = if (waiting > 0) "$waiting more waiting" else "Converting…"
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(title, text))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Conversions", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun build(title: String, text: String?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ConversionForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    companion object {
        private const val CHANNEL = "conversions"
        private const val NOTIFICATION_ID = 1
        const val ACTION_CANCEL = "com.mounsokdara.zyphvert.CANCEL"

        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, ConversionForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }
    }
}
