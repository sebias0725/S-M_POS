package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.finalFloatFrom
import com.shoptracker.pos.util.Fmt

/**
 * Two-step flow derived from the database state:
 *  - a business day is open  -> step 1: close it (review sales + expenses, enter final amounts)
 *  - no business day is open -> step 2: start the new day (previous day shown for reference)
 */
@Composable
fun NewDayScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val current = open
    if (current != null) {
        CloseDayStep(vm, current, onBack)
    } else {
        StartDayStep(vm, onBack, onDone)
    }
}

@Composable
private fun CloseDayStep(vm: MainViewModel, day: BusinessDay, onBack: () -> Unit) {
    val data = rememberDayData(vm, setOf(day.id))
    val stats = data.stats
    val products by vm.products.collectAsState()
    val lowStockThreshold = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getInt("low_stock_threshold", 5)
    val lowStock = products.filter { it.active && it.stock <= lowStockThreshold }.sortedWith(compareBy({ it.stock }, { it.name }))

    val payments = paymentSummary(data)
    val calculatedCash = day.openingFloatCentavos + payments.cash - data.totalExpenses
    var finalCash by remember { mutableStateOf("") }
    var remit by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var addingExpense by remember { mutableStateOf(false) }

    val cashC = calculatedCash
    val remitC = Fmt.parsePeso(remit)
    val floatC = if (cashC != null && remitC != null) finalFloatFrom(cashC, remitC) else null
    val remitTooHigh = floatC != null && floatC < 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Close current day", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Step 1 of 2. Close ${Fmt.date(day.openedAt)} before starting a new day.",
                style = MaterialTheme.typography.bodyMedium
            )
            SectionCard("Today's sales") {
                LabelValue("Total units sold", stats.units.toString(), bold = true)
                LabelValue("Product sales", Fmt.peso(stats.productSales))
                LabelValue("Delivery fees", Fmt.peso(stats.deliveryFees))
                LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
                LabelValue("Profit from products", Fmt.peso(stats.profit))
                if (day.targetUnits > 0) {
                    val pct = Fmt.targetPct(stats.units, day.targetUnits)
                    LabelValue("Target", "${stats.units} / ${day.targetUnits}" + (if (pct != null) " (${pct.toPlainString()}%)" else ""))
                }
            }

            SectionCard("Expenses today") {
                if (data.expenses.isEmpty()) {
                    Text("No expenses recorded.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    data.expenses.forEach { e ->
                        LabelValue(e.description, Fmt.peso(e.amountCentavos))
                    }
                }
                LabelValue("Total expenses", Fmt.peso(data.totalExpenses), bold = true)
                LabelValue(
                    "Net (profit + delivery fees - expenses)",
                    Fmt.signedPeso(data.net),
                    valueColor = deltaColor(data.net),
                    bold = true
                )
                OutlinedButton(onClick = { addingExpense = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add expense")
                }
            }

            SectionCard("Stock alerts (5 or fewer remaining)") {
                if (lowStock.isEmpty()) Text("No low-stock products.")
                else lowStock.forEach { product ->
                    LabelValue(product.name, if (product.stock == 0) "OUT OF STOCK" else "${product.stock} left")
                }
            }

            SectionCard("Opening amounts (for reference)") {
                LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
                LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            }

            SectionCard("Payments received") {
                LabelValue("Cash received", Fmt.peso(payments.cash))
                LabelValue("QR received", Fmt.peso(payments.qr))
                LabelValue("Total received", Fmt.peso(payments.received), bold = true)
                LabelValue("Outstanding payments", Fmt.peso(payments.outstanding))
                LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
            }
            SectionCard("Calculated cash on hand") {
                LabelValue("Starting float", Fmt.peso(day.openingFloatCentavos))
                LabelValue("+ Cash received", Fmt.peso(payments.cash))
                LabelValue("- Expenses", Fmt.peso(data.totalExpenses))
                LabelValue("Final cash on hand", Fmt.peso(calculatedCash), bold = true)
            }
            MoneyField(
                remit, { remit = it }, "Remit",
                error = when {
                    showErrors && remitC == null -> "Enter an amount (0 is fine)"
                    remitTooHigh -> "Remit cannot be more than cash on hand"
                    else -> null
                }
            )
            if (showErrors && cashC < 0) Text("Calculated cash is negative. Check expenses and payments.", color = MaterialTheme.colorScheme.error)
            SectionCard("Final floating money (cash on hand - remit)") {
                LabelValue(
                    "Final floating money",
                    if (floatC != null && !remitTooHigh) Fmt.peso(floatC) else "-",
                    bold = true
                )
            }
        }
        Button(
            onClick = {
                if (cashC < 0 || remitC == null || floatC == null || remitTooHigh) showErrors = true
                else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("CLOSE DAY", fontWeight = FontWeight.Bold) }
    }

    if (addingExpense) {
        ExpenseDialog(
            onDismiss = { addingExpense = false },
            onSave = { desc, amount ->
                vm.addExpense(desc, amount)
                addingExpense = false
            }
        )
    }

    if (confirm && floatC != null && cashC != null && remitC != null && !remitTooHigh) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Close this business day?") },
            text = {
                Text(
                    "No more sales, orders or expenses can be recorded for ${Fmt.shortDate(day.openedAt)} after closing.\n\n" +
                        "Cash on hand: ${Fmt.peso(cashC)}\nRemit: ${Fmt.peso(remitC)}\n" +
                        "Final floating money: ${Fmt.peso(floatC)}" +
                        "\n\nStock alerts:\n" + (if (lowStock.isEmpty()) "None" else lowStock.joinToString("\n") { "${it.name}: ${it.stock} remaining" })
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        confirm = false
                        vm.closeDay(day.id, cashC, remitC) { busy = false }
                    }
                ) { Text("Close day") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StartDayStep(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val days by vm.allDays.collectAsState()
    val prev = days.firstOrNull() // newest, and it is closed because no day is open
    val prevData = rememberDayData(vm, setOfNotNull(prev?.id))

    var openFloat by remember { mutableStateOf("") }
    
    
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val floatC = Fmt.parsePeso(openFloat)
    val cashC = floatC
    val targetN = 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (prev == null) "Start your first day" else "Start new day", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (prev != null) {
                val stats = prevData.stats
                SectionCard("Previous day • ${Fmt.shortDate(prev.openedAt)}") {
                    LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
                    LabelValue("Units sold", stats.units.toString())
                    LabelValue("Expenses", Fmt.peso(prevData.totalExpenses))
                    LabelValue("Final floating money", prev.finalFloatCentavos?.let { Fmt.peso(it) } ?: "-")
                    LabelValue("Final cash on hand", prev.finalCashCentavos?.let { Fmt.peso(it) } ?: "-")
                    Text(
                        "For reference only. Enter the amounts you actually start with today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Opening financial information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            MoneyField(
                openFloat, { openFloat = it }, "Opening floating money (PHP)",
                error = if (showErrors && floatC == null) "Enter an amount (0 is fine)" else null
            )
            if (prev?.finalFloatCentavos != null) {
                TextButton(onClick = { openFloat = Fmt.plain(prev.finalFloatCentavos) }) {
                    Text("Use previous final: ${Fmt.peso(prev.finalFloatCentavos)}")
                }
            }
        }
        Button(
            onClick = {
                if (floatC == null || cashC == null || targetN == null) showErrors = true else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("START NEW DAY", fontWeight = FontWeight.Bold) }
    }

    if (confirm && floatC != null && cashC != null && targetN != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start a new business day?") },
            text = {
                Text(
                    "Opening floating money: ${Fmt.peso(floatC)}\n" +
                        "Starting float will be used as opening cash on hand."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            confirm = false
                            vm.startDay(floatC, cashC, targetN) { ok ->
                                busy = false
                                if (ok) onDone()
                            }
                        }
                    }
                ) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
