package com.shoptracker.pos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.ExportScope

@Composable
fun ExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val days by vm.allDays.collectAsState()
    var scope by remember { mutableStateOf<ExportScope?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val selected = scope
        scope = null
        if (uri != null && selected != null) vm.export(uri, selected)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Export", onBack)
        ExportDialog(days = days, onDismiss = onBack, onConfirm = { selected, filename ->
            scope = selected
            launcher.launch(filename)
        })
    }
}
