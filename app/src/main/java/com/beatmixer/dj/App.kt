package com.beatmixer.dj

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import java.io.File
import kotlin.system.exitProcess

/**
 * Safety net: records any crash to a file (shown on the next start) and, if the app
 * dies right after launching, quietly starts it again so the user never sees a "closed" screen.
 */
class App : Application() {
    private val born = SystemClock.elapsedRealtime()

    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash.txt").writeText("thread " + t.name + "\n" + android.util.Log.getStackTraceString(e).take(3000))
                val prefs = getSharedPreferences("beatmixer", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val early = SystemClock.elapsedRealtime() - born < 20_000L
                val lastAuto = prefs.getLong("autoRestartAt", 0L)
                if (early && now - lastAuto > 60_000L) {
                    prefs.edit().putLong("autoRestartAt", now).commit()
                    val i = packageManager.getLaunchIntentForPackage(packageName)
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        val pi = PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
                        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 400, pi)
                        Process.killProcess(Process.myPid())
                        exitProcess(10)
                    }
                }
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
