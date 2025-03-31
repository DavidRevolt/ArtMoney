package org.davidrevolt.artmoney.search

/**
 * Implementation of Boyer Moore Algo.
 * Handles overlapping matches by incrementing sourcePos++ when a match is found.
 * It primarily relies on the Bad Character Rule, skipping ahead when a mismatch occurs.
 * Time complexity: O(n/m) average case.
 * */
class BoyerMooreSearchV1 : SearchAlgorithm {
    override fun search(buffer: ByteArray, baseAddress: Long, target: ByteArray): List<Long> {
        if (target.isEmpty() || target.size > buffer.size) return emptyList()

        val results = mutableListOf<Long>()
        val patternSize = target.size
        val bufferSize = buffer.size

        // Build bad character table
        // 256 - all possible byte values 0x00 to 0xFF
        // -1 Indicate not exists in the badChar
        val badChar = IntArray(256) { -1  }
        for (i in 0 until patternSize) {
            badChar[target[i].toInt() and 0xFF] = i
        }

        // Search loop
        var sourcePos = 0
        while (sourcePos <= bufferSize - patternSize) {
            var j = patternSize - 1

            // Compare right to left
            while (j >= 0 && buffer[sourcePos + j] == target[j]) {
                j--
            }

            if (j < 0) {
                // Match found
                results.add(baseAddress + sourcePos)
                /*
                * Advance by 1 to find overlapping matches, why?:
                * Buffer:  A  B  A  B  A
                * Index:   0  1  2  3  4
                * Pattern: A  B  A
                * if we use sourcePos += patternSize we will miss the match at index 2!
                * */
                sourcePos++
            } else {
                // No match - use bad character rule to skip ahead
                val currentByte = buffer[sourcePos + j].toInt() and 0xFF
                val shift = badChar[currentByte]
                sourcePos += maxOf(1,j-shift)
            }
        }
        return results
    }
}
