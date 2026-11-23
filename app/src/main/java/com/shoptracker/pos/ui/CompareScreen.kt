package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.util.Fmt

@Composable
fun CompareScreen(vm: MainViewModel, onBack: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val days by vm.allDays.collectAsState() // newest first
    val current = open ?: days.firstOrNull()
    val idx = if (current == null) -1 else days.indexOfFirst { it.id == current.id }
    val previous = if (idx >= 0) days.getOrNull(idx + 1) else null

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Compare with yesterday", onBack)
        when {
            current == null -> EmptyState("No business day yet", "Start a business day to compare sales.")
            previous == null -> EmptyState(
                "No previous business day",
                "Comparison becomes available once you have at least two business days."
            )
            else -> CompareContent(vm, current, previous)
        }
    }
}

@Composable
private fun CompareContent(vm: MainViewModel, today: BusinessDay, yesterday: BusinessDay) {
    val todayData = rememberDayData(vm, setOf(today.id))
    val yData = rememberDayData(vm, setOf(yesterday.id))
    val t = todayData.stats
    val y = yData.stats

    val revDiff = t.gross - y.gross
    val unitDiff = (t.units - y.units).toLong()
    val growth = Fmt.growth(t.gross, y.gross)
    val mainColor = deltaColor(revDiff)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = mainColor.copy(alpha = 0.14f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BigLine("TODAY", Fmt.peso(t.gross), Fmt.shortDate(today.openedAt))
                BigLine("YESTERDAY", Fmt.peso(y.gross), Fmt.shortDate(yesterday.openedAt))
                Text(
                    "Difference: ${Fmt.signedPeso(revDiff)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = mainColor
                )
                Text(
                    when {
                        growth != null -> "Growth: ${Fmt.signedPct(growth)}"
                        t.gross == 0L -> "Growth: 0.00%"
                        else -> "Growth: n/a (no sales on the previous day)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = mainColor
                )
                Text(
                    "Total sales include delivery fees.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        SectionCard("Units sold") {
            LabelValue("Today", t.units.toString(), bold = true)
            LabelValue("Yesterday", y.units.toString(), bold = true)
            LabelValue(
                "Difference",
                (if (unitDiff > 0) "+" else "") + unitDiff,
                valueColor = deltaColor(unitDiff),
                bold = true
            )
        }

        SectionCard("Delivery fees") {
            LabelValue("Today", Fmt.peso(t.deliveryFees))
            LabelValue("Yesterday", Fmt.peso(y.deliveryFees))
            LabelValue(
                "Difference",
                Fmt.signedPeso(t.deliveryFees - y.deliveryFees),
                valueColor = deltaColor(t.deliveryFees - y.deliveryFees),
                bold = true
            )
        }

        SectionCard("Expenses") {
            LabelValue("Today", Fmt.peso(todayData.totalExpenses))
            LabelValue("Yesterday", Fmt.peso(yData.totalExpenses))
            LabelValue("Net today", Fmt.signedPeso(todayData.net), bold = true)
            LabelValue("Net yesterday", Fmt.signedPeso(yData.net), bold = true)
        }

        SectionCard("Product by product") {
            val ids = (t.products.map { it.productId } + y.products.map { it.productId }).distinct()
            if (ids.isEmpty()) {
                Text("No sales on either day.", style = MaterialTheme.typography.bodyMedium)
            }
            ids.forEach { id ->
                val a = t.products.firstOrNull { it.productId == id }
                val b = y.products.firstOrNull { it.productId == id }
                val name = a?.name ?: b?.name ?: "?"
                val au = a?.units ?: 0
                val bu = b?.units ?: 0
                val ar = a?.revenue ?: 0L
                val br = b?.revenue ?: 0L
                val du = (au - bu).toLong()
                val dr = ar - br
                Column(Modifier.fillMaxWidth()) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today $au  •  Yesterday $bu", style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (du > 0) "+" else "") + du + " units  " + Fmt.signedPeso(dr),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor(if (dr != 0L) dr else du)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigLine(label: String, value: String, date: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.labelSmall)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
