package com.shoptracker.pos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.DayData
import java.time.LocalDate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.DELIVERY_TIME_SLOTS
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

/** Orders of the open business day (or of the latest day when none is open). */
@Composable
fun OrdersScreen(vm: MainViewModel, onBack: () -> Unit, onNewOrder: () -> Unit, onEditOrder: (Long) -> Unit) {
    val open by vm.openDay.collectAsState()
    val days by vm.allDays.collectAsState()
    val today = Fmt.localDate(System.currentTimeMillis())
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    // A calendar date may have multiple business sessions. Include all of them.
    val selectedDays = remember(days, selectedDate) {
        days.filter { Fmt.localDate(it.openedAt) == selectedDate }
    }
    val day = selectedDays.firstOrNull { it.isOpen } ?: selectedDays.firstOrNull()
    val data = rememberDayData(vm, selectedDays.map { it.id }.toSet())
    var cancelling by remember { mutableStateOf<SalesOrder?>(null) }
    var deleting by remember { mutableStateOf<SalesOrder?>(null) }
    var editingTime by remember { mutableStateOf<SalesOrder?>(null) }
    var details by remember { mutableStateOf<SalesOrder?>(null) }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reportPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val orders = remember(data) {
        data.orders.filter { it.status == STATUS_COMPLETED || it.status == com.shoptracker.pos.data.STATUS_CANCELLED }
            .sortedWith(compareBy<SalesOrder> { deliverySortKey(it.deliveryTime) }.thenBy { it.createdAt })
    }
    fun copyOrders() {
        if (orders.isEmpty()) return
        val included = if (reportPrefs.getBoolean("report_cancelled", true)) orders else orders.filter { it.status != com.shoptracker.pos.data.STATUS_CANCELLED }
        val body = formatMessengerOrders(selectedDate, included, data, reportPrefs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Orders ${Fmt.localDateText(selectedDate)}", body))
        copied = true
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Orders • ${Fmt.localDateText(selectedDate)}", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text(if (selectedDate == today) "Today ▾" else "${Fmt.localDateText(selectedDate)} ▾")
            }
            FilledTonalIconButton(
                onClick = { copyOrders() },
                enabled = orders.isNotEmpty(),
                modifier = Modifier.size(52.dp).semantics { contentDescription = "Copy all orders for selected date" }
            ) {
                val iconColor = MaterialTheme.colorScheme.onSecondaryContainer
                Canvas(Modifier.size(25.dp)) {
                    val line = 2.2.dp.toPx()
                    // Two overlapping rounded rectangles, matching the requested copy icon.
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(size.width * .34f, size.height * .08f),
                        size = Size(size.width * .59f, size.height * .65f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = line)
                    )
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(size.width * .08f, size.height * .34f),
                        size = Size(size.width * .59f, size.height * .59f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = line)
                    )
                }
            }
        }
        if (copied) Text("${orders.size} orders copied to clipboard", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.primary)
        if (orders.isEmpty()) {
            EmptyState(
                "No orders yet",
                "Bulk orders, pickups and deliveries you record with NEW ORDER will be listed here.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    var swipeDistance by remember(o.id) { mutableStateOf(0f) }
                    Card(
                        Modifier.fillMaxWidth()
                            .pointerInput(o.id, o.delivered) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeDistance < -100f && !o.delivered && day?.isOpen == true) vm.setOrderDelivered(o.id, true)
                                        swipeDistance = 0f
                                    },
                                    onDragCancel = { swipeDistance = 0f },
                                    onHorizontalDrag = { change, delta ->
                                        swipeDistance += delta
                                        if (kotlin.math.abs(swipeDistance) > 15f) change.consume()
                                    }
                                )
                            }.clickable { details = o },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("#${orders.indexOf(o) + 1}  •  ${if (o.type == ORDER_DELIVERY) "DELIVERY" else "PICKUP"}",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(o.customerName.ifBlank { "Customer #${o.id}" },
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (o.type == ORDER_DELIVERY) Text(o.deliveryTime.ifBlank { "Pending" },
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Paid", style = MaterialTheme.typography.labelSmall)
                                    Checkbox(checked = o.paid, onCheckedChange = { vm.setOrderPaid(o.id, it) }, enabled = day?.isOpen == true && o.status == STATUS_COMPLETED)
                                }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(if (o.type == ORDER_DELIVERY) "Delivered" else "Picked Up",
                                        style = MaterialTheme.typography.labelSmall)
                                    Checkbox(checked = o.delivered, onCheckedChange = { vm.setOrderDelivered(o.id, it) }, enabled = day?.isOpen == true && o.status == STATUS_COMPLETED)
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onNewOrder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("NEW ORDER", fontWeight = FontWeight.Bold) }
    }

    if (showDatePicker) {
        val availableDates = days.map { Fmt.localDate(it.openedAt) }.distinct().sortedDescending()
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select order date") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { selectedDate = today; copied = false; showDatePicker = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Today • ${Fmt.localDateText(today)}")
                    }
                    availableDates.filter { it != today }.forEach { date ->
                        OutlinedButton(onClick = { selectedDate = date; copied = false; showDatePicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(Fmt.localDateText(date))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("Close") } }
        )
    }

    details?.let { selected ->
        val o = orders.firstOrNull { it.id == selected.id } ?: selected
        val lines = data.txns.filter { it.orderId == o.id && it.status == STATUS_COMPLETED }
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("Order #${o.id} • ${o.customerName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { Text("${it.quantity} × ${it.productName} — ${Fmt.peso(it.totalCentavos)}") }
                    Text("Delivery fee: ${Fmt.peso(o.deliveryFeeCentavos)}")
                    Text("Total: ${Fmt.peso(lines.sumOf { it.totalCentavos } + o.deliveryFeeCentavos)}")
                    Text("Payment: ${o.paymentMethod.ifBlank { "Not recorded" }}")
                    Text("Address: ${o.address}")
                    Text("Notes: ${o.note}")
                    Text("Delivery time: ${o.deliveryTime.ifBlank { "Pending" }}")
                    if (day?.isOpen == true && o.status == STATUS_COMPLETED) {
                        OutlinedButton(onClick = { details = null; onEditOrder(o.id) }) { Text("Edit all order details") }
                        OutlinedButton(onClick = { details = null; cancelling = o }) { Text("Cancel order") }
                    }
                    if (day?.isOpen == true) {
                        OutlinedButton(onClick = { details = null; deleting = o }) { Text("Permanently delete order", color = MaterialTheme.colorScheme.error) }
                    }
                    if (o.type == ORDER_DELIVERY && day?.isOpen == true && o.status == STATUS_COMPLETED) {
                        OutlinedButton(onClick = { editingTime = o; details = null }) { Text("Edit delivery time") }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = o.paid, onCheckedChange = { vm.setOrderPaid(o.id, it) }, enabled = selectedDays.any { it.id == o.businessDayId && it.isOpen })
                        Text("Paid")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = o.delivered, onCheckedChange = { vm.setOrderDelivered(o.id, it) }, enabled = selectedDays.any { it.id == o.businessDayId && it.isOpen })
                        Text(if (o.type == ORDER_DELIVERY) "Delivered (uncheck to reverse)" else "Picked Up (uncheck to reverse)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } }
        )
    }

    editingTime?.let { o ->
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text("Delivery time • order #${o.id}") },
            text = {
                DeliveryTimePicker(value = o.deliveryTime.ifBlank { "Pending" }, onChange = {
                    vm.updateDeliveryTime(o.id, it)
                    editingTime = null
                })
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text("Close") } }
        )
    }

    deleting?.let { o ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete order #${o.id} permanently?") },
            text = { Text("This cannot be undone. Order and sale lines will be removed from the database. Stock will be restored only if the order was active.") },
            confirmButton = { TextButton(onClick = { vm.deleteOrder(o.id); deleting = null }) { Text("Delete permanently") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
    cancelling?.let { o ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel order #${o.id}?") },
            text = {
                Text("Its items and delivery fee will be removed from today's sales and the stock will be put back. The order stays in your records as CANCELLED.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelOrder(o.id)
                    cancelling = null
                }) { Text("Cancel order") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep") } }
        )
    }
}

/** Pending and invalid times appear after all specified times. */
private fun deliverySortKey(time: String): Int = parseDeliveryMinutes(time) ?: Int.MAX_VALUE

/** Plain-text Messenger report. Uses saved transaction snapshots, not current product prices. */
internal fun formatMessengerOrders(date: LocalDate, orders: List<SalesOrder>, data: DayData, prefs: android.content.SharedPreferences? = null): String {
    val today = Fmt.localDate(System.currentTimeMillis())
    val title = if (date == today) (prefs?.getString("report_heading", "‼️Today's orders") ?: "‼️Today's orders") else "‼️Orders for ${Fmt.localDateText(date)}"
    return buildString {
        append(title)
        orders.forEachIndexed { index, order ->
            val lines = data.txns.filter { it.orderId == order.id && (it.status == STATUS_COMPLETED || order.status == com.shoptracker.pos.data.STATUS_CANCELLED) }
            val status = when {
                order.status == com.shoptracker.pos.data.STATUS_CANCELLED -> " [❌ Cancelled]"
                order.delivered && order.type == ORDER_DELIVERY -> " [✅ Delivered]"
                order.delivered -> " [✅ Picked Up]"
                else -> " [⏳ Pending]"
            }
            append("\n\n${index + 1}. ${order.customerName.ifBlank { "Customer #${order.id}" }}$status")
            if (lines.isNotEmpty()) append("\n- " + lines.joinToString(", ") { "${it.quantity}x ${it.productName}" })
            val total = lines.sumOf { it.totalCentavos } + order.deliveryFeeCentavos
            val annotations = mutableListOf<String>()
            if (order.deliveryFeeCentavos > 0) annotations.add("${Fmt.peso(order.deliveryFeeCentavos)}df")
            if (prefs?.getBoolean("report_payment", true) != false && order.paymentMethod.isNotBlank()) annotations.add(order.paymentMethod + if (order.paid) " paid" else "")
            append("\n- ${Fmt.peso(total)}")
            if (annotations.isNotEmpty()) append(" (${annotations.joinToString("; ")})")
            if (prefs?.getBoolean("report_address", true) != false && order.address.isNotBlank()) append("\n- ${order.address.trim()}")
            val time = order.deliveryTime.trim()
            if (order.type == ORDER_DELIVERY) {
                append("\n- DELIVER ${time.ifBlank { "Pending" }}")
            } else {
                append("\n- PICK UP ${time.ifBlank { "Pending" }}")
            }
            if (prefs?.getBoolean("report_notes", true) != false && order.note.isNotBlank()) append("\n- ${order.note.trim()}")
        }
    }
}
