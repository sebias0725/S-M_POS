package com.shoptracker.pos.ui

import com.shoptracker.pos.data.DayData
import com.shoptracker.pos.data.STATUS_COMPLETED

/** Paid order totals plus immediate counter sales. Unpaid orders are excluded from received funds. */
data class PaymentSummary(val cash: Long, val qr: Long, val outstanding: Long) {
    val received: Long get() = cash + qr
}

fun paymentSummary(data: DayData): PaymentSummary {
    val lines = data.txns.filter { it.status == STATUS_COMPLETED }
    val orders = data.orders.filter { it.status == STATUS_COMPLETED }
    val byOrder = lines.filter { it.orderId != null }.groupBy { it.orderId }
    val walkInCash = lines.filter { it.orderId == null }.sumOf { it.totalCentavos }
    var cash = walkInCash
    var qr = 0L
    var unpaid = 0L
    for (order in orders) {
        val total = (byOrder[order.id] ?: emptyList()).sumOf { it.totalCentavos } + order.deliveryFeeCentavos
        if (!order.paid) unpaid += total
        else if (order.paymentMethod.equals("QR", ignoreCase = true)) qr += total
        else cash += total
    }
    return PaymentSummary(cash, qr, unpaid)
}
