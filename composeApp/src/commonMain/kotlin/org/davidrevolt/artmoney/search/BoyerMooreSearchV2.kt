package org.davidrevolt.artmoney.search

/**
 * Implementation of Boyer Moore Algo.
 * Handles overlapping matches by incrementing sourcePos++ when a match is found.
 * Includes both the Bad Character Rule and the Good Suffix Rule, ensuring overlapping matches are handled efficiently.
 * Time complexity: O(n/m) average case.
 * */
class BoyerMooreSearchV2 : SearchAlgorithm {
    override fun search(buffer: ByteArray, baseAddress: Long, target: ByteArray): List<Long> {
        if (target.isEmpty() || target.size > buffer.size) return emptyList()

        val results = mutableListOf<Long>()
        val patternSize = target.size
        val bufferSize = buffer.size

        // Build bad character table
        val badChar = IntArray(256) { patternSize }
        for (i in 0 until patternSize - 1) {
            badChar[target[i].toInt() and 0xFF] = patternSize - 1 - i
        }

        // Build good suffix table
        val suffixShiftTable = IntArray(patternSize + 1)
        val borderPos = IntArray(patternSize + 1)
        preprocessGoodSuffix(target, suffixShiftTable, borderPos)

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
                sourcePos++  // Advance by 1 for overlapping matches , if no overlapping care use sourcePos += suffixShiftTable[0]
            } else {
                // No match - calculate maximum shift using both rules
                val currentByte = buffer[sourcePos + j].toInt() and 0xFF
                val badCharShift = badChar[currentByte].coerceAtLeast(1)
                val goodSuffixShift = suffixShiftTable[j + 1]
                sourcePos += maxOf(badCharShift, goodSuffixShift)
            }
        }
        return results
    }
}

private fun preprocessGoodSuffix(
    pattern: ByteArray,
    suffixShiftTable: IntArray,
    borderPositions: IntArray
) {
    val patternLength = pattern.size
    var suffixEnd = patternLength
    var nextBorder = patternLength + 1
    borderPositions[suffixEnd] = nextBorder

    while (suffixEnd > 0) {
        while (nextBorder <= patternLength &&
            pattern[suffixEnd - 1] != pattern[nextBorder - 1]) {
            if (suffixShiftTable[nextBorder] == 0) {
                suffixShiftTable[nextBorder] = nextBorder - suffixEnd
            }
            nextBorder = borderPositions[nextBorder]
        }
        suffixEnd--
        nextBorder--
        borderPositions[suffixEnd] = nextBorder
    }

    nextBorder = borderPositions[0]
    for (index in 0..patternLength) {
        if (suffixShiftTable[index] == 0) {
            suffixShiftTable[index] = nextBorder
        }
        if (index == nextBorder) {
            nextBorder = borderPositions[nextBorder]
        }
    }
}