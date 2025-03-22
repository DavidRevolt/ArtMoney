package org.davidrevolt.artmoney.service

import org.davidrevolt.artmoney.model.ProcessInfo

interface ProcessManager {
    /**
     * @return The processes currently running
     * */
    suspend fun getRunningProcesses(): List<ProcessInfo>
}