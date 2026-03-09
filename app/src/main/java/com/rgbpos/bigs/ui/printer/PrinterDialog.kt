package com.rgbpos.bigs.ui.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rgbpos.bigs.util.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable
fun PrinterDialog(
    onDismiss: () -> Unit,
    onPrint: (BluetoothPrinter) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connecting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var savedPrinterMac by remember { mutableStateOf<String?>(null) }
    var savedPrinterName by remember { mutableStateOf<String?>(null) }
    var autoConnectAttempted by remember { mutableStateOf(false) }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
        if (hasPermission) {
            pairedDevices = getPairedDevices(context)
        }
    }

    fun connectAndPrint(device: BluetoothDevice, savePrinter: Boolean = true) {
        connecting = true
        statusMsg = "Connecting to ${device.name ?: device.address}..."
        scope.launch {
            val printer = BluetoothPrinter(device)
            try {
                withContext(Dispatchers.IO) { printer.connect() }
                if (savePrinter) {
                    TokenStore.savePrinter(context, device.address, device.name ?: device.address)
                    savedPrinterMac = device.address
                    savedPrinterName = device.name ?: device.address
                }
                statusMsg = "Printing..."
                withContext(Dispatchers.IO) { onPrint(printer) }
                withContext(Dispatchers.IO) { printer.disconnect() }
                statusMsg = "Printed successfully"
            } catch (e: Exception) {
                statusMsg = "Failed: ${e.localizedMessage}"
                printer.disconnect()
            } finally {
                connecting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        savedPrinterMac = TokenStore.getPrinterMac(context)
        savedPrinterName = TokenStore.getPrinterName(context)

        hasPermission = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) {
            pairedDevices = getPairedDevices(context)
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }

    // Auto-connect to saved printer
    LaunchedEffect(hasPermission, pairedDevices, autoConnectAttempted) {
        if (hasPermission && pairedDevices.isNotEmpty() && !autoConnectAttempted && savedPrinterMac != null) {
            autoConnectAttempted = true
            val savedDevice = pairedDevices.find { it.address == savedPrinterMac }
            if (savedDevice != null) {
                connectAndPrint(savedDevice, savePrinter = false)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Bluetooth Printer") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Show saved printer info
                if (savedPrinterName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Print, null, tint = Color(0xFF27AE60), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Saved: $savedPrinterName", fontSize = 13.sp, color = Color(0xFF27AE60))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!hasPermission) {
                    Text("Bluetooth permission required.", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(bluetoothPermissions) }) {
                        Text("Grant Permission")
                    }
                } else if (pairedDevices.isEmpty()) {
                    Text("No paired Bluetooth devices found.", color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text("Pair your 58mm printer in Android Bluetooth Settings first.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text("Select printer:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(pairedDevices) { device ->
                            val isSaved = device.address == savedPrinterMac
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !connecting) {
                                        connectAndPrint(device)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Print, null,
                                    tint = if (isSaved) Color(0xFF27AE60) else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.name ?: "Unknown", fontWeight = FontWeight.Medium)
                                    Text(device.address, fontSize = 12.sp, color = Color.Gray)
                                }
                                if (isSaved) {
                                    Icon(Icons.Default.CheckCircle, "Saved", tint = Color(0xFF27AE60), modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }

                if (statusMsg.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    if (connecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(statusMsg, fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        Text(
                            statusMsg, fontSize = 13.sp,
                            color = if (statusMsg.startsWith("Failed")) MaterialTheme.colorScheme.error else Color(0xFF27AE60),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("Close") }
        },
    )
}

@SuppressLint("MissingPermission")
private fun getPairedDevices(context: Context): List<BluetoothDevice> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()
    return adapter.bondedDevices?.toList() ?: emptyList()
}
