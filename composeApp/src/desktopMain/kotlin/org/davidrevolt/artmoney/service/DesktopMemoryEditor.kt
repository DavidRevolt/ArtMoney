package org.davidrevolt.artmoney.service

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.davidrevolt.artmoney.model.MemoryRegion
import org.davidrevolt.artmoney.model.ScanValue
import org.davidrevolt.artmoney.model.sizeOfScanValue
import org.davidrevolt.artmoney.model.toByteBuffer
import org.davidrevolt.artmoney.search.SearchAlgorithm
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant


// Note: jna Access low-level WINDOWS OS features beyond ProcessHandle
// Pure Java could have limitation: No direct memory access - JNA is better
// The Memory implementation of JNA relies on the java garbage collection
class DesktopMemoryEditor(
    private val searchAlgorithm: SearchAlgorithm,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) : MemoryEditor {

    override suspend fun scanProcessMemoryForValue(
        processId: Int,
        scanValue: ScanValue
    ): List<Long> {
        val processHandle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_VM_READ or WinNT.PROCESS_QUERY_INFORMATION,
            false,
            processId
        ) ?: throw RuntimeException("Failed to open process")

        val addresses = mutableListOf<Long>()
        // Instead searching every page we retrieve readable regions [Range of pages that are readable]
        val memoryRegions = getMemoryReadableRegions(processHandle)
        val patternToSearch = scanValue.toByteBuffer().array()

        val startTime = Instant.now().epochSecond
        for (region in memoryRegions) {
            try {
                val buffer = readProcessMemoryFromHandle(
                    processHandle,
                    region.startAddress,
                    region.regionSize
                )
                addresses.addAll(withContext(defaultDispatcher) {
                    searchAlgorithm.search(
                        buffer.array(),
                        region.startAddress,
                        patternToSearch
                    )
                })
            } catch (e: Exception) {
                println(e.message)
            }
        }
        val endTime = Instant.now().epochSecond
        Kernel32.INSTANCE.CloseHandle(processHandle)
        println("Scan Process Memory took ${(endTime - startTime)}s, Discovered ${addresses.size} addresses ")
        return addresses
    }


    private suspend fun getMemoryReadableRegions(processHandle: WinNT.HANDLE): List<MemoryRegion> =
        withContext(ioDispatcher) {
            val memoryRegions = mutableListOf<MemoryRegion>()
            val memInfo =
                WinNT.MEMORY_BASIC_INFORMATION() // Structure contains info about memory
            var address = 0L

            // Readable protection flags we want to filter for
            val readableProtections = setOf(
                WinNT.PAGE_READONLY,
                WinNT.PAGE_READWRITE,
                WinNT.PAGE_WRITECOPY,
                WinNT.PAGE_EXECUTE_READ,
                WinNT.PAGE_EXECUTE_READWRITE
            )
            // Continue until VirtualQueryEx returns 0 (no more regions)
            while (Kernel32.INSTANCE.VirtualQueryEx(
                    processHandle,
                    Pointer(address),
                    memInfo,
                    BaseTSD.SIZE_T(memInfo.size().toLong())
                ).toInt() != 0
            ) {
                // Check if region is committed [allocated and is ready to be used by the program] and readable
                if (memInfo.state.toInt() == WinNT.MEM_COMMIT && memInfo.protect.toInt() in readableProtections)
                    memoryRegions.add(MemoryRegion(address, memInfo.regionSize.toInt()))

                // Move to next region
                address += memInfo.regionSize.toLong()
            }
            memoryRegions
        }


    override suspend fun scanProcessAddressesForValue(
        processId: Int,
        addresses: List<Long>,
        scanValue: ScanValue
    ): List<Long> {
        val processHandle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_VM_READ or WinNT.PROCESS_QUERY_INFORMATION,
            false,
            processId
        ) ?: throw RuntimeException("Failed to open process")
        val filteredAddresses = mutableListOf<Long>()
        val startTime = Instant.now().epochSecond
        addresses.forEach { address ->
            try {
                val buffer =
                    readProcessMemoryFromHandle(processHandle, address, scanValue.sizeOfScanValue())

                val matches = when (scanValue) {
                    is ScanValue.IntScanValue -> buffer.getInt(0) == scanValue.value
                    is ScanValue.FloatScanValue -> buffer.getFloat(0) == scanValue.value
                    is ScanValue.LongScanValue -> buffer.getLong(0) == scanValue.value
                    is ScanValue.DoubleScanValue -> buffer.getDouble(0) == scanValue.value
                    is ScanValue.StringScanValue -> buffer.array().decodeToString()
                        .trim('\u0000') == scanValue.value
                }

                if (matches) filteredAddresses.add(address)
            } catch (e: Exception) {
                println(e.message)
            }
        }
        val endTime = Instant.now().epochSecond
        Kernel32.INSTANCE.CloseHandle(processHandle)
        println("Scan Process Specific addresses took ${(endTime - startTime)}s, Discovered ${filteredAddresses.size} addresses ")
        return filteredAddresses
    }

    /**
     * return the raw data of the starts at baseAddress, ends at: baseAddress+regionSize
     * NOT responsible for Opening & Closing processHandle
     *  */
    private suspend fun readProcessMemoryFromHandle(
        processHandle: WinNT.HANDLE,
        baseAddress: Long,
        regionSize: Int
    ): ByteBuffer =
        withContext(ioDispatcher) {
            val buffer = Memory(regionSize.toLong()) // Allocate memory buffer
            val bytesRead = IntByReference() // Stores the number of bytes successfully read
            if (!Kernel32.INSTANCE.ReadProcessMemory(
                    processHandle,
                    Pointer(baseAddress),
                    buffer,
                    regionSize,
                    bytesRead
                )
            ) {
                throw RuntimeException("Failed to read memory at 0x${baseAddress.toString(16)}")
            }
            // Returning ByteBuffer allow us to use getInt() or getDouble()... on ByteBuffer obj
            ByteBuffer.wrap(buffer.getByteArray(0, bytesRead.value))
                .order(ByteOrder.LITTLE_ENDIAN)
        }


    /**
     * return the raw data of the starts at baseAddress, ends at: baseAddress+regionSize
     *  */
    override suspend fun readProcessMemory(
        processId: Int,
        baseAddress: Long,
        regionSize: Int
    ): ByteBuffer =
        withContext(ioDispatcher) {
            val processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_VM_READ or WinNT.PROCESS_QUERY_INFORMATION,
                false,
                processId
            ) ?: throw RuntimeException("Failed to open process")
            try {
                readProcessMemoryFromHandle(processHandle, baseAddress, regionSize)
            } finally {
                Kernel32.INSTANCE.CloseHandle(processHandle)
            }
        }


    override suspend fun writeProcessMemoryValue(
        processId: Int,
        address: Long,
        scanValue: ScanValue
    ): Boolean = withContext(ioDispatcher) {
        val processHandle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_VM_WRITE or WinNT.PROCESS_VM_OPERATION or WinNT.PROCESS_QUERY_INFORMATION,
            false,
            processId
        ) ?: return@withContext false

        val buffer = scanValue.toByteBuffer()
        val memory = Memory(buffer.capacity().toLong()).apply {
            write(
                0,
                buffer.array(),
                0,
                buffer.capacity()
            )
        }
        val bytesWritten = IntByReference()

        val success = Kernel32.INSTANCE.WriteProcessMemory(
            processHandle,
            Pointer(address),
            memory,
            buffer.capacity(),
            bytesWritten
        ) && bytesWritten.value == buffer.capacity()

        val msg =
            if (success) "Success" else "failed with error code: ${Kernel32.INSTANCE.GetLastError()}"
        println("Write to process memory: $msg at 0x${address.toString(16)}")
        Kernel32.INSTANCE.CloseHandle(processHandle)
        return@withContext success
    }
}

