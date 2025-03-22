package org.davidrevolt.artmoney.service

import org.davidrevolt.artmoney.model.ScanValue
import java.nio.ByteBuffer

interface MemoryEditor {
    /**
     * Scan process memory for given value
     * @param processId The processId.
     * @param scanValue The scanValue, e.g: IntScanValue(100).
     * @return The memory addresses containing the scanValue.value.
     * */
    suspend fun scanProcessMemoryForValue(processId: Int, scanValue: ScanValue): List<Long>

    /**
     * Scan process memory specific addresses if containing  given value
     * @param processId The processId.
     * @param addresses The specific addresses to scan.
     * @param scanValue The scanValue, e.g: IntScanValue(100).
     * @return The memory addresses containing the scanValue value.
     * */
    suspend fun scanProcessAddressesForValue(
        processId: Int,
        addresses: List<Long>,
        scanValue: ScanValue
    ): List<Long>

    /**
     * @param processId The processId.
     * @param address The memory address.
     * @param scanValue The scanValue, e.g: IntScanValue(100).
     * @return Boolean if op successful.
     * */
    suspend fun writeProcessMemoryValue(
        processId: Int,
        address: Long,
        scanValue: ScanValue
    ): Boolean


    /**
     * @param processId The processId.
     * @param baseAddress The memory baseAddress.
     * @param regionSize The size of bytes to read (e.g: Int will be 4).
     * @return ByteBuffer.
     * */
    suspend fun readProcessMemory(processId: Int, baseAddress: Long, regionSize: Int): ByteBuffer
}