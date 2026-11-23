package com.shoptracker.pos.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.DeliveryRate
import com.shoptracker.pos.util.Fmt

/** Delivery Rates: add, edit and delete location rates. Orders keep their own copy of the fee. */
@Composable
fun RatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rates by vm.rates.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DeliveryRate?>(null) }
    var deleting by remember { mutableStateOf<DeliveryRate?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Delivery Rates", onBack)
        if (rates.isEmpty()) {
            EmptyState(
                "No delivery rates yet",
                "Add each location with its delivery fee. You can pick from this list when taking a delivery order.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rates, key = { it.id }) { r ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (r.note.isNotBlank()) {
                                        Text(r.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    Fmt.peso(r.feeCentavos),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editing = r }) { Text("Edit") }
                                TextButton(onClick = { deleting = r }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("ADD DELIVERY RATE", fontWeight = FontWeight.Bold) }
    }

    if (adding) {
        RateDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { name, fee, note ->
                vm.addRate(name, fee, note)
                adding = false
            }
        )
    }
    editing?.let { r ->
        RateDialog(
            initial = r,
            onDismiss = { editing = null },
            onSave = { name, fee, note ->
                vm.updateRate(r.copy(name = name.trim(), feeCentavos = fee, note = note.trim()))
                editing = null
            }
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${r.name}?") },
            text = { Text("This only removes it from the rates list. Past orders keep the delivery fee they were charged.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRate(r)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RateDialog(
    initial: DeliveryRate?,
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var fee by remember { mutableStateOf(if (initial != null) Fmt.plain(initial.feeCentavos) else "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var showErrors by remember { mutableStateOf(false) }
    val feeC = Fmt.parsePeso(fee)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add delivery rate" else "Edit delivery rate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Location / name") },
                    singleLine = true,
                    isError = showErrors && name.isBlank(),
                    supportingText = if (showErrors && name.isBlank()) ({ Text("Enter a location") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(
                    fee, { fee = it }, "Delivery fee",
                    error = if (showErrors && feeC == null) "Enter a fee (0 if free)" else null
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || feeC == null) showErrors = true else onSave(name, feeC, note)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
