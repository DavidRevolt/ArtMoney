package org.davidrevolt.artmoney.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.davidrevolt.artmoney.model.MemoryAddressInfo
import org.davidrevolt.artmoney.model.ProcessInfo
import org.davidrevolt.artmoney.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArtMoney() {

    val viewModel = koinViewModel<ArtMoneyViewModel>()
    val uiState by viewModel.artMoneyUiState.collectAsStateWithLifecycle()

    when (uiState) {
        is ArtMoneyUiState.Data -> {
            val data = (uiState as ArtMoneyUiState.Data)
            ArtMoneyContent(data, viewModel)
        }

        is ArtMoneyUiState.Loading -> {
            LoadingIndicator()
        }

        is ArtMoneyUiState.Failure -> {
            FailureContent(
                errDescription = (uiState as ArtMoneyUiState.Failure).message,
                onTryAgainClick = viewModel::loadRunningProcesses
            )
        }
    }
}


@Composable
fun ArtMoneyContent(
    uiState: ArtMoneyUiState.Data,
    viewModel: ArtMoneyViewModel
) {
    var selectedProcess by remember { mutableStateOf<ProcessInfo?>(null) }
    var scanValue by remember { mutableStateOf("") }
    var newScanValue by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.safeDrawingPadding())
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProcessSelector(
                processes = uiState.runningProcesses,
                onProcessSelected = { selectedProcess = it }
            )

            OutlinedTextField(
                value = scanValue,
                onValueChange = { scanValue = it },
                label = { Text("Value to scan") },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedProcess != null
            )

            ValueTypeSelector(
                selectedTypeIndex = selectedTypeIndex,
                onTypeSelected = { selectedTypeIndex = it },
                availableTypes = uiState.availableScanValues,
                enabled = selectedProcess != null
            )

            Button(
                onClick = {
                    selectedProcess?.let {
                        viewModel.startScan(
                            it.pid,
                            scanValue,
                            selectedTypeIndex
                        )
                    }
                },
                enabled = selectedProcess != null && scanValue.isNotEmpty()
            ) {
                Text("Scan")
            }

            if (uiState.scanResults.isNotEmpty()) {
                OutlinedTextField(
                    value = newScanValue,
                    onValueChange = { newScanValue = it },
                    label = { Text("New value to filter") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedProcess != null
                )
                Button(
                    onClick = {
                        selectedProcess?.let {
                            viewModel.reScan(
                                it.pid,
                                uiState.scanResults.map { it.address },
                                newScanValue,
                                selectedTypeIndex
                            )
                        }
                    },
                    enabled = selectedProcess != null && newScanValue.isNotEmpty()
                ) {
                    Text("Filter")
                }
                ScanResults(
                    selectedProcess = selectedProcess,
                    selectedTypeIndex = selectedTypeIndex,
                    viewModel = viewModel,
                    scanResults = uiState.scanResults
                )
            }

            LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let {
                    snackbarHostState.showSnackbar(it)
                }
            }
            if (uiState.isOperationInProgress) {
                LoadingIndicator()
            }
        }
    }
}


@Composable
fun ProcessSelector(
    processes: List<ProcessInfo>,
    onProcessSelected: (ProcessInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredProcesses = processes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val focusManager = LocalFocusManager.current
    Box {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                expanded = true  // Keep dropdown open while typing
            },
            label = { Text("Search Running Processes") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false), // Without that after first letter cant write anymore
            modifier = Modifier.heightIn(max = 300.dp).fillMaxWidth(fraction = 0.7f),
            scrollState = scrollState,
        ) {
            if (filteredProcesses.isEmpty()) {
                DropdownMenuItem(onClick = {}) {
                    Text("No processes found", fontStyle = FontStyle.Italic)
                }
            } else {
                filteredProcesses.forEach { process ->
                    DropdownMenuItem(onClick = {
                        searchQuery = process.name  // Update text field with selected process name
                        onProcessSelected(process)
                        expanded = false  // Close dropdown
                        focusManager.clearFocus()
                    }) {
                        //  Text(process.name)
                        Text("PID: ${process.pid}\n${process.name}")
                    }
                }
            }
        }
    }
}


@Composable
fun ScanResults(
    selectedProcess: ProcessInfo?,
    selectedTypeIndex: Int,
    viewModel: ArtMoneyViewModel,
    scanResults: List<MemoryAddressInfo>,
) {
    val scrollState = rememberLazyListState()
    Text(
        text = "Found ${scanResults.size} address(es)",
        modifier = Modifier.padding(top = 8.dp)
    )
    Box { // need box if wanna use vertical scroller
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            scrollState
        ) {
            items(scanResults) { addressInfo ->
                var showDialog by remember { mutableStateOf(false) }
                val displayValue =
                    viewModel.formatAddressValue(addressInfo.value, selectedTypeIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable {
                            selectedProcess?.let { // TODO: if refreshing string -> Show dialog to determine length of string
                                viewModel.refreshAddressValue(
                                    it.pid,
                                    addressInfo.address,
                                    selectedTypeIndex
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0x${addressInfo.address.toString(16)}",
                        modifier = Modifier
                            .weight(1f)
                    )
                    Text(
                        text = "Value: $displayValue",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Edit")
                    }
                }
                if (showDialog) {
                    ChangeValueDialog(
                        address = addressInfo.address,
                        onDismiss = { showDialog = false },
                        onConfirm = { newValue ->
                            selectedProcess?.let {
                                viewModel.writeValue(
                                    it.pid,
                                    addressInfo.address,
                                    newValue,
                                    selectedTypeIndex
                                )
                            }
                            showDialog = false
                        }
                    )
                }
            }
        }
        /*
               // VerticalScrollbar - Works only on Desktop platform
                      VerticalScrollbar(
                           adapter = rememberScrollbarAdapter(scrollState),
                           modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
                       )*/
    }
}

@Composable
fun ValueTypeSelector(
    selectedTypeIndex: Int,
    onTypeSelected: (Int) -> Unit,
    availableTypes: List<String>,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Box() {
        OutlinedTextField(
            value = availableTypes[selectedTypeIndex],
            onValueChange = {},
            readOnly = true,
            label = { Text("Scan Type") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = { expanded = true }, enabled = enabled) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableTypes.forEachIndexed { index, displayName ->
                DropdownMenuItem(onClick = {
                    onTypeSelected(index)
                    expanded = false
                }) {
                    Text(displayName)
                }
            }
        }
    }
}

@Composable
fun ChangeValueDialog(
    address: Long,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Value at 0x${address.toString(16)}") },
        text = {
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = { Text("New Value") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newValue) },
                enabled = newValue.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun FailureContent(errDescription: String, onTryAgainClick: () -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = errDescription,
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.body1
        )
        Button(
            onClick = onTryAgainClick,
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
        ) {
            Text("Try Again", color = MaterialTheme.colors.onSecondary)
        }
    }
}