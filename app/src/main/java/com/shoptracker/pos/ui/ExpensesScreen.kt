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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.shoptracker.pos.data.Expense
import com.shoptracker.pos.util.Fmt

/** Expenses of the open business day. They are tallied and shown when closing the day. */
@Composable
fun ExpensesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val day by vm.openDay.collectAsState()
    val data = rememberDayData(vm, setOfNotNull(day?.id))
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    val list = remember(data) { data.expenses.asReversed() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Expenses", onBack)
        if (day == null) {
            EmptyState(
                "No active business day",
                "Start a business day (NEW DAY) to record its expenses.",
                Modifier.weight(1f)
            )
        } else {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Total expenses today", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Fmt.peso(data.totalExpenses),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${list.size} item" + (if (list.size == 1) "" else "s"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (list.isEmpty()) {
                EmptyState(
                    "No expenses recorded",
                    "Tap ADD EXPENSE to log things like ingredients, gas, packaging or rider pay.",
                    Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { e ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(Fmt.time(e.createdAt), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(Fmt.peso(e.amountCentavos), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { deleting = e }) { Icon(Icons.Default.Delete, "Delete") }
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
            ) { Text("ADD EXPENSE", fontWeight = FontWeight.Bold) }
        }
    }

    if (adding) {
        ExpenseDialog(
            onDismiss = { adding = false },
            onSave = { desc, amount ->
                vm.addExpense(desc, amount)
                adding = false
            }
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this expense?") },
            text = { Text("${e.description} • ${Fmt.peso(e.amountCentavos)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteExpense(e)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

/** Popup form: description + amount. Also used from the close-day screen. */
@Composable
fun ExpenseDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val amountC = Fmt.parsePeso(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 80) description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    isError = showErrors && description.isBlank(),
                    supportingText = if (showErrors && description.isBlank()) ({ Text("Enter a description") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(
                    amount, { amount = it }, "Amount",
                    error = if (showErrors && (amountC == null || amountC <= 0L)) "Enter an amount above 0" else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (description.isBlank() || amountC == null || amountC <= 0L) showErrors = true
                else onSave(description, amountC)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
