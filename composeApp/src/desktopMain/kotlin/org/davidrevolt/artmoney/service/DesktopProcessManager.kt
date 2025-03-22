package org.davidrevolt.artmoney.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.davidrevolt.artmoney.model.ProcessInfo
import org.davidrevolt.artmoney.search.SearchAlgorithm
import java.time.Instant


class DesktopProcessManager(private val ioDispatcher: CoroutineDispatcher) : ProcessManager {
    override suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(ioDispatcher) {
        val processList = mutableListOf<ProcessInfo>()
        ProcessHandle.allProcesses().forEach { handle ->
            try {
                val pid = handle.pid().toInt()
                val name = handle.info().command()
                name.ifPresent { processList.add(ProcessInfo(pid, it)) }
            } catch (e: Exception) {
                println("getRunningProcesses ${e.message}") // Skip processes we can't access
            }
        }
        processList
    }

    /*
        // JNA - SLOWER THAN ProcessHandle.allProcesses()
        override suspend fun getRunningProcessesUsingJNA(): List<ProcessInfo> = withContext(ioDispatcher) {
            val kernel32 = Kernel32.INSTANCE
            val psapi = Psapi.INSTANCE

            val startTime = System.nanoTime()
            val processList = mutableListOf<ProcessInfo>()

            // Buffer for process IDs (up to 1024 processes initially)
            val processIds = IntArray(1024)
            val bytesReturned = IntByReference()

            // Enumerate all processes
            if (psapi.EnumProcesses(processIds, processIds.size * 4, bytesReturned)) {
                val processCount = bytesReturned.value / 4 // Each int is 4 bytes
                for (i in 0 until processCount) {
                    val pid = processIds[i]
                    if (pid == 0) continue // Skip idle process

                    try {
                        // Open process handle with query and read permissions
                        val processHandle: WinNT.HANDLE? = kernel32.OpenProcess(
                            Kernel32.PROCESS_QUERY_INFORMATION or Kernel32.PROCESS_VM_READ,
                            false,
                            pid
                        )

                        if (processHandle != null) {
                            try {
                                // Buffer for full executable path
                                val nameBuffer = CharArray(1024) // Larger buffer for full paths
                                val size = psapi.GetModuleFileNameExW(processHandle, null, nameBuffer, nameBuffer.size)
                                if (size > 0) {
                                    val name = String(nameBuffer, 0, size).trim()
                                    processList.add(ProcessInfo(pid, name))
                                }
                            } finally {
                                kernel32.CloseHandle(processHandle) // Always close handle
                            }
                        }
                    } catch (e: Exception) {
                        println("getRunningProcesses for PID $pid: ${e.message}")
                        // Skip processes we can’t access
                    }
                }
            } else {
                println("EnumProcesses failed: ${kernel32.GetLastError()}")
            }

            val endTime = System.nanoTime()
            println("Getting Running Processes took: ${(endTime - startTime) / 1_000} μs, Discovered ${processList.size} processes")
            processList
        }*/
}

