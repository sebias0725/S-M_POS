package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.Expense
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.data.Waste
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shoptracker.pos.data.DayStats
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.data.finalFloatFrom
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val days by vm.allDays.collectAsState()
    val txns by vm.allTxns.collectAsState()
    val orders by vm.allOrders.collectAsState()
    var deletingDay by remember { mutableStateOf<BusinessDay?>(null) }

    val totals: Map<Long, DayStats> = remember(days, txns, orders) {
        val tx = txns.groupBy { it.businessDayId }
        val od = orders.groupBy { it.businessDayId }
        days.associate { d -> d.id to computeStats(tx[d.id] ?: emptyList(), od[d.id] ?: emptyList()) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Business day history", onBack)
        if (days.isEmpty()) {
            EmptyState("No business days yet", "Your daily records will be listed here after you start your first day.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days, key = { it.id }) { d ->
                    val tot = totals[d.id]
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDay(d.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.date(d.openedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (d.isOpen) "Open" else "Closed ${Fmt.time(d.closedAt ?: d.openedAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (d.isOpen) positiveColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (!d.isOpen) TextButton(onClick = { deletingDay = d }) { Text("Delete day", color = MaterialTheme.colorScheme.error) }
                                Text(Fmt.peso(tot?.gross ?: 0L), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${tot?.units ?: 0} units", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    deletingDay?.let { d ->
        AlertDialog(onDismissRequest = { deletingDay = null }, title = { Text("Permanently delete business day?") },
            text = { Text("${Fmt.date(d.openedAt)}: all orders, sales, expenses and wastes will be deleted. Inventory will be restored for completed sales and wastes. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteBusinessDay(d.id); deletingDay = null }) { Text("Delete day permanently") } },
            dismissButton = { TextButton(onClick = { deletingDay = null }) { Text("Keep day") } })
    }
} 

@Composable
fun DayDetailScreen(
    vm: MainViewModel,
    dayId: Long,
    onBack: () -> Unit,
    onCloseDay: () -> Unit,
    onEditOrder: (Long) -> Unit
) {
    val days by vm.allDays.collectAsState()
    val day = days.firstOrNull { it.id == dayId }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Day summary", onBack)
        if (day == null) {
            EmptyState("Day not found", "This business day is no longer available.")
        } else {
            DayDetailContent(vm, day, onCloseDay, onEditOrder)
        }
    }
}

@Composable
private fun DayDetailContent(vm: MainViewModel, day: BusinessDay, onCloseDay: () -> Unit, onEditOrder: (Long) -> Unit) {
    val data = rememberDayData(vm, setOf(day.id))
    val stats = data.stats
    val payments = paymentSummary(data)
    var editFinals by remember { mutableStateOf(false) }
    var editDay by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var addingExpense by remember { mutableStateOf(false) }
    var editingSale by remember { mutableStateOf<SalesTransaction?>(null) }
    var editingWaste by remember { mutableStateOf<Waste?>(null) }
    var confirmDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wastes by remember { mutableStateOf<List<Waste>>(emptyList()) }
    LaunchedEffect(day.id, data.txns, data.expenses) { wastes = vm.getWastesForDay(day.id) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            Fmt.date(day.openedAt) + if (day.isOpen) " • open" else " • closed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        SectionCard("Sales") {
            LabelValue("Total units sold", stats.units.toString(), bold = true)
            LabelValue("Product sales", Fmt.peso(stats.productSales))
            LabelValue("Delivery fees (${stats.orderCount} order" + (if (stats.orderCount == 1) ")" else "s)"), Fmt.peso(stats.deliveryFees))
            LabelValue("Total gross sales", Fmt.peso(stats.gross), bold = true)
            LabelValue("Profit from products", Fmt.peso(stats.profit))
            val pct = Fmt.targetPct(stats.units, day.targetUnits)
            LabelValue("Target sales", if (day.targetUnits > 0) "${day.targetUnits} units" else "No target")
            if (pct != null) LabelValue("Target progress", "${pct.toPlainString()}%")
        }

        SectionCard("Expenses") {
            if (data.expenses.isEmpty()) {
                Text("No expenses recorded.", style = MaterialTheme.typography.bodyMedium)
            } else {
                data.expenses.forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { LabelValue("${e.description} (${Fmt.time(e.createdAt)})", Fmt.peso(e.amountCentavos)) }
                        if (!day.isOpen) TextButton(onClick = { editingExpense = e }) { Text("Edit") }
                        if (!day.isOpen) TextButton(onClick = { confirmDelete = { vm.deleteHistoricalExpense(e) } }) { Text("Delete") }
                    }
                }
            }
            if (!day.isOpen) OutlinedButton(onClick = { addingExpense = true }) { Text("Add missing expense") }
            LabelValue("Total expenses", Fmt.peso(data.totalExpenses), bold = true)
            LabelValue(
                "Net (profit + delivery fees - expenses)",
                Fmt.signedPeso(data.net),
                valueColor = deltaColor(data.net),
                bold = true
            )
        }

        SectionCard("Payments received") {
            LabelValue("Cash received", Fmt.peso(payments.cash))
            LabelValue("QR received", Fmt.peso(payments.qr))
            LabelValue("Total received", Fmt.peso(payments.received), bold = true)
            LabelValue("Unpaid orders", Fmt.peso(payments.outstanding))
            LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
        }

        SectionCard("Money (physical cash is tracked separately from sales)") {
            LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
            LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            LabelValue("Final floating money", day.finalFloatCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
            LabelValue("Final cash on hand", day.finalCashCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
            day.remitCentavos?.let { LabelValue("Remit", Fmt.peso(it)) }
        }

        if (day.isOpen) {
            Button(onClick = onCloseDay, modifier = Modifier.fillMaxWidth()) { Text("Close this day…") }
        } else {
            OutlinedButton(onClick = { editDay = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Edit day date, opening float, target and remittance")
            }
            OutlinedButton(onClick = { editFinals = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Review final amounts")
            }
            OutlinedButton(onClick = { vm.recalculateHistoricalDay(day.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Recalculate cash from corrected records")
            }
        }

        SectionCard("Products sold") {
            if (stats.products.isEmpty()) {
                Text("No sales recorded for this day.", style = MaterialTheme.typography.bodyMedium)
            } else {
                stats.products.forEach { p ->
                    LabelValue("${p.name} × ${p.units}", Fmt.peso(p.revenue))
                }
            }
        }

        if (!day.isOpen) SectionCard("Orders (${data.orders.size})") {
            data.orders.forEach { order ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${order.id} • ${order.customerName} • ${order.paymentMethod} • ${if (order.paid) "Paid" else "Unpaid"}", Modifier.weight(1f))
                    if (order.status == STATUS_COMPLETED) {
                        TextButton(onClick = { onEditOrder(order.id) }) { Text("Edit") }
                        TextButton(onClick = { confirmDelete = { vm.cancelOrder(order.id) } }) { Text("Cancel") }
                    }
                    TextButton(onClick = { confirmDelete = { vm.deleteOrder(order.id) } }) { Text("Delete") }
                }
            }
        }
        if (!day.isOpen) SectionCard("Wastes (${wastes.size})") {
            wastes.forEach { waste ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${waste.productName} × ${waste.quantity} • ${waste.reason}", Modifier.weight(1f))
                    TextButton(onClick = { editingWaste = waste }) { Text("Edit") }
                    TextButton(onClick = { confirmDelete = { vm.deleteHistoricalWaste(waste) } }) { Text("Delete") }
                }
            }
        }
        SectionCard("Transactions (${data.txns.size})") {
            if (data.txns.isEmpty()) {
                Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                data.txns.asReversed().forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    val done = t.status == STATUS_COMPLETED
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (t.quantity > 1) "${t.quantity} × " else "") + t.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (done) TextDecoration.None else TextDecoration.LineThrough
                            )
                            Text(
                                "#${t.id} • ${Fmt.time(t.timestamp)}" +
                                    (if (t.orderId != null) " • order #${t.orderId}" else "") +
                                    (if (done) "" else " • UNDONE"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.peso(t.totalCentavos), style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (done) TextDecoration.None else TextDecoration.LineThrough)
                            if (!day.isOpen && done && t.orderId == null) {
                                TextButton(onClick = { editingSale = t }) { Text("Edit") }
                                TextButton(onClick = { confirmDelete = { vm.undoHistoricalSale(t) } }) { Text("Reverse") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editDay) HistoricalDayDialog(day, onDismiss = { editDay = false }) { date, opening, target, remit ->
        vm.editHistoricalDay(day.id, date, opening, target, remit)
        editDay = false
    }
    if (addingExpense) HistoricalExpenseDialog(null, onDismiss = { addingExpense = false }) { name, amount ->
        vm.addHistoricalExpense(day.id, name, amount); addingExpense = false
    }
    editingExpense?.let { e -> HistoricalExpenseDialog(e, onDismiss = { editingExpense = null }) { name, amount ->
        vm.editHistoricalExpense(e, name, amount); editingExpense = null
    } }
    editingSale?.let { tx -> HistoricalSaleDialog(tx, onDismiss = { editingSale = null }) { quantity, price ->
        vm.editHistoricalSale(tx, quantity, price); editingSale = null
    } }
    editingWaste?.let { waste -> HistoricalWasteDialog(waste, onDismiss = { editingWaste = null }) { quantity, reason, note ->
        vm.editHistoricalWaste(waste, quantity, reason, note); editingWaste = null
    } }
    confirmDelete?.let { action -> AlertDialog(onDismissRequest = { confirmDelete = null },
        title = { Text("Confirm historical correction") },
        text = { Text("This changes saved historical records and may adjust inventory and financial totals. Continue?") },
        confirmButton = { TextButton(onClick = { action(); confirmDelete = null }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }) }

    if (editFinals) {
        EditFinalsDialog(
            day = day,
            onDismiss = { editFinals = false },
            onSave = { f, c, r ->
                vm.updateFinals(day.id, f, c, r)
                editFinals = false
            }
        )
    }
}

@Composable
private fun EditFinalsDialog(day: BusinessDay, onDismiss: () -> Unit, onSave: (Long, Long, Long?) -> Unit) {
    var f by remember { mutableStateOf(day.finalFloatCentavos?.let { Fmt.plain(it) } ?: "") }
    var c by remember { mutableStateOf(day.finalCashCentavos?.let { Fmt.plain(it) } ?: "") }
    var r by remember { mutableStateOf(day.remitCentavos?.let { Fmt.plain(it) } ?: "") }
    var showErrors by remember { mutableStateOf(false) }
    // Days closed with a remit keep floating = cash - remit; older days keep the manual entry.
    val hasRemit = day.remitCentavos != null
    val rc = Fmt.parsePeso(r)
    val cc = Fmt.parsePeso(c)
    val fc = if (hasRemit) { if (cc != null && rc != null) finalFloatFrom(cc, rc) else null } else Fmt.parsePeso(f)
    val remitTooHigh = hasRemit && fc != null && fc < 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final amounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasRemit) {
                    MoneyField(c, { c = it }, "Final cash on hand", error = if (showErrors && cc == null) "Enter an amount" else null)
                    MoneyField(
                        r, { r = it }, "Remit",
                        error = if (showErrors && rc == null) "Enter an amount" else if (remitTooHigh) "Remit cannot be more than cash on hand" else null
                    )
                    LabelValue("Final floating money", if (fc != null && !remitTooHigh) Fmt.peso(fc) else "-", bold = true)
                } else {
                    MoneyField(f, { f = it }, "Final floating money", error = if (showErrors && fc == null) "Enter an amount" else null)
                    MoneyField(c, { c = it }, "Final cash on hand", error = if (showErrors && cc == null) "Enter an amount" else null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (fc == null || cc == null || remitTooHigh || (hasRemit && rc == null)) showErrors = true
                else onSave(fc, cc, if (hasRemit) rc else null)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HistoricalDayDialog(day: BusinessDay, onDismiss: () -> Unit, onSave: (Long, Long, Int, Long) -> Unit) {
    val format = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
    var date by remember(day.id) { mutableStateOf(format.format(Date(day.openedAt))) }
    var opening by remember(day.id) { mutableStateOf(Fmt.plain(day.openingFloatCentavos)) }
    var target by remember(day.id) { mutableStateOf(day.targetUnits.toString()) }
    var remit by remember(day.id) { mutableStateOf(Fmt.plain(day.remitCentavos ?: 0L)) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit historical business day") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Business date (YYYY-MM-DD)") })
            OutlinedTextField(opening, { opening = it }, label = { Text("Starting floating money (₱)") })
            OutlinedTextField(target, { target = it }, label = { Text("Target units") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(remit, { remit = it }, label = { Text("Remittance (₱)") })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val dateMillis = try { format.parse(date)?.time } catch (_: Exception) { null }
        val float = Fmt.parsePeso(opening); val rem = Fmt.parsePeso(remit); val units = target.toIntOrNull()
        if (dateMillis == null || float == null || rem == null || units == null || float < 0 || rem < 0 || units < 0) error = "Enter valid nonnegative values and a date"
        else onSave(dateMillis, float, units, rem)
    }) { Text("Save changes") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun HistoricalExpenseDialog(expense: Expense?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var description by remember(expense?.id) { mutableStateOf(expense?.description ?: "") }
    var amount by remember(expense?.id) { mutableStateOf(Fmt.plain(expense?.amountCentavos ?: 0L)) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Add historical expense" else "Edit expense") }, text = {
        Column { OutlinedTextField(description, { description = it }, label = { Text("Description") })
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₱)") })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) }
    }, confirmButton = { TextButton(onClick = { val cents = Fmt.parsePeso(amount)
        if (description.isBlank() || cents == null || cents < 0) error = "Enter a description and valid amount" else onSave(description, cents)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun HistoricalSaleDialog(tx: SalesTransaction, onDismiss: () -> Unit, onSave: (Int, Long) -> Unit) {
    var quantity by remember(tx.id) { mutableStateOf(tx.quantity.toString()) }
    var price by remember(tx.id) { mutableStateOf(Fmt.plain(tx.unitPriceCentavos)) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Correct sale: ${tx.productName}") }, text = {
        Column { OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(price, { price = it }, label = { Text("Unit price (₱)") })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) }
    }, confirmButton = { TextButton(onClick = { val q = quantity.toIntOrNull(); val p = Fmt.parsePeso(price)
        if (q == null || q <= 0 || p == null || p < 0) error = "Enter valid quantity and price" else onSave(q, p)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun HistoricalWasteDialog(waste: Waste, onDismiss: () -> Unit, onSave: (Int, String, String) -> Unit) {
    var quantity by remember(waste.id) { mutableStateOf(waste.quantity.toString()) }
    var reason by remember(waste.id) { mutableStateOf(waste.reason) }
    var note by remember(waste.id) { mutableStateOf(waste.note) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Correct waste: ${waste.productName}") }, text = {
        Column { OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") })
            OutlinedTextField(note, { note = it }, label = { Text("Notes") })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) }
    }, confirmButton = { TextButton(onClick = { val q = quantity.toIntOrNull()
        if (q == null || q <= 0 || reason.isBlank()) error = "Enter valid quantity and reason" else onSave(q, reason, note)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
