package com.shoptracker.pos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.DayStats
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.util.Fmt

private data class PendingAction(val product: Product, val add: Boolean)

@Composable
fun MainScreen(
    vm: MainViewModel,
    onProducts: () -> Unit,
    onOrders: () -> Unit,
    onExpenses: () -> Unit,
    onRates: () -> Unit,
    onHistory: () -> Unit,
    onNewOrder: () -> Unit,
    onNewDay: () -> Unit,
    onChart: () -> Unit,
    onCompare: () -> Unit,
    onOpenDay: (Long) -> Unit
) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val day by vm.openDay.collectAsState()
    val allDays by vm.allDays.collectAsState()
    val confirmTaps by vm.confirmTaps.collectAsState()
    val data = rememberDayData(vm, setOfNotNull(day?.id))
    val stats = data.stats
    val active = remember(products) { products.filter { it.active } }
    val productGroups = remember(active, categories) { groupedProducts(active, categories) }
    val lastSale = remember(data) { data.txns.lastOrNull { it.status == STATUS_COMPLETED } }
    val haptic = LocalHapticFeedback.current

    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var pendingScope by remember { mutableStateOf<ExportScope?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        val scope = pendingScope
        pendingScope = null
        if (uri != null && scope != null) vm.export(uri, scope)
    }

    fun request(p: Product, add: Boolean) {
        if (confirmTaps) {
            pending = PendingAction(p, add)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (add) vm.sell(p) else vm.removeOne(p)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopSummary(
            day = day,
            stats = stats,
            lastSale = lastSale,
            confirmTaps = confirmTaps,
            onConfirmChange = { vm.setConfirmTaps(it) },
            onProducts = onProducts,
            onOrders = onOrders,
            onExpenses = onExpenses,
            onRates = onRates,
            onHistory = onHistory,
            onOpenSummary = { day?.let { onOpenDay(it.id) } }
        )

        Box(Modifier.weight(1f)) {
            when {
                products.isEmpty() -> EmptyState(
                    "No products yet",
                    "Tap \"Products\" at the top to add the items you sell.",
                    Modifier.align(Alignment.Center)
                )
                active.isEmpty() -> EmptyState(
                    "No active products",
                    "All your products are disabled. Enable one in \"Products\".",
                    Modifier.align(Alignment.Center)
                )
                else -> {
                    val byId = remember(stats) { stats.products.associateBy { it.productId } }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        productGroups.forEach { (categoryName, categoryProducts) ->
                            item(key = "category:$categoryName", span = { GridItemSpan(maxLineSpan) }) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = categoryName.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                            items(categoryProducts, key = { it.id }) { p ->
                                val s = byId[p.id]
                                ProductCard(
                                    product = p,
                                    units = s?.units ?: 0,
                                    revenue = s?.revenue ?: 0L,
                                    enabled = day != null,
                                    onAdd = { request(p, true) },
                                    onRemove = { request(p, false) }
                                )
                            }
                        }
                    }
                }
            }
        }

        BottomBar(
            dayActive = day != null,
            canUndo = day != null && stats.units > 0,
            onUndo = { vm.undo() },
            onNewOrder = onNewOrder,
            onNewDay = onNewDay,
            onChart = onChart,
            onCompare = onCompare,
            onExport = { showExport = true }
        )
    }

    pending?.let { pa ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (pa.add) "Add 1 sale?" else "Remove 1 sale?") },
            text = {
                Text(
                    pa.product.name + "  •  " + Fmt.peso(pa.product.priceCentavos),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pa.add) vm.sell(pa.product) else vm.removeOne(pa.product)
                    },
                    modifier = Modifier.height(52.dp)
                ) { Text(if (pa.add) "ADD" else "REMOVE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    if (showExport) {
        ExportDialog(
            days = allDays,
            onDismiss = { showExport = false },
            onConfirm = { scope, fileName ->
                showExport = false
                pendingScope = scope
                launcher.launch(fileName)
            }
        )
    }
}

@Composable
private fun RowScope.NavText(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopSummary(
    day: BusinessDay?,
    stats: DayStats,
    lastSale: SalesTransaction?,
    confirmTaps: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    onProducts: () -> Unit,
    onOrders: () -> Unit,
    onExpenses: () -> Unit,
    onRates: () -> Unit,
    onHistory: () -> Unit,
    onOpenSummary: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.clickable(enabled = day != null) { onOpenSummary() }) {
                if (day != null) {
                    Text(
                        Fmt.date(day.openedAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                StatusChip(day)
            }



            Row {
                Column(Modifier.weight(1f)) {
                    Text("Total sales", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Fmt.peso(stats.gross),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (stats.deliveryFees > 0) {
                        Text(
                            "incl. ${Fmt.peso(stats.deliveryFees)} delivery fees",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Units sold", style = MaterialTheme.typography.labelMedium)
                    Text(
                        stats.units.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (day != null && day.targetUnits > 0) {
                Spacer(Modifier.height(6.dp))
                val progress = (stats.units.toFloat() / day.targetUnits.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                val pct = Fmt.targetPct(stats.units, day.targetUnits)
                Text(
                    "Target: ${stats.units} / ${day.targetUnits} units" + (if (pct != null) " (${pct.toPlainString()}%)" else ""),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        day == null -> ""
                        lastSale == null -> "No sales recorded yet today"
                        else -> "Last sale: ${lastSale.productName} • ${Fmt.time(lastSale.timestamp)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("Confirm taps", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Switch(checked = confirmTaps, onCheckedChange = onConfirmChange)
            }
        }
    }
}

@Composable
private fun StatusChip(day: BusinessDay?) {
    val open = day != null
    Surface(
        shape = RoundedCornerShape(50),
        color = if (open) positiveColor().copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    ) {
        Text(
            if (open && day != null) "● OPEN since ${Fmt.time(day.openedAt)}" else "● NO ACTIVE DAY",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (open) positiveColor() else negativeColor()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(
    product: Product,
    units: Int,
    revenue: Long,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val stockColor: Color = when {
        product.stock <= 0 -> negativeColor()
        product.stock <= 5 -> Color(0xFFEF6C00)
        else -> Color.Unspecified
    }
    Card(
        onClick = onAdd,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(Fmt.peso(product.priceCentavos), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (product.stock <= 0) "Out of stock (${product.stock})" else "Stock: ${product.stock} left",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = stockColor
                )
            }
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepButton("−", onClick = onRemove, enabled = enabled && units > 0)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            units.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp
                        )
                        Text("sold today", style = MaterialTheme.typography.labelSmall)
                    }
                    StepButton("+", onClick = onAdd, enabled = enabled)
                }
                Text(
                    Fmt.peso(revenue),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    dayActive: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onNewOrder: () -> Unit,
    onNewDay: () -> Unit,
    onChart: () -> Unit,
    onCompare: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val pad = PaddingValues(horizontal = 4.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = pad
            ) { Text("UNDO", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            Button(
                onClick = onNewOrder,
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = pad
            ) { Text("NEW ORDER", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            Button(
                onClick = onNewDay,
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = pad
            ) { Text(if (dayActive) "CLOSE DAY" else "NEW DAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }

    }
}
