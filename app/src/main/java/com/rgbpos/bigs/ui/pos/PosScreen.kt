package com.rgbpos.bigs.ui.pos

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.rgbpos.bigs.data.model.*
import com.rgbpos.bigs.ui.printer.PrinterDialog
import com.rgbpos.bigs.ui.printer.ReceiptFormatter
import com.rgbpos.bigs.ui.update.AppUpdater
import com.rgbpos.bigs.ui.update.VersionInfo
import java.text.NumberFormat
import java.util.Locale

private val pesoFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
private fun formatPeso(amount: Double) = "\u20B1 ${pesoFormat.format(amount)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    userName: String,
    onLogout: () -> Unit,
    vm: PosViewModel = viewModel(),
) {
    val context = LocalContext.current
    var showPayment by remember { mutableStateOf(false) }
    var showPrinter by remember { mutableStateOf(false) }
    var orderToPrint by remember { mutableStateOf<OrderResponse?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }

    // Check for app update on launch
    LaunchedEffect(Unit) {
        val info = AppUpdater.checkForUpdate()
        if (info != null) updateInfo = info
    }

    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Big's Crispy Lechon Belly", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (!vm.isOnline) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = Color(0xFFE74C3C)) {
                                Text("OFFLINE", fontSize = 9.sp)
                            }
                        }
                        if (vm.pendingCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = Color(0xFFF39C12)) {
                                Text("${vm.pendingCount} pending", fontSize = 9.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    Text(userName, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                    IconButton(onClick = { vm.syncFromServer() }) {
                        Icon(Icons.Default.Refresh, "Sync")
                    }
                    IconButton(onClick = { showPrinter = true }) {
                        Icon(Icons.Default.Print, "Printer")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, "Logout")
                    }
                },
            )
        },
    ) { padding ->
        if (vm.loading && vm.products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(padding)) {
                // Left: Products
                Column(Modifier.weight(0.6f).fillMaxHeight().padding(8.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = vm.selectedCategoryId == 0,
                                onClick = { vm.selectCategory(0) },
                                label = { Text("All") },
                            )
                        }
                        items(vm.categories) { cat ->
                            FilterChip(
                                selected = vm.selectedCategoryId == cat.id,
                                onClick = { vm.selectCategory(cat.id) },
                                label = { Text(cat.name) },
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(vm.filteredProducts, key = { it.id }) { product ->
                            ProductCard(product) { vm.addToCart(product) }
                        }
                    }
                }

                // Right: Cart
                Card(
                    modifier = Modifier.weight(0.4f).fillMaxHeight().padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Cart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Badge(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                                Text("${vm.itemCount}")
                            }
                        }

                        if (vm.cart.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No items in cart", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f)) {
                                items(vm.cart, key = { it.productId }) { item ->
                                    CartItemRow(
                                        item = item,
                                        onIncrease = { vm.updateQuantity(item.productId, item.quantity + 1) },
                                        onDecrease = { vm.updateQuantity(item.productId, item.quantity - 1) },
                                        onRemove = { vm.removeFromCart(item.productId) },
                                    )
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Items", color = Color.Gray, fontSize = 13.sp)
                                Text("${vm.itemCount}", color = Color.Gray, fontSize = 13.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", fontWeight = FontWeight.Bold)
                                Text(formatPeso(vm.subtotal), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showPayment = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = vm.cart.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                            ) {
                                Icon(Icons.Default.Payment, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Payment")
                            }
                            OutlinedButton(
                                onClick = { vm.clearCart() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }
    }

    // Payment dialog
    if (showPayment) {
        PaymentDialog(
            subtotal = vm.subtotal,
            customers = vm.customers,
            submitting = vm.submitting,
            isOnline = vm.isOnline,
            onDismiss = { showPayment = false },
            onComplete = { customerId, orderType, discount ->
                vm.completeOrder(
                    customerId = customerId,
                    orderType = orderType,
                    discount = discount,
                    onSuccess = { order ->
                        showPayment = false
                        orderToPrint = order
                        val prefix = if (order.orderNumber.startsWith("OFFLINE")) "(Offline) " else ""
                        snackMessage = "${prefix}Order ${order.orderNumber} completed!"
                    },
                    onError = { msg -> snackMessage = msg },
                )
            },
        )
    }

    // Print receipt after order
    orderToPrint?.let { order ->
        AlertDialog(
            onDismissRequest = { orderToPrint = null },
            title = { Text("Order Complete") },
            text = {
                Column {
                    Text("Order: ${order.orderNumber}")
                    Text("Total: ${formatPeso(order.total)}")
                    Spacer(Modifier.height(8.dp))
                    Text("Print receipt?", color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = { showPrinter = true }) { Text("Print") }
            },
            dismissButton = {
                TextButton(onClick = { orderToPrint = null }) { Text("Skip") }
            },
        )
    }

    // Printer dialog
    if (showPrinter) {
        PrinterDialog(
            onDismiss = { showPrinter = false },
            onPrint = { printer ->
                val order = orderToPrint ?: vm.lastOrder
                if (order != null) {
                    val receipt = ReceiptFormatter.format(order)
                    printer.printReceipt(receipt)
                    orderToPrint = null
                    snackMessage = "Receipt sent to printer"
                } else {
                    snackMessage = "No order to print"
                }
                showPrinter = false
            },
        )
    }

    // Update available dialog
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${info.version_name} is available.")
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(info.changelog, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    AppUpdater.downloadAndInstall(context, info)
                    updateInfo = null
                }) { Text("Update Now") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Later") }
            },
        )
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (product.imageUrl != null) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDFE8F1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        product.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                product.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                formatPeso(product.price),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE8F1)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name.take(2).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatPeso(item.price)} x ${item.quantity}", fontSize = 12.sp, color = Color.Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
            }
            Text("${item.quantity}", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, fontSize = 14.sp)
            IconButton(onClick = onIncrease, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            formatPeso(item.price * item.quantity),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.End,
        )
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    subtotal: Double,
    customers: List<Customer>,
    submitting: Boolean,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onComplete: (customerId: Int?, orderType: String, discount: Double) -> Unit,
) {
    var discount by remember { mutableStateOf("0") }
    var cashTendered by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Int?>(null) }
    var orderType by remember { mutableStateOf("dine-in") }
    var customerExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val total = subtotal - discountVal
    val cash = cashTendered.toDoubleOrNull() ?: 0.0
    val change = cash - total

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .widthIn(max = 420.dp)
            .padding(16.dp)
            .imePadding(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Payment")
                if (!isOnline) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = Color(0xFFE74C3C)) { Text("OFFLINE", fontSize = 9.sp) }
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal:")
                    Text(formatPeso(subtotal))
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = discount,
                    onValueChange = { discount = it },
                    label = { Text("Discount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("\u20B1 ") },
                )

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(formatPeso(total), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cashTendered,
                    onValueChange = { cashTendered = it },
                    label = { Text("Cash Tendered") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("\u20B1 ") },
                )

                if (cash > 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Change:", fontWeight = FontWeight.Bold)
                        Text(
                            formatPeso(if (change >= 0) change else 0.0),
                            fontWeight = FontWeight.Bold,
                            color = if (change >= 0) Color(0xFF27AE60) else MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Customer dropdown
                ExposedDropdownMenuBox(
                    expanded = customerExpanded,
                    onExpandedChange = { customerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = customers.find { it.id == selectedCustomerId }?.let { "${it.name} (${it.loyaltyPoints} pts)" } ?: "Walk-in Customer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Customer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(customerExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                        DropdownMenuItem(text = { Text("Walk-in Customer") }, onClick = {
                            selectedCustomerId = null
                            customerExpanded = false
                        })
                        customers.forEach { c ->
                            DropdownMenuItem(text = { Text("${c.name} (${c.loyaltyPoints} pts)") }, onClick = {
                                selectedCustomerId = c.id
                                customerExpanded = false
                            })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Order type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = orderType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Order Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("dine-in", "takeout", "delivery").forEach { type ->
                            DropdownMenuItem(text = { Text(type.replaceFirstChar { it.uppercase() }) }, onClick = {
                                orderType = type
                                typeExpanded = false
                            })
                        }
                    }
                }

                errorMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cash < total && cashTendered.isNotBlank()) {
                        errorMsg = "Cash tendered is not enough"
                        return@Button
                    }
                    onComplete(selectedCustomerId, orderType, discountVal)
                },
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Complete Order")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
