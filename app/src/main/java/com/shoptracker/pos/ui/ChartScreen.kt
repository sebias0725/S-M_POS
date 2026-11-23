package com.shoptracker.pos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.util.Fmt
import java.time.LocalDate

private enum class RangeOption(val label: String) {
    TODAY("Today"),
    WEEK("Last Week"),
    MONTH("Last Month"),
    CUSTOM("Custom")
}

@Composable
fun ChartScreen(vm: MainViewModel, onBack: () -> Unit) {
    val days by vm.allDays.collectAsState() // newest first

    var range by remember { mutableStateOf(RangeOption.TODAY) }
    var customStart by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val start: LocalDate
    val end: LocalDate
    when (range) {
        RangeOption.TODAY -> {
            start = today
            end = today
        }
        RangeOption.WEEK -> {
            start = today.minusDays(6)
            end = today
        }
        RangeOption.MONTH -> {
            start = today.minusDays(29)
            end = today
        }
        RangeOption.CUSTOM -> {
            start = customStart ?: today
            end = customEnd ?: today
        }
    }

    val selDays = remember(days, start, end) {
        days.filter {
            val d = Fmt.localDate(it.openedAt)
            !d.isBefore(start) && !d.isAfter(end)
        }
    }
    val data = rememberDayData(vm, selDays.map { it.id }.toSet())

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sales charts", onBack)

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RangeOption.values().forEach { opt ->
                FilterChip(
                    selected = range == opt,
                    onClick = {
                        range = opt
                        if (opt == RangeOption.CUSTOM) showPicker = true
                    },
                    label = { Text(opt.label) }
                )
            }
        }

        Text(
            (if (start == end) Fmt.localDateText(start) else Fmt.localDateText(start) + " – " + Fmt.localDateText(end)) +
                "  •  ${selDays.size} business day" + (if (selDays.size == 1) "" else "s"),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )

        if (selDays.isEmpty()) {
            EmptyState(
                "No business days in this range",
                if (range == RangeOption.TODAY) "No business day was started today. Pick another range above."
                else "Pick another range above."
            )
        } else {
            ChartContent(selDays, data)
        }
    }

    if (showPicker) {
        RangePickerDialog(
            initialStart = customStart,
            initialEnd = customEnd,
            onDismiss = { showPicker = false },
            onConfirm = { s, e ->
                customStart = s
                customEnd = e
                range = RangeOption.CUSTOM
                showPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.let { Fmt.dateToPickerMillis(it) },
        initialSelectedEndDateMillis = initialEnd?.let { Fmt.dateToPickerMillis(it) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null) {
                        onConfirm(Fmt.pickerMillisToDate(s), Fmt.pickerMillisToDate(e ?: s))
                    }
                }
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}

@Composable
private fun ChartContent(selDays: List<BusinessDay>, data: com.shoptracker.pos.data.DayData) {
    val stats = data.stats
    val multiDay = selDays.size > 1
    val targetTotal = remember(selDays) { selDays.sumOf { it.targetUnits } }

    // Cumulative sales over time for a single day (product sales + delivery fees),
    // or total sales per day when the range covers several days.
    val points = remember(data, selDays) {
        if (multiDay) {
            val tx = data.txns.groupBy { it.businessDayId }
            val od = data.orders.groupBy { it.businessDayId }
            selDays.sortedBy { it.openedAt }.map { d ->
                d.openedAt to computeStats(tx[d.id] ?: emptyList(), od[d.id] ?: emptyList()).gross
            }
        } else {
            val events = mutableListOf<Pair<Long, Long>>()
            data.txns.filter { it.status == STATUS_COMPLETED }.forEach { events.add(it.timestamp to it.totalCentavos) }
            data.orders.filter { it.status == STATUS_COMPLETED && it.deliveryFeeCentavos > 0 }
                .forEach { events.add(it.createdAt to it.deliveryFeeCentavos) }
            events.sortBy { it.first }
            var cum = 0L
            events.map {
                cum += it.second
                it.first to cum
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Sales summary") {
            LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
            LabelValue("Product sales", Fmt.peso(stats.productSales))
            LabelValue("Delivery fees", Fmt.peso(stats.deliveryFees))
            LabelValue("Total units sold", stats.units.toString(), bold = true)
            LabelValue("Orders (pickup / delivery)", stats.orderCount.toString())
            LabelValue("Average selling price", if (stats.units == 0) "-" else Fmt.peso(stats.averagePrice))
            LabelValue("Profit from products", Fmt.peso(stats.profit))
            LabelValue("Expenses", Fmt.peso(data.totalExpenses))
            LabelValue("Net (profit + delivery fees - expenses)", Fmt.signedPeso(data.net), bold = true)
            if (targetTotal > 0) {
                val pct = Fmt.targetPct(stats.units, targetTotal)
                LabelValue(
                    "Target progress",
                    "${stats.units} / $targetTotal" + (if (pct != null) " (${pct.toPlainString()}%)" else "")
                )
                LinearProgressIndicator(
                    progress = { (stats.units.toFloat() / targetTotal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LabelValue("Target progress", "No target set")
            }
        }

        if (points.isEmpty() || (stats.units == 0 && stats.deliveryFees == 0L)) {
            EmptyState("No sales recorded yet", "Charts will appear after the first sale in this range.")
        } else {
            SectionCard(if (multiDay) "Total sales per day" else "Sales over time (cumulative)") {
                LineChart(points, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (multiDay) Fmt.dayMonth(points.first().first) else Fmt.time(points.first().first),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        (if (multiDay) "Peak " else "Total ") + Fmt.peso(points.maxOf { it.second }),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (multiDay) Fmt.dayMonth(points.last().first) else Fmt.time(points.last().first),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (stats.products.isNotEmpty()) {
                SectionCard("Units sold per product") {
                    HBars(
                        stats.products.map { BarRow(it.name, it.units.toLong(), it.units.toString()) },
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
            SectionCard("Revenue (products + delivery fees)") {
                val rows = stats.products.sortedByDescending { it.revenue }
                    .map { BarRow(it.name, it.revenue, Fmt.peso(it.revenue)) }
                    .toMutableList()
                if (stats.deliveryFees > 0) {
                    rows.add(BarRow("Delivery fees", stats.deliveryFees, Fmt.peso(stats.deliveryFees)))
                }
                HBars(rows.sortedByDescending { it.value }, MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun LineChart(points: List<Pair<Long, Long>>, lineColor: Color, axisColor: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        drawLine(axisColor, Offset(pad, pad), Offset(pad, pad + h), strokeWidth = 2f)
        drawLine(axisColor, Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 2f)
        if (points.isEmpty()) return@Canvas

        val minT = points.first().first
        val spanT = (points.last().first - minT).coerceAtLeast(1L)
        val maxV = points.maxOf { it.second }.coerceAtLeast(1L)

        fun px(t: Long): Float =
            if (points.size == 1) pad + w / 2f else pad + w * ((t - minT).toFloat() / spanT.toFloat())

        fun py(v: Long): Float = pad + h - h * (v.toFloat() / maxV.toFloat())

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = px(p.first)
            val y = py(p.second)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (points.size > 1) {
            drawPath(
                path,
                lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (points.size <= 60) {
            points.forEach { p -> drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(px(p.first), py(p.second))) }
        }
    }
}
