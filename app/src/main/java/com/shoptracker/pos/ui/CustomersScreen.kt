package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.Customer
import com.shoptracker.pos.data.STATUS_CANCELLED
import com.shoptracker.pos.util.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val customers by vm.customers.collectAsState()
    val orders by vm.allOrders.collectAsState()
    val txns by vm.allTxns.collectAsState()
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var create by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Customer?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Customers", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Search customers") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add customer") }
            customers.filter { it.name.contains(search.trim(), ignoreCase = true) }.forEach { c ->
                val history = orders.filter { it.customerName.equals(c.name, ignoreCase = true) }.sortedByDescending { it.createdAt }
                val completed = history.filter { it.status != STATUS_CANCELLED }
                val lines = txns.filter { tx -> completed.any { it.id == tx.orderId } && tx.status == "COMPLETED" }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${completed.size} orders • ${lines.sumOf { it.quantity }} units purchased")
                        Text("Total spent: ${Fmt.peso(lines.sumOf { it.totalCentavos } + completed.sumOf { it.deliveryFeeCentavos })}")
                        if (c.phone.isNotBlank()) Text(c.phone)
                        if (c.address.isNotBlank()) Text(c.address)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = c }) { Text("Edit profile") }
                            TextButton(onClick = { deleting = c }) { Text("Delete profile") }
                        }
                        Text("Order history", style = MaterialTheme.typography.titleSmall)
                        if (history.isEmpty()) Text("No orders yet")
                        history.forEach { order ->
                            OutlinedCard(Modifier.fillMaxWidth().clickable { expanded = if (expanded == order.id) null else order.id }) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(dateFormat.format(Date(order.createdAt)), style = MaterialTheme.typography.labelLarge)
                                    Text("${order.type} • ${if (order.status == STATUS_CANCELLED) "Cancelled" else if (order.delivered) "Completed" else "Pending"}")
                                    if (expanded == order.id) {
                                        txns.filter { it.orderId == order.id }.forEach { Text("${it.quantity} × ${it.productName} — ${Fmt.peso(it.totalCentavos)}") }
                                        Text("Delivery fee: ${Fmt.peso(order.deliveryFeeCentavos)}")
                                        Text("Payment: ${order.paymentMethod} • ${if (order.paid) "Paid" else "Unpaid"}")
                                        if (order.deliveryTime.isNotBlank()) Text("Time: ${order.deliveryTime}")
                                        if (order.address.isNotBlank()) Text("Location: ${order.address}")
                                        if (order.note.isNotBlank()) Text("Notes: ${order.note}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (create || editing != null) {
        val old = editing ?: Customer(name = "")
        key(old.id, create) { CustomerEditor(old, onDismiss = { create = false; editing = null }, onSave = {
            vm.saveCustomer(it); create = false; editing = null
        }) }
    }
    deleting?.let { c -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete customer profile?") },
        text = { Text("${c.name} will be removed from saved customers. Existing orders will remain in history.") },
        confirmButton = { TextButton(onClick = { vm.deleteCustomer(c.id); deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}

@Composable
private fun CustomerEditor(initial: Customer, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var address by remember { mutableStateOf(initial.address) }
    var fee by remember { mutableStateOf(Fmt.plain(initial.deliveryFeeCentavos)) }
    var note by remember { mutableStateOf(initial.note) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "New customer" else "Edit customer") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Customer name") })
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") })
            OutlinedTextField(address, { address = it }, label = { Text("Default delivery location") })
            OutlinedTextField(fee, { fee = it }, label = { Text("Default delivery fee (₱)") })
            OutlinedTextField(note, { note = it }, label = { Text("Permanent customer instructions") })
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && Fmt.parsePeso(fee) != null, onClick = {
            onSave(initial.copy(name = name.trim(), phone = phone.trim(), address = address.trim(),
                deliveryFeeCentavos = Fmt.parsePeso(fee) ?: 0L, note = note.trim()))
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
