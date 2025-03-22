package org.davidrevolt.artmoney.service

import android.app.ActivityManager
import android.content.Context
import org.davidrevolt.artmoney.model.ProcessInfo

class AndroidProcessManager(private val context: Context) : ProcessManager {
    override suspend fun getRunningProcesses(): List<ProcessInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return emptyList()

        return runningProcesses.map { process ->
            ProcessInfo(
                pid = process.pid,
                name = process.processName ?: "Unknown"
            )
        }
    }
}

