package org.davidrevolt.artmoney.search

interface SearchAlgorithm {
    /**
     * @returns Addresses containing target
     * Example:
     * Given memory buffer: [0x00, 0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x90] ,starts at baseAddress
     * Searching for target : [0x12, 0x34]
     * Result: [baseAddress + 1, baseAddress + 5]
     *
     * Search algorithm should handle overlapping, e.g:
     * Buffer:  A  B  A  B  A
     * Index:   0  1  2  3  4
     * Pattern: A  B  A
     * Should return indexes [0, 2]
     */
    fun search(buffer: ByteArray, baseAddress: Long, target: ByteArray): List<Long>
}