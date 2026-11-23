package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.*
import com.shoptracker.pos.util.Fmt

/** Edits an order in an open or closed business day, with atomic inventory adjustments. */
@Composable
fun EditOrderScreen(vm: MainViewModel, orderId: Long, onBack: () -> Unit) {
    val orders by vm.allOrders.collectAsState()
    val txns by vm.allTxns.collectAsState()
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val days by vm.allDays.collectAsState()
    val original = orders.firstOrNull { it.id == orderId }
    val lines = remember(txns, orderId) { txns.filter { it.orderId == orderId && it.status == STATUS_COMPLETED } }
    if (original == null) { Column { ScreenHeader("Edit order", onBack); Text("Order no longer exists") }; return }
    val editable = days.any { it.id == original.businessDayId } && original.status == STATUS_COMPLETED
    var customer by remember(orderId) { mutableStateOf(original.customerName) }
    var type by remember(orderId) { mutableStateOf(original.type) }
    var address by remember(orderId) { mutableStateOf(original.address) }
    var fee by remember(orderId) { mutableStateOf(Fmt.plain(original.deliveryFeeCentavos)) }
    var note by remember(orderId) { mutableStateOf(original.note) }
    var payment by remember(orderId) { mutableStateOf(original.paymentMethod.ifBlank { PAYMENT_COD }) }
    var time by remember(orderId) { mutableStateOf(original.deliveryTime.ifBlank { "Pending" }) }
    var paid by remember(orderId) { mutableStateOf(original.paid) }
    var delivered by remember(orderId) { mutableStateOf(original.delivered) }
    val qty = remember(orderId) { mutableStateMapOf<Long, String>().apply { lines.forEach { put(it.productId, it.quantity.toString()) } } }
    val price = remember(orderId) { mutableStateMapOf<Long, String>().apply { lines.forEach { put(it.productId, Fmt.plain(it.unitPriceCentavos)) } } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Edit order #$orderId", onBack)
        if (!editable) Text("Cancelled orders cannot be edited.", Modifier.padding(12.dp))
        else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(customer, { customer = it }, label = { Text("Customer") }, modifier = Modifier.fillMaxWidth())
                OptionDropdown("Order type", type, listOf(ORDER_PICKUP, ORDER_DELIVERY), { type = it })
                OutlinedTextField(address, { address = it }, label = { Text("Address / location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fee, { fee = it }, label = { Text("Delivery fee (₱)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = type == ORDER_DELIVERY)
                OutlinedTextField(time, { time = it }, label = { Text("Preferred time / Pending") }, modifier = Modifier.fillMaxWidth())
                OptionDropdown("Payment method", payment, PAYMENT_METHODS, { payment = it })
                Row { Checkbox(paid, { paid = it }); Text("Paid", Modifier.padding(top = 12.dp)); Spacer(Modifier.width(12.dp)); Checkbox(delivered, { delivered = it }); Text(if (type == ORDER_DELIVERY) "Delivered" else "Picked up", Modifier.padding(top = 12.dp)) }
                Text("Products and unit prices", style = MaterialTheme.typography.titleMedium)
                groupedProducts(products.filter { it.active || qty.containsKey(it.id) }, categories).forEach { (category, group) ->
                    Text(category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    group.forEach { product ->
                        val originalQty = lines.filter { it.productId == product.id }.sumOf { it.quantity }
                        val available = product.stock + originalQty
                        Text("${product.name} • $available available (including current order)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(qty[product.id] ?: "0", { qty[product.id] = it.filter(Char::isDigit) }, label = { Text("Quantity") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(price[product.id] ?: Fmt.plain(product.priceCentavos), { price[product.id] = it }, label = { Text("Unit ₱") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = {
                val quantities = products.mapNotNull { p ->
                    val n = (qty[p.id] ?: "0").toIntOrNull() ?: 0
                    if (n <= 0) null else {
                        val cents = Fmt.parsePeso(price[p.id] ?: Fmt.plain(p.priceCentavos))
                        if (cents == null || cents < 0 || n > p.stock + lines.filter { it.productId == p.id }.sumOf { it.quantity }) { error = "Invalid price or insufficient stock: ${p.name}"; return@Button }
                        p.id to (n to cents)
                    }
                }.toMap()
                val deliveryFee = if (type == ORDER_DELIVERY) Fmt.parsePeso(fee) else 0L
                if (quantities.isEmpty() || deliveryFee == null || deliveryFee < 0) { error = "Enter at least one product and a valid delivery fee"; return@Button }
                saving = true
                vm.editOrder(orderId, original.copy(customerName = customer.trim(), type = type, address = address.trim(), deliveryFeeCentavos = deliveryFee, note = note.trim(), paymentMethod = payment, deliveryTime = time.trim(), paid = paid, delivered = delivered), quantities) { success -> saving = false; if (success) onBack() else error = "Unable to save changes. Check stock and try again." }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text(if (saving) "Saving..." else "Save Order Changes") }
        }
    }
}
