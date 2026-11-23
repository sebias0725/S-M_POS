package com.shoptracker.pos.data

data class ProductStat(
    val productId: Long,
    val name: String,
    val units: Int,
    val revenue: Long,
    val profit: Long
)

data class DayStats(
    val units: Int,
    /** Revenue from products only. */
    val productSales: Long,
    /** Delivery fees of non-cancelled orders. Delivery fees count as sales. */
    val deliveryFees: Long,
    /** Profit from products (profit per unit x units). */
    val profit: Long,
    val orderCount: Int,
    val products: List<ProductStat>
) {
    /** Total sales = product sales + delivery fees. */
    val gross: Long get() = productSales + deliveryFees

    /** Average selling price per unit (product sales only) in centavos, rounded half up. */
    val averagePrice: Long
        get() = if (units == 0) 0L else (productSales + units / 2) / units
}

/** Only COMPLETED transactions and orders count; UNDONE / CANCELLED ones are ignored. */
fun computeStats(txns: List<SalesTransaction>, orders: List<SalesOrder> = emptyList()): DayStats {
    val done = txns.filter { it.status == STATUS_COMPLETED }
    val products = done.groupBy { it.productId }.map { (id, list) ->
        ProductStat(
            productId = id,
            name = list.last().productName,
            units = list.sumOf { it.quantity },
            revenue = list.sumOf { it.totalCentavos },
            profit = list.sumOf { it.unitProfitCentavos * it.quantity }
        )
    }.sortedWith(compareByDescending<ProductStat> { it.units }.thenBy { it.name.lowercase() })
    val doneOrders = orders.filter { it.status == STATUS_COMPLETED }
    return DayStats(
        units = products.sumOf { it.units },
        productSales = products.sumOf { it.revenue },
        deliveryFees = doneOrders.sumOf { it.deliveryFeeCentavos },
        profit = products.sumOf { it.profit },
        orderCount = doneOrders.size,
        products = products
    )
}

/** Everything recorded for one or more business days. */
data class DayData(
    val txns: List<SalesTransaction>,
    val orders: List<SalesOrder>,
    val expenses: List<Expense>
) {
    val stats: DayStats by lazy { computeStats(txns, orders) }
    val totalExpenses: Long get() = expenses.sumOf { it.amountCentavos }

    /** Product profit + delivery fees - expenses. */
    val net: Long get() = stats.profit + stats.deliveryFees - totalExpenses
}
