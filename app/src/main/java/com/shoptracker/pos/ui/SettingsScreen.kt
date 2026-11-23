package com.shoptracker.pos.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.shoptracker.pos.MainViewModel

/** Persistent preferences for operational defaults and reports. */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var lowStock by remember { mutableStateOf(prefs.getInt("low_stock_threshold", 5).toString()) }
    var heading by remember { mutableStateOf(prefs.getString("report_heading", "‼️Today's orders") ?: "‼️Today's orders") }
    var includeNotes by remember { mutableStateOf(prefs.getBoolean("report_notes", true)) }
    var includeCancelled by remember { mutableStateOf(prefs.getBoolean("report_cancelled", true)) }
    var includePayment by remember { mutableStateOf(prefs.getBoolean("report_payment", true)) }
    var includeAddress by remember { mutableStateOf(prefs.getBoolean("report_address", true)) }
    var dark by remember { mutableStateOf(LocalThemePreference.dark) }
    val confirm by vm.confirmTaps.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings & Customization", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSection("Appearance")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Dark mode", Modifier.weight(1f)); Switch(dark, { dark = it; LocalThemePreference.dark = it; prefs.edit().putBoolean("dark_mode", it).apply() })
            }
            SettingsSection("Inventory alerts")
            OutlinedTextField(lowStock, { lowStock = it.filter(Char::isDigit).take(4) }, label = { Text("Low stock alert threshold (units)") }, modifier = Modifier.fillMaxWidth())
            SettingsSection("Order reports")
            OutlinedTextField(heading, { heading = it }, label = { Text("Today's Messenger report heading") }, modifier = Modifier.fillMaxWidth())

            Toggle("Include customer notes", includeNotes, { includeNotes = it })
            Toggle("Include cancelled orders", includeCancelled, { includeCancelled = it })
            Toggle("Include payment details", includePayment, { includePayment = it })
            Toggle("Include delivery address", includeAddress, { includeAddress = it })
            SettingsSection("Sales")
            Toggle("Confirm product taps", confirm, { vm.setConfirmTaps(it) })
            SettingsSection("Profile")
            Text("Tap your profile picture in the Dashboard sidebar to change your business name and logo.")
            SettingsSection("Data safety")
            Text("Order and day deletions require confirmation and cannot be undone.")
            BackupControls()
        }
        Button(onClick = {
            prefs.edit().putInt("low_stock_threshold", lowStock.toIntOrNull()?.coerceIn(0, 9999) ?: 5)
                .putString("report_heading", heading).putBoolean("report_notes", includeNotes)
                .putBoolean("report_cancelled", includeCancelled).putBoolean("report_payment", includePayment)
                .putBoolean("report_address", includeAddress).apply()
            onBack()
        }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Save settings") }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
 Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}

@Composable
private fun SettingsSection(name: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Text(name.uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}
