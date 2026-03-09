package com.rgbpos.bigs.ui.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
    var connectedPrinter by remember { mutableStateOf<BluetoothPrinter?>(null) }
    var statusMsg by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        hasPermission = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) {
            pairedDevices = getPairedDevices(context)
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }

    AlertDialog(
        onDismissRequest = {
            connectedPrinter?.disconnect()
            onDismiss()
        },
        title = { Text("Bluetooth Printer") },
        text = {
            Column(Modifier.fillMaxWidth()) {
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
                    Text("Select your thermal printer:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(pairedDevices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !connecting) {
                                        connecting = true
                                        statusMsg = "Connecting to ${device.name ?: device.address}..."
                                        scope.launch {
                                            val printer = BluetoothPrinter(device)
                                            try {
                                                withContext(Dispatchers.IO) { printer.connect() }
                                                connectedPrinter = printer
                                                statusMsg = "Connected to ${printer.name}"
                                                onPrint(printer)
                                                withContext(Dispatchers.IO) { printer.disconnect() }
                                            } catch (e: Exception) {
                                                statusMsg = "Failed: ${e.localizedMessage}"
                                                printer.disconnect()
                                            } finally {
                                                connecting = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(device.name ?: "Unknown", fontWeight = FontWeight.Medium)
                                    Text(device.address, fontSize = 12.sp, color = Color.Gray)
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
                        Text(statusMsg, fontSize = 13.sp, color = if (statusMsg.startsWith("Failed")) MaterialTheme.colorScheme.error else Color(0xFF27AE60))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                connectedPrinter?.disconnect()
                onDismiss()
            }) { Text("Close") }
        },
    )
}

@SuppressLint("MissingPermission")
private fun getPairedDevices(context: Context): List<BluetoothDevice> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()
    return adapter.bondedDevices?.toList() ?: emptyList()
}
