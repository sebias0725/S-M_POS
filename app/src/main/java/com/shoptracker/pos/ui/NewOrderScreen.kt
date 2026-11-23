package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.DELIVERY_TIME_PENDING
import com.shoptracker.pos.data.DELIVERY_TIME_SLOTS
import com.shoptracker.pos.data.DeliveryRate
import com.shoptracker.pos.data.PAYMENT_COD
import com.shoptracker.pos.data.PAYMENT_METHODS
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.ORDER_PICKUP
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.util.Fmt

@Composable
fun NewOrderScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val day by vm.openDay.collectAsState()
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val rates by vm.rates.collectAsState()
    val customers by vm.customers.collectAsState()
    val active = remember(products) { products.filter { it.active } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("New order", onBack)
        when {
            day == null -> EmptyState(
                "No active business day",
                "Tap NEW DAY on the main screen to start a business day before taking orders."
            )
            active.isEmpty() -> EmptyState(
                "No active products",
                "Add or enable products first in \"Products\"."
            )
            else -> OrderForm(vm, active, categories, rates, customers, onDone)
        }
    }
}

@Composable
private fun OrderForm(
    vm: MainViewModel,
    products: List<Product>,
    categories: List<com.shoptracker.pos.data.Category>,
    rates: List<DeliveryRate>,
    customers: List<com.shoptracker.pos.data.Customer>,
    onDone: () -> Unit
) {
    var type by remember { mutableStateOf(ORDER_PICKUP) }
    var customer by remember { mutableStateOf("") }
    var chosenCustomer by remember { mutableStateOf<com.shoptracker.pos.data.Customer?>(null) }
    var saveNewCustomer by remember { mutableStateOf(true) }
    var customerPhone by remember { mutableStateOf("") }
    var customerInstructions by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    // null = "Custom" (manual amount). Otherwise the id of the chosen Delivery Rate; its fee is COPIED into `fee`.
    var selectedRateId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("Cash") }
    var paymentPaid by remember { mutableStateOf(false) }
    var deliveryTime by remember { mutableStateOf(DELIVERY_TIME_PENDING) }
    val qty = remember { mutableStateMapOf<Long, Int>() }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val delivery = type == ORDER_DELIVERY
    val selectedRate = rates.firstOrNull { it.id == selectedRateId }
    val isCustomFee = selectedRate == null
    val feeCentavos: Long? = if (delivery) Fmt.parsePeso(fee) else 0L
    val itemsTotal = products.sumOf { it.priceCentavos * (qty[it.id] ?: 0) }
    val totalUnits = products.sumOf { qty[it.id] ?: 0 }
    val grandTotal = itemsTotal + (feeCentavos ?: 0L)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeButton("PICKUP", type == ORDER_PICKUP, { type = ORDER_PICKUP }, Modifier.weight(1f))
                TypeButton("DELIVERY", type == ORDER_DELIVERY, { type = ORDER_DELIVERY }, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = customer,
                onValueChange = { if (it.length <= 60) { customer = it; chosenCustomer = null; showSuggestions = true } },
                label = { Text("Customer name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            if (showSuggestions && customer.isNotBlank()) {
                val matches = customers.filter { it.name.startsWith(customer.trim(), ignoreCase = true) }.take(8)
                if (matches.isNotEmpty()) {
                    androidx.compose.material3.Surface(tonalElevation = 3.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                        Column {
                            matches.forEach { saved ->
                                androidx.compose.material3.TextButton(onClick = {
                                    chosenCustomer = saved; customer = saved.name; address = saved.address
                                    fee = Fmt.plain(saved.deliveryFeeCentavos); selectedRateId = null
                                    customerPhone = saved.phone; customerInstructions = saved.note; note = saved.note
                                    showSuggestions = false
                                }, modifier = Modifier.fillMaxWidth()) { Text(saved.name, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            }
            if (chosenCustomer == null && customer.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(saveNewCustomer, { saveNewCustomer = it })
                    Text("Save as customer for future orders")
                }
                if (saveNewCustomer) {
                    OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Customer phone (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customerInstructions, { customerInstructions = it }, label = { Text("Permanent customer instructions (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (delivery) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { if (it.length <= 120) address = it },
                    label = { Text("Delivery address / area") },
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                DeliveryFeeDropdown(
                    rates = rates,
                    selectedRate = selectedRate,
                    onCustom = {
                        if (selectedRateId != null) {
                            selectedRateId = null
                            fee = ""
                        }
                    },
                    onRate = { r ->
                        selectedRateId = r.id
                        fee = Fmt.plain(r.feeCentavos) // snapshot: later edits to the rate do not touch this order
                        if (address.isBlank()) address = r.name
                    }
                )
                if (rates.isEmpty()) {
                    Text(
                        "No delivery rates yet. Add some in Delivery Rates, or use Custom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCustomFee) {
                    MoneyField(
                        fee, { fee = it }, "Custom delivery fee",
                        error = if (showErrors && feeCentavos == null) "Enter the delivery fee (0 if free)" else null
                    )
                }
                if (deliveryTime == DELIVERY_TIME_PENDING) {
                    Text(
                        "Pending = the customer has not confirmed a delivery time yet. You can change it later in Orders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DeliveryTimePicker(value = deliveryTime, onChange = { deliveryTime = it }, label = if (delivery) "Preferred delivery time" else "Preferred pickup time")

            OptionDropdown(
                label = "Payment method",
                selected = payment,
                options = PAYMENT_METHODS,
                onSelect = { payment = it }
            )

            OptionDropdown(
                label = "Payment status",
                selected = if (paymentPaid) "Paid" else "On Delivery / Pickup",
                options = listOf("Paid", "On Delivery / Pickup"),
                onSelect = { paymentPaid = it == "Paid" }
            )

            Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            groupedProducts(products, categories).forEach { (categoryName, categoryProducts) ->
                Text(categoryName, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp))
                categoryProducts.forEach { p ->
                    QtyRow(p, qty[p.id] ?: 0) { n ->
                        if (n <= 0) qty.remove(p.id) else qty[p.id] = n
                        Unit
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text("Note (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            SectionCard("Order summary") {
                LabelValue("Units", totalUnits.toString())
                LabelValue("Items total", Fmt.peso(itemsTotal))
                if (delivery) LabelValue("Delivery fee", if (feeCentavos == null) "-" else Fmt.peso(feeCentavos))
                LabelValue("TOTAL", Fmt.peso(grandTotal), bold = true)
            }
            if (showErrors && totalUnits == 0) {
                Text("Add at least one item.", color = negativeColor(), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = {
                if (totalUnits == 0 || feeCentavos == null) showErrors = true else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("SAVE ORDER", fontWeight = FontWeight.Bold) }
    }

    if (confirm && feeCentavos != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Record this order?") },
            text = {
                Text(
                    (if (delivery) "Delivery" else "Pickup") +
                        (if (customer.isNotBlank()) " • ${customer.trim()}" else "") + "\n" +
                        "$totalUnits units • items ${Fmt.peso(itemsTotal)}\n" +
                        (if (delivery) "Delivery fee ${Fmt.peso(feeCentavos)}\n" else "") +
                        (if (delivery) "Delivery time: $deliveryTime\n" else "Pickup time: $deliveryTime\n") +
                        "Payment: $payment • ${if (paymentPaid) "Paid" else "On Delivery / Pickup"}\n" +
                        "Total ${Fmt.peso(grandTotal)}\n\n" +
                        "It will be counted as sales of the current business day."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            confirm = false
                            val items = products.mapNotNull { p ->
                                val n = qty[p.id] ?: 0
                                if (n > 0) p.id to n else null
                            }
                            vm.createOrder(customer, type, address, feeCentavos, note, items, payment, deliveryTime, paymentPaid) { ok ->
                                busy = false
                                if (ok) {
                                    if (chosenCustomer == null && saveNewCustomer && customer.isNotBlank() &&
                                        customers.none { it.name.equals(customer.trim(), ignoreCase = true) }) {
                                        vm.saveCustomer(com.shoptracker.pos.data.Customer(name = customer.trim(),
                                            phone = customerPhone.trim(), address = address.trim(),
                                            deliveryFeeCentavos = if (delivery) feeCentavos else 0L,
                                            note = customerInstructions.trim()))
                                    }
                                    onDone()
                                }
                            }
                        }
                    }
                ) { Text("Save order") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

/** First option is always "Custom"; then every Delivery Rate (A-Z as supplied by the DAO). */
@Composable
private fun DeliveryFeeDropdown(
    rates: List<DeliveryRate>,
    selectedRate: DeliveryRate?,
    onCustom: () -> Unit,
    onRate: (DeliveryRate) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (selectedRate == null) CUSTOM_FEE_LABEL else rateOptionLabel(selectedRate),
            onValueChange = {},
            readOnly = true,
            label = { Text("Delivery fee") },
            trailingIcon = { Text("▾", style = MaterialTheme.typography.titleMedium) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(CUSTOM_FEE_LABEL, fontWeight = FontWeight.Bold) },
                onClick = { onCustom(); open = false }
            )
            rates.forEach { r ->
                DropdownMenuItem(
                    text = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.name, modifier = Modifier.weight(1f))
                            Text(Fmt.peso(r.feeCentavos), fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = { onRate(r); open = false }
                )
            }
        }
    }
}

private const val CUSTOM_FEE_LABEL = "Custom"

private fun rateOptionLabel(r: DeliveryRate): String = r.name + " • " + Fmt.peso(r.feeCentavos)

@Composable
private fun TypeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QtyRow(product: Product, qty: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                Fmt.peso(product.priceCentavos) + "  •  stock ${product.stock}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepButton("−", onClick = { onChange((qty - 1).coerceAtLeast(0)) }, enabled = qty > 0)
        OutlinedTextField(
            value = if (qty == 0) "" else qty.toString(),
            onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) onChange((v.toIntOrNull() ?: 0).coerceIn(0, product.stock.coerceAtLeast(0))) },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(84.dp)
        )
        StepButton("+", onClick = { onChange(qty + 1) }, enabled = qty < product.stock)
    }
}
