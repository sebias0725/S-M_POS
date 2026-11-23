package com.shoptracker.pos.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Exact hour/minute picker with a separate Pending selection. */
@Composable
fun DeliveryTimePicker(value: String, onChange: (String) -> Unit, label: String = "Preferred delivery time") {
    val context = LocalContext.current
    val minutes = parseDeliveryMinutes(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange("Pending") }, modifier = Modifier.weight(1f)) {
                Text(if (minutes == null) "✓ Pending" else "Pending")
            }
            OutlinedButton(onClick = {
                val start = minutes ?: 18 * 60
                TimePickerDialog(context, { _, hour, minute ->
                    val amPm = if (hour < 12) "AM" else "PM"
                    onChange(String.format(Locale.US, "%d:%02d%s", (hour + 11) % 12 + 1, minute, amPm))
                }, start / 60, start % 60, false).show()
            }, modifier = Modifier.weight(1f)) {
                Text(if (minutes == null) "◷ Set time" else "◷ $value")
            }
        }
    }
}

/** Supports new exact times and legacy time ranges without changing old order records. */
fun parseDeliveryMinutes(raw: String): Int? {
    val text = raw.trim().uppercase(Locale.US)
    val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)""").find(text) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 1..12 || minute !in 0..59) return null
    return ((hour % 12) + if (match.groupValues[3] == "PM") 12 else 0) * 60 + minute
}
