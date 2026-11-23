package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.util.Fmt

@Composable
fun WastesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val products by vm.products.collectAsState()
    val day by vm.openDay.collectAsState()
    val wastes by vm.wastes.collectAsState()
    val current = wastes.filter { it.businessDayId == day?.id }
    var productId by remember { mutableStateOf<Long?>(null) }
    var qty by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("Damaged / Dropped") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var reasonExpanded by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader("Wastes", onBack)
        if (day == null) Text("Start a business day to record wastes.")
        else {
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(products.firstOrNull { it.id == productId }?.name ?: "Select product")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    products.filter { it.active }.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name + " (stock ${p.stock})") }, onClick = { productId = p.id; expanded = false })
                    }
                }
            }
            OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit) }, label = { Text("Quantity wasted") }, singleLine = true)
            Box {
                OutlinedButton(onClick = { reasonExpanded = true }) { Text(reason) }
                DropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                    listOf("Damaged / Dropped", "Spoiled", "Unsold", "Other").forEach { r ->
                        DropdownMenuItem(text = { Text(r) }, onClick = { reason = r; reasonExpanded = false })
                    }
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") })
            Button(enabled = productId != null && (qty.toIntOrNull() ?: 0) > 0 && (qty.toIntOrNull() ?: 0) <= (products.firstOrNull { it.id == productId }?.stock ?: 0), onClick = {
                productId?.let { vm.recordWaste(it, qty.toIntOrNull() ?: 0, reason, note) }
                note = ""; qty = "1"
            }) { Text("Record waste") }
        }
        Text("Today's wastes: ${current.sumOf { it.quantity }} units • estimated value ${Fmt.peso(current.sumOf { it.unitValueCentavos * it.quantity })}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(current, key = { it.id }) { w ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${w.quantity} × ${w.productName} — ${Fmt.peso(w.unitValueCentavos * w.quantity)}")
                        Text("${w.reason} • ${Fmt.time(w.createdAt)}")
                        if (w.note.isNotBlank()) Text(w.note)
                        TextButton(onClick = { deleting = w.id }) { Text("Remove") }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove waste record?") },
            text = { Text("The recorded quantity will be restored to stock.") },
            confirmButton = { TextButton(onClick = { vm.removeWaste(id); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
}
