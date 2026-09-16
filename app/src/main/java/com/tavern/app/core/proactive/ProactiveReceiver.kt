package com.tavern.app.core.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 主动消息定时广播接收器
 */
@AndroidEntryPoint
class ProactiveReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: ProactiveEngine
    @Inject lateinit var appSettings: AppSettings

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                engine.checkAndMaybeSend()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 78231

        /** 安排下一次检查（应用启动与每次触发后调用） */
        fun schedule(context: Context, appSettings: AppSettings) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val intervalMs = TimeUnit.HOURS.toMillis(appSettings.proactiveIntervalH.value.toLong())
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                pending
            )
        }
    }
}
