package org.davidrevolt.artmoney.search

interface SearchAlgorithm {
    /**
     * @returns Addresses containing target
     * Example:
     * Given memory buffer: [0x00, 0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x90] ,starts at baseAddress
     * Searching for target : [0x12, 0x34]
     * Result: [baseAddress + 1, baseAddress + 5]
     */
    fun search(buffer: ByteArray, baseAddress: Long, target: ByteArray): List<Long>
}