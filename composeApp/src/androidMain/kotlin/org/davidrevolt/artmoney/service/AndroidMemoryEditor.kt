package org.davidrevolt.artmoney.service

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.davidrevolt.artmoney.model.MemoryRegion
import org.davidrevolt.artmoney.model.ScanValue
import org.davidrevolt.artmoney.model.sizeOfScanValue
import org.davidrevolt.artmoney.model.toByteBuffer
import org.davidrevolt.artmoney.search.SearchAlgorithm
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder


// Note: jna Access low-level OS features beyond ProcessHandle
// Pure Java could have limitation: No direct memory access - JNA is better
class AndroidMemoryEditor(
    private val searchAlgorithm: SearchAlgorithm,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) : MemoryEditor {

    override suspend fun scanProcessMemoryForValue(
        processId: Int,
        scanValue: ScanValue
    ): List<Long> {
        val fd =
            LibC.INSTANCE.open("/proc/$processId/mem", O_RDONLY) //fb stands for file descriptor
        if (fd < 0) throw RuntimeException("Failed to open process memory")

        val addresses = mutableListOf<Long>()
        // Instead searching every page we retrieve readable regions [Range of pages that are readable]
        val memoryRegions = getMemoryReadableRegions(processId)
        val patternToSearch = scanValue.toByteBuffer().array()

        val chunkSize = 2 * 1024 * 1024 // 2 MB
        val overlapSize = scanValue.sizeOfScanValue() - 1 // Overlap for boundary patterns,
        val startTime = System.currentTimeMillis()
        for (region in memoryRegions) {
            try {
                // On PC When trying to read region in one call the data copied to buffer.
                // On android the app CRASH if trying to allocate large buffer (when tested: Requested 512mb, available 6mb)
                // Solution: Read region in small chunks size of 1mb + overlapSize
                var offset = 0L
                while (offset < region.regionSize) {
                    val sizeToRead = minOf(chunkSize, region.regionSize - offset.toInt())
                    val chunkStartAddress = region.startAddress + offset
                    // Read extra bytes for overlap, but cap at region size
                    val adjustedSize =
                        minOf(sizeToRead + overlapSize, region.regionSize - offset.toInt())
                    val buffer =
                        readProcessMemoryFromFileDescriptor(fd, chunkStartAddress, adjustedSize)
                    val foundAddresses = withContext(defaultDispatcher) {
                        searchAlgorithm.search(
                            buffer.array(),
                            chunkStartAddress,
                            patternToSearch
                        )
                    }
                    addresses.addAll(foundAddresses)
                    offset += sizeToRead.toLong() // Move forward by chunkSize, not adjustedSize
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
            }
        }
        val endTime = System.currentTimeMillis()
        LibC.INSTANCE.close(fd)
        Log.i(
            TAG,
            "Scan Process Memory took ${(endTime - startTime) / 1000.0}s, Discovered ${addresses.size} addresses"
        )
        return addresses
    }


    private suspend fun getMemoryReadableRegions(processId: Int): List<MemoryRegion> =
        withContext(ioDispatcher) {
            val memoryRegions = mutableListOf<MemoryRegion>()
            // mapsFile - virtual file in Linux that lists memory regions used by a process
            val mapsFile = File("/proc/$processId/maps")
            if (!mapsFile.exists()) throw RuntimeException("Cannot access process maps to get readable regions")

            // Each line represents a memory region and includes
            mapsFile.readLines().forEach { line ->
                val parts = line.split(" ")
                if (parts.isNotEmpty() && parts[1].contains("r")) { // Check read permission
                    val addresses = parts[0].split("-")
                    val startAddress = addresses[0].toLong(16)
                    val endAddress = addresses[1].toLong(16)
                    val size = (endAddress - startAddress).toInt()
                    memoryRegions.add(MemoryRegion(startAddress, size))
                }
            }
            memoryRegions
        }


    override suspend fun scanProcessAddressesForValue(
        processId: Int,
        addresses: List<Long>,
        scanValue: ScanValue
    ): List<Long> {
        val fd =
            LibC.INSTANCE.open("/proc/$processId/mem", O_RDONLY) //fb stands for file descriptor
        if (fd < 0) throw RuntimeException("Failed to open process memory")

        val filteredAddresses = mutableListOf<Long>()
        val startTime = System.currentTimeMillis()
        addresses.forEach { address ->
            try {
                val buffer =
                    readProcessMemoryFromFileDescriptor(fd, address, scanValue.sizeOfScanValue())

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
                Log.e(TAG, e.message.toString())
            }
        }
        val endTime = System.currentTimeMillis()
        LibC.INSTANCE.close(fd)
        Log.i(
            TAG,
            "Scan Process Specific addresses took ${(endTime - startTime) / 1000.0}s, Discovered ${filteredAddresses.size} addresses "
        )
        return filteredAddresses
    }


    /**
     * return the raw data of the starts at baseAddress, ends at: baseAddress+regionSize
     * NOT responsible for Opening & Closing File Descriptor
     * */
    private suspend fun readProcessMemoryFromFileDescriptor(
        fd: Int,
        baseAddress: Long,
        regionSize: Int
    ): ByteBuffer =
        withContext(ioDispatcher) {
            val buffer = Memory(regionSize.toLong())
            val bytesRead = LibC.INSTANCE.pread(
                fd,
                buffer,
                NativeLong(regionSize.toLong()),
                NativeLong(baseAddress)
            )
            if (bytesRead.toLong() < 0) throw RuntimeException(
                "Failed to read memory at 0x${
                    baseAddress.toString(
                        16
                    )
                }"
            )
            ByteBuffer.wrap(buffer.getByteArray(0, bytesRead.toInt()))
                .order(ByteOrder.LITTLE_ENDIAN)
        }


    /**
     * return the raw data of the starts at baseAddress, ends at: baseAddress+regionSize
     * */
    override suspend fun readProcessMemory(
        processId: Int,
        baseAddress: Long,
        regionSize: Int
    ): ByteBuffer =
        withContext(ioDispatcher) {
            val fd = LibC.INSTANCE.open("/proc/$processId/mem", O_RDONLY)
            if (fd < 0) throw RuntimeException("Failed to open process memory")
            try {
                readProcessMemoryFromFileDescriptor(fd, baseAddress, regionSize)
            } finally {
                LibC.INSTANCE.close(fd)
            }
        }


    override suspend fun writeProcessMemoryValue(
        processId: Int,
        address: Long,
        scanValue: ScanValue
    ): Boolean = withContext(ioDispatcher) {
        val fd = LibC.INSTANCE.open("/proc/$processId/mem", O_RDWR)
        if (fd < 0) return@withContext false

        val buffer = scanValue.toByteBuffer()
        val memory = Memory(buffer.capacity().toLong()).apply {
            write(0, buffer.array(), 0, buffer.capacity())
        }

        val bytesWritten = LibC.INSTANCE.pwrite(
            fd,
            memory,
            NativeLong(buffer.capacity().toLong()),
            NativeLong(address)
        )
        LibC.INSTANCE.close(fd)

        val success =
            bytesWritten.toLong() == buffer.capacity().toLong() //convert to throw execption
        Log.d(
            TAG,
            "Write to memory ${if (success) "successful" else "failed"} at 0x${address.toString(16)}"
        )
        return@withContext success
    }


    companion object {
        private const val O_RDONLY = 0
        private const val O_RDWR = 2
        const val TAG = "ArtMoneyLog"
    }

    interface LibC : Library {
        companion object {
            // "c" - tells to use libc library on Linux or Unix systems
            val INSTANCE: LibC = Native.load("c", LibC::class.java)
        }

        //   fun getpid(): Int // Returns the PID of the calling process.
        fun open(pathname: String, flags: Int): Int
        fun pread(fd: Int, buffer: Pointer, count: NativeLong, offset: NativeLong): NativeLong
        fun pwrite(fd: Int, buffer: Pointer, count: NativeLong, offset: NativeLong): NativeLong
        fun close(fd: Int): Int
    }
}


