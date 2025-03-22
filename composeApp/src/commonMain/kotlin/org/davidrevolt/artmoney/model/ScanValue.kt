package org.davidrevolt.artmoney.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KFunction1

sealed class ScanValue {
    data class IntScanValue(val value: Int) : ScanValue()      // 4 bytes
    data class FloatScanValue(val value: Float) : ScanValue()  // 4 bytes
    data class LongScanValue(val value: Long) : ScanValue()    // 8 bytes
    data class DoubleScanValue(val value: Double) : ScanValue() // 8 bytes
    data class StringScanValue(val value: String) : ScanValue()
}

fun getAvailableScanValuesTypes(): List<KFunction1<*, ScanValue>> {
    return listOf(
        ScanValue::IntScanValue,
        ScanValue::FloatScanValue,
        ScanValue::LongScanValue,
        ScanValue::DoubleScanValue,
        ScanValue::StringScanValue
    )
}


fun ScanValue.toByteBuffer(): ByteBuffer {
    return when (this) {
        is ScanValue.IntScanValue -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)


        is ScanValue.FloatScanValue -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(value)


        is ScanValue.LongScanValue -> ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(value)


        is ScanValue.DoubleScanValue -> ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(value)


        is ScanValue.StringScanValue -> ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8) + 0x00.toByte())
    }.rewind() as ByteBuffer
}


// Return sizeOfScanValue based on Initialized ScanValue Obj
fun ScanValue.sizeOfScanValue(): Int {
    return when (this) {
        is ScanValue.IntScanValue, is ScanValue.FloatScanValue -> 4
        is ScanValue.LongScanValue, is ScanValue.DoubleScanValue -> 8
        is ScanValue.StringScanValue -> this.value.length + 1
    }
}

// Return sizeOfScanValue based on the constructor, StringScanValue not init and value size is dynamic -> throws exception
fun KFunction1<*, ScanValue>.sizeOfScanValue(): Int {
    return when (this) {
        ScanValue::IntScanValue, ScanValue::FloatScanValue -> 4
        ScanValue::LongScanValue, ScanValue::DoubleScanValue -> 8
        ScanValue::StringScanValue -> throw Exception("Can't determine size of String")
        else -> throw IllegalArgumentException("Unknown ScanValue constructor")
    }
}

// Return Description based on the constructor
fun KFunction1<*, ScanValue>.getTypeDescription(): String {
    return when (this) {
        ScanValue::IntScanValue -> "Integer (4 bytes)"
        ScanValue::FloatScanValue -> "Float (4 bytes)"
        ScanValue::LongScanValue -> "Long (8 bytes)"
        ScanValue::DoubleScanValue -> "Double (8 bytes)"
        ScanValue::StringScanValue -> "String UTF-8"
        else -> "Unknown"
    }
}

