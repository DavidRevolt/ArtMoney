package org.davidrevolt.artmoney.search

/**
 * Method iterates one byte at a time
 * Time complexity: O(n * m)
 */

class BruteForce: SearchAlgorithm {
    override fun search(buffer: ByteArray, baseAddress: Long, target: ByteArray): List<Long> {
        val foundAddresses = mutableListOf<Long>()

        for (i in 0..buffer.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (buffer[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match)
                foundAddresses.add(baseAddress + i)
        }
        return foundAddresses
    }

}