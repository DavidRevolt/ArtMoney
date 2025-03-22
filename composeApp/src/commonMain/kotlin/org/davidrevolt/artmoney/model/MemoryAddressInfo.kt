package org.davidrevolt.artmoney.model

import java.nio.ByteBuffer

data class MemoryAddressInfo(
    val address: Long,
    val value: ByteBuffer
)