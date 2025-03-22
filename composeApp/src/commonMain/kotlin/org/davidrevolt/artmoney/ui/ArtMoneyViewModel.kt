package org.davidrevolt.artmoney.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.davidrevolt.artmoney.model.MemoryAddressInfo
import org.davidrevolt.artmoney.model.ProcessInfo
import org.davidrevolt.artmoney.model.ScanValue
import org.davidrevolt.artmoney.model.getAvailableScanValuesTypes
import org.davidrevolt.artmoney.model.getTypeDescription
import org.davidrevolt.artmoney.model.sizeOfScanValue
import org.davidrevolt.artmoney.model.toByteBuffer
import org.davidrevolt.artmoney.service.MemoryEditor
import org.davidrevolt.artmoney.service.ProcessManager
import java.nio.ByteBuffer

class ArtMoneyViewModel(
    private val manager: ProcessManager,
    private val memoryEditor: MemoryEditor
) : ViewModel() {

    private val _availableScanValuesTypes = getAvailableScanValuesTypes()
    private val _artMoneyUiState = MutableStateFlow<ArtMoneyUiState>(ArtMoneyUiState.Loading)
    val artMoneyUiState = _artMoneyUiState.asStateFlow()

    init {
        loadRunningProcesses()
    }

    fun loadRunningProcesses() =
        viewModelScope.launch {
            _artMoneyUiState.value = try {
                ArtMoneyUiState.Data(
                    runningProcesses = manager.getRunningProcesses(),
                    availableScanValues = _availableScanValuesTypes.map { constructor ->
                        constructor.getTypeDescription()
                    })
            } catch (e: Exception) {
                ArtMoneyUiState.Failure(e.message ?: "Failed to load processes")
            }
        }


    fun startScan(pid: Int, input: String, typeIndex: Int) {
        viewModelScope.launch {
            _artMoneyUiState.update { state ->
                when (state) {
                    is ArtMoneyUiState.Data -> state.copy(isOperationInProgress = true)
                    else -> state
                }
            }
            try {
                val scanValue = createScanValue(input, typeIndex)
                val addresses = memoryEditor.scanProcessMemoryForValue(pid, scanValue)
                val dummy = scanValue.toByteBuffer() // we already know the addresses value
                val results =
                    addresses.map { address -> MemoryAddressInfo(address, dummy) }
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            scanResults = results,
                            isOperationInProgress = false,
                            errorMessage = null
                        )

                        else -> state
                    }
                }
            } catch (e: Exception) {
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            isOperationInProgress = false,
                            errorMessage = e.message
                        )

                        else -> state
                    }
                }
            }

        }
    }


    fun reScan(pid: Int, addressesToReScan: List<Long>, input: String, typeIndex: Int) {
        viewModelScope.launch {
            _artMoneyUiState.update { state ->
                when (state) {
                    is ArtMoneyUiState.Data -> state.copy(isOperationInProgress = true)
                    else -> state
                }
            }
            try {
                val scanValue = createScanValue(input, typeIndex)
                val filterAddresses = memoryEditor.scanProcessAddressesForValue(
                    pid,
                    addressesToReScan,
                    scanValue
                )
                val dummy = scanValue.toByteBuffer()
                val filteredResults = filterAddresses.map { address ->
                    MemoryAddressInfo(
                        address,
                        dummy
                    )
                }
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            scanResults = filteredResults,
                            isOperationInProgress = false,
                            errorMessage = null
                        )

                        else -> state
                    }
                }

            } catch (e: Exception) {
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            isOperationInProgress = false,
                            errorMessage = e.message
                        )

                        else -> state
                    }
                }
            }
        }
    }

    fun writeValue(pid: Int, address: Long, input: String, typeIndex: Int) {
        _artMoneyUiState.update { state ->
            when (state) {
                is ArtMoneyUiState.Data -> state.copy(isOperationInProgress = true)
                else -> state
            }
        }
        viewModelScope.launch {
            try {
                val scanValue = createScanValue(input, typeIndex)
                val success = memoryEditor.writeProcessMemoryValue(
                    pid,
                    address,
                    scanValue
                )
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            isOperationInProgress = false,
                            errorMessage = if (success) null else "Failed to write value"
                        )

                        else -> state
                    }
                }
            } catch (e: Exception) {
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            isOperationInProgress = false,
                            errorMessage = e.message
                        )

                        else -> state
                    }
                }
            }
        }
    }


    fun refreshAddressValue(pid: Int, address: Long, typeIndex: Int) {
        viewModelScope.launch {
            _artMoneyUiState.update { state ->
                when (state) {
                    is ArtMoneyUiState.Data -> state.copy(isOperationInProgress = true)
                    else -> state
                }
            }
            try {
                val size =
                    _availableScanValuesTypes[typeIndex].sizeOfScanValue() // How Many bytes 2 read?
                val newValue =
                    memoryEditor.readProcessMemory(pid, address, size)
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> {
                            val updatedResults = state.scanResults.map {
                                if (it.address == address) it.copy(
                                    address = address,
                                    value = newValue
                                ) else it
                            }
                            state.copy(
                                isOperationInProgress = false,
                                scanResults = updatedResults
                            )
                        }

                        else -> state
                    }
                }
            } catch (e: Exception) {
                _artMoneyUiState.update { state ->
                    when (state) {
                        is ArtMoneyUiState.Data -> state.copy(
                            isOperationInProgress = false,
                            errorMessage = e.message
                        )

                        else -> state
                    }
                }
            }
        }
    }

    private fun createScanValue(input: String, typeInd: Int): ScanValue {
        return when (_availableScanValuesTypes[typeInd]) {
            ScanValue::IntScanValue -> ScanValue.IntScanValue(
                input.toIntOrNull() ?: throw IllegalArgumentException("Invalid Int")
            )

            ScanValue::FloatScanValue -> ScanValue.FloatScanValue(
                input.toFloatOrNull() ?: throw IllegalArgumentException("Invalid Float")
            )

            ScanValue::LongScanValue -> ScanValue.LongScanValue(
                input.toLongOrNull() ?: throw IllegalArgumentException("Invalid Long")
            )

            ScanValue::DoubleScanValue -> ScanValue.DoubleScanValue(
                input.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Double")
            )

            ScanValue::StringScanValue -> ScanValue.StringScanValue(input)
            else -> throw IllegalArgumentException("Can't create ScanValue Obj: Invalid type Index ($typeInd)")
        }
    }


    fun formatAddressValue(value: ByteBuffer, typeInd: Int): String {
        return try {
            when (_availableScanValuesTypes[typeInd]) {
                ScanValue::IntScanValue -> value.getInt(0).toString()

                ScanValue::FloatScanValue -> value.getFloat(0).toString()

                ScanValue::LongScanValue -> value.getLong(0).toString()

                ScanValue::DoubleScanValue -> value.getDouble(0).toString()

                ScanValue::StringScanValue -> String(
                    value.array(),
                    Charsets.UTF_8
                ).trimEnd('\u0000')

                else -> "Invalid Type Index"
            }
        } catch (e: Exception) {
            e.message ?: "ByteBuffer cannot format"
        }
    }

}


sealed interface ArtMoneyUiState {
    data class Data(
        val runningProcesses: List<ProcessInfo> = emptyList(),  // List of running processes
        val scanResults: List<MemoryAddressInfo> = emptyList(), // Found memory addresses
        val availableScanValues: List<String> = emptyList(),
        val isOperationInProgress: Boolean = false,             // Boolean to show loading indicator ON DATA
        val errorMessage: String? = null,                       // error message - showed on snackbar
    ) :
        ArtMoneyUiState

    data object Loading : ArtMoneyUiState
    data class Failure(val message: String) : ArtMoneyUiState
}