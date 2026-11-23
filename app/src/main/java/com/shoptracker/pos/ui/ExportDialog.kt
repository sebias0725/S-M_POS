package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** days is newest-first. */
@Composable
fun ExportDialog(
    days: List<BusinessDay>,
    onDismiss: () -> Unit,
    onConfirm: (ExportScope, String) -> Unit
) {
    var option by remember { mutableIntStateOf(0) } // 0 = today, 1 = selected day, 2 = all
    var selectedDayId by remember { mutableLongStateOf(days.firstOrNull()?.id ?: -1L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export to Excel") },
        text = {
            if (days.isEmpty()) {
                Text("There are no business days to export yet. Start a day and record some sales first.")
            } else {
                Column {
                    OptionRow("Today's records", option == 0) { option = 0 }
                    OptionRow("A selected business day", option == 1) { option = 1 }
                    if (option == 1) {
                        Column(
                            Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 24.dp)
                        ) {
                            days.forEach { d ->
                                OptionRow(
                                    Fmt.shortDate(d.openedAt) + if (d.isOpen) " (open)" else "",
                                    selectedDayId == d.id
                                ) { selectedDayId = d.id }
                            }
                        }
                    }
                    OptionRow("All historical records", option == 2) { option = 2 }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = {
                    val latest = days.first()
                    when (option) {
                        0 -> onConfirm(ExportScope.Today, "sales_${Fmt.isoDate(latest.openedAt)}.xlsx")
                        1 -> {
                            val d = days.firstOrNull { it.id == selectedDayId } ?: latest
                            onConfirm(ExportScope.Day(d.id), "sales_${Fmt.isoDate(d.openedAt)}.xlsx")
                        }
                        else -> onConfirm(ExportScope.All, "sales_all_${Fmt.isoDate(System.currentTimeMillis())}.xlsx")
                    }
                }
            ) { Text("Choose location & export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
