package com.shoptracker.pos

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shoptracker.pos.data.AppDatabase
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.CATEGORY_NAME_MAX
import com.shoptracker.pos.data.Category
import com.shoptracker.pos.data.CategoryResult
import com.shoptracker.pos.data.CategoryTarget
import com.shoptracker.pos.data.cleanCategoryName
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.data.finalFloatFrom
import com.shoptracker.pos.data.DeliveryRate
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.Waste
import com.shoptracker.pos.data.Expense
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.data.ProductTotal
import com.shoptracker.pos.data.STATUS_CANCELLED
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.export.XlsxExporter
import com.shoptracker.pos.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val productDao = db.productDao()
    private val dayDao = db.businessDayDao()
    private val txDao = db.transactionDao()
    private val orderDao = db.orderDao()
    private val expenseDao = db.expenseDao()
    private val rateDao = db.rateDao()
    private val categoryDao = db.categoryDao()
    private val wasteDao = db.wasteDao()
    private val customerDao = db.customerDao()
    val customers: StateFlow<List<com.shoptracker.pos.data.Customer>> =
        customerDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveCustomer(customer: com.shoptracker.pos.data.Customer) {
        viewModelScope.launch {
            try {
                if (customer.name.isBlank()) { say("Enter customer name"); return@launch }
                if (customer.id == 0L) customerDao.insert(customer.copy(name = customer.name.trim()))
                else customerDao.update(customer.copy(name = customer.name.trim()))
                say("Customer saved")
            } catch (e: Exception) { say("Could not save customer: ${e.message}") }
        }
    }

    fun deleteCustomer(id: Long) { viewModelScope.launch {
        customerDao.delete(id); say("Customer removed; historical orders retained")
    } }


    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _confirmTaps = MutableStateFlow(prefs.getBoolean("confirm_taps", true))

    /** When true (default) every product tap / + / - asks for confirmation first. */
    val confirmTaps: StateFlow<Boolean> = _confirmTaps.asStateFlow()

    fun setConfirmTaps(value: Boolean) {
        _confirmTaps.value = value
        prefs.edit().putBoolean("confirm_taps", value).apply()
    }

    val products: StateFlow<List<Product>> =
        productDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Alphabetical (case-insensitive). */
    val categories: StateFlow<List<Category>> =
        categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openDay: StateFlow<BusinessDay?> =
        dayDao.observeOpen().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Newest first. */
    val allDays: StateFlow<List<BusinessDay>> =
        dayDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lifetime: StateFlow<Map<Long, ProductTotal>> =
        txDao.observeLifetime().map { list -> list.associateBy { it.productId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allTxns: StateFlow<List<SalesTransaction>> =
        txDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allOrders: StateFlow<List<SalesOrder>> =
        orderDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allExpenses: StateFlow<List<Expense>> =
        expenseDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wastes: StateFlow<List<Waste>> =
        wasteDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun recordWaste(productId: Long, quantity: Int, reason: String, note: String) {
        viewModelScope.launch {
            try {
                val saved = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    val product = productDao.getById(productId) ?: return@withTransaction false
                    if (quantity <= 0 || quantity > product.stock) return@withTransaction false
                    wasteDao.insert(Waste(businessDayId = day.id, productId = product.id,
                        productName = product.name, quantity = quantity,
                        unitValueCentavos = product.priceCentavos, reason = reason,
                        note = note.trim(), createdAt = System.currentTimeMillis()))
                    productDao.adjustStock(product.id, -quantity)
                    true
                }
                say(if (saved) "Waste recorded" else "Check active day, product and available stock")
            } catch (e: Exception) { say("Could not record waste: ${e.message}") }
        }
    }

    fun removeWaste(id: Long) {
        viewModelScope.launch {
            try {
                val ok = db.withTransaction {
                    val waste = wasteDao.getById(id) ?: return@withTransaction false
                    if (dayDao.getById(waste.businessDayId)?.isOpen != true) return@withTransaction false
                    productDao.adjustStock(waste.productId, waste.quantity)
                    wasteDao.deleteById(id)
                    true
                }
                say(if (ok) "Waste removed and stock restored" else "Only current-day wastes can be removed")
            } catch (e: Exception) { say("Could not remove waste: ${e.message}") }
        }
    }

    val rates: StateFlow<List<DeliveryRate>> =
        rateDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    private fun say(msg: String) {
        _events.tryEmit(msg)
    }

    // ---------------- sales ----------------

    /** Records one sale of [product] under the open business day, atomically. Reduces stock by 1. */
    fun sell(product: Product) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    val p = productDao.getById(product.id) ?: return@withTransaction false
                    if (!p.active || p.stock < 1) return@withTransaction false
                    txDao.insert(
                        SalesTransaction(
                            businessDayId = day.id,
                            productId = p.id,
                            productName = p.name,
                            unitPriceCentavos = p.priceCentavos,
                            quantity = 1,
                            totalCentavos = p.priceCentavos,
                            timestamp = System.currentTimeMillis(),
                            unitProfitCentavos = p.profitCentavos
                        )
                    )
                    productDao.adjustStock(p.id, -1)
                    true
                }
                if (!ok) say("Sale not recorded: no active day, disabled product, or out of stock")
            } catch (e: Exception) {
                say("Could not save sale: ${e.message}")
            }
        }
    }

    /**
     * The "-" button: removes the latest single sale of this product from the open day
     * (the record is kept as UNDONE) and puts the unit back in stock.
     */
    fun removeOne(product: Product) {
        viewModelScope.launch {
            try {
                val removed: SalesTransaction? = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction null
                    val last = txDao.getLastSingleSale(day.id, product.id) ?: return@withTransaction null
                    val changed = txDao.markUndone(last.id, System.currentTimeMillis())
                    if (changed == 1) {
                        productDao.adjustStock(last.productId, last.quantity)
                        last
                    } else {
                        null
                    }
                }
                if (removed == null) {
                    say("No single sale of ${product.name} to remove (cancel an order from Orders instead)")
                } else {
                    say("Removed 1 × ${removed.productName}")
                }
            } catch (e: Exception) {
                say("Could not remove sale: ${e.message}")
            }
        }
    }

    /**
     * Undoes the most recent completed sale of the open business day. Can be repeated.
     * If that sale is part of an order, the whole order is cancelled so orders never end up half-undone.
     */
    fun undo() {
        viewModelScope.launch {
            try {
                val message: String? = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction null
                    val last = txDao.getLastCompleted(day.id) ?: return@withTransaction null
                    val orderId = last.orderId
                    if (orderId != null) {
                        cancelOrderInternal(orderId)
                        "Undid order #$orderId"
                    } else {
                        val changed = txDao.markUndone(last.id, System.currentTimeMillis())
                        if (changed == 1) {
                            productDao.adjustStock(last.productId, last.quantity)
                            "Undid: ${last.productName} (${Fmt.peso(last.totalCentavos)})"
                        } else {
                            null
                        }
                    }
                }
                say(message ?: "Nothing to undo")
            } catch (e: Exception) {
                say("Undo failed: ${e.message}")
            }
        }
    }

    // ---------------- orders ----------------

    /** Records a bulk / pickup / delivery order as sales of the open business day, atomically. */
    fun createOrder(
        customer: String,
        type: String,
        address: String,
        deliveryFeeCentavos: Long,
        note: String,
        items: List<Pair<Long, Int>>,
        paymentMethod: String,
        deliveryTime: String,
        paymentPaid: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    val lines = items.filter { it.second > 0 }
                    if (lines.isEmpty()) return@withTransaction false
                    // Check all quantities before inserting any order or transaction.
                    val requested = lines.groupBy { it.first }.mapValues { entry -> entry.value.sumOf { it.second } }
                    for ((productId, quantity) in requested) {
                        val product = productDao.getById(productId) ?: return@withTransaction false
                        if (!product.active || quantity > product.stock) return@withTransaction false
                    }
                    val now = System.currentTimeMillis()
                    val orderId = orderDao.insert(
                        SalesOrder(
                            businessDayId = day.id,
                            customerName = customer.trim(),
                            type = type,
                            address = address.trim(),
                            deliveryFeeCentavos = if (type == ORDER_DELIVERY) deliveryFeeCentavos else 0L,
                            note = note.trim(),
                            createdAt = now,
                            paymentMethod = paymentMethod,
                            deliveryTime = deliveryTime,
                            paid = paymentPaid
                        )
                    )
                    var inserted = 0
                    for ((productId, qty) in lines) {
                        val p = productDao.getById(productId) ?: continue
                        txDao.insert(
                            SalesTransaction(
                                businessDayId = day.id,
                                productId = p.id,
                                productName = p.name,
                                unitPriceCentavos = p.priceCentavos,
                                quantity = qty,
                                totalCentavos = p.priceCentavos * qty,
                                timestamp = now,
                                unitProfitCentavos = p.profitCentavos,
                                orderId = orderId
                            )
                        )
                        productDao.adjustStock(p.id, -qty)
                        inserted++
                    }
                    if (inserted == 0) throw IllegalStateException("None of the products exist any more")
                    true
                }
                say(if (ok) "Order saved" else "Order not saved: check active day and available stock")
            } catch (e: Exception) {
                say("Could not save order: ${e.message}")
            }
            onResult(ok)
        }
    }

    /** Atomically replace all editable fields and sale lines; restore old stock before checking new quantities. */
    fun editOrder(orderId: Long, updated: SalesOrder, items: Map<Long, Pair<Int, Long>>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                db.withTransaction {
                    val old = orderDao.getById(orderId) ?: return@withTransaction false
                    val day = dayDao.getById(old.businessDayId) ?: return@withTransaction false
                    if (old.status != STATUS_COMPLETED || items.isEmpty()) return@withTransaction false
                    if (updated.deliveryFeeCentavos < 0 || items.values.any { it.first <= 0 || it.second < 0 }) return@withTransaction false
                    val oldLines = txDao.getByOrder(orderId).filter { it.status == STATUS_COMPLETED }
                    val restore = oldLines.groupBy { it.productId }.mapValues { e -> e.value.sumOf { it.quantity } }
                    for ((id, item) in items) {
                        val product = productDao.getById(id) ?: return@withTransaction false
                        if (!product.active || item.first > product.stock + (restore[id] ?: 0)) return@withTransaction false
                    }
                    oldLines.forEach { if (productDao.adjustStock(it.productId, it.quantity) == 0) error("Could not restore stock") }
                    txDao.deleteByOrder(orderId)
                    val now = System.currentTimeMillis()
                    items.forEach { (id, item) ->
                        val product = productDao.getById(id) ?: error("Missing product")
                        if (productDao.adjustStock(id, -item.first) == 0) error("Insufficient stock")
                        txDao.insert(SalesTransaction(businessDayId = old.businessDayId, productId = id,
                            productName = product.name, unitPriceCentavos = item.second, quantity = item.first,
                            totalCentavos = item.second * item.first, timestamp = old.createdAt,
                            unitProfitCentavos = product.profitCentavos, orderId = orderId))
                    }
                    orderDao.update(updated.copy(id = old.id, businessDayId = old.businessDayId,
                        createdAt = old.createdAt, status = old.status, cancelledAt = old.cancelledAt))
                    recalculateClosedDay(old.businessDayId)
                    true
                }
            } catch (e: Exception) { say("Edit failed: ${e.message}"); false }
            say(if (ok) "Order updated" else "Order not updated: check stock and active business day")
            onResult(ok)
        }
    }

    /** Permanent deletion of an order. Never restore stock for a previously cancelled order. */
    fun deleteOrder(orderId: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = try {
                db.withTransaction {
                    val order = orderDao.getById(orderId) ?: return@withTransaction false
                    if (dayDao.getById(order.businessDayId) == null) return@withTransaction false
                    if (order.status == STATUS_COMPLETED) {
                        txDao.getByOrder(orderId).filter { it.status == STATUS_COMPLETED }.forEach {
                            if (productDao.adjustStock(it.productId, it.quantity) == 0) error("Stock restore failed")
                        }
                    }
                    txDao.deleteByOrder(orderId)
                    orderDao.deleteById(orderId)
                    recalculateClosedDay(order.businessDayId)
                    true
                }
            } catch (e: Exception) { say("Delete failed: ${e.message}"); false }
            say(if (ok) "Order permanently deleted" else "Could not delete order")
            onResult(ok)
        }
    }

    /** Remove a closed test day and all its records in one database transaction. */
    fun deleteBusinessDay(dayId: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = try {
                db.withTransaction {
                    val day = dayDao.getById(dayId) ?: return@withTransaction false
                    if (day.isOpen) return@withTransaction false
                    txDao.getForDay(dayId).filter { it.status == STATUS_COMPLETED }.forEach {
                        if (productDao.adjustStock(it.productId, it.quantity) == 0) error("Stock restore failed")
                    }
                    // Wastes also deducted inventory; restore only this day's wastes.
                    wasteDao.getAllForDay(dayId).forEach {
                        if (productDao.adjustStock(it.productId, it.quantity) == 0) error("Waste restore failed")
                    }
                    txDao.deleteByDay(dayId)
                    orderDao.deleteByDay(dayId)
                    expenseDao.deleteByDay(dayId)
                    wasteDao.deleteByDay(dayId)
                    dayDao.deleteById(dayId)
                    true
                }
            } catch (e: Exception) { say("Day deletion failed: ${e.message}"); false }
            say(if (ok) "Business day permanently deleted" else "Could not delete day; close it first")
            onResult(ok)
        }
    }

    /** Edits the preferred delivery time of an existing delivery order (allowed at any time). */
    fun updateDeliveryTime(orderId: Long, time: String) {
        viewModelScope.launch {
            try {
                val order = orderDao.getById(orderId)
                if (order == null || order.type != ORDER_DELIVERY) {
                    say("Only delivery orders have a delivery time")
                } else {
                    orderDao.setDeliveryTime(orderId, time)
                    say("Order #$orderId delivery time: ${time.ifBlank { "not recorded" }}")
                }
            } catch (e: Exception) {
                say("Could not update delivery time: ${e.message}")
            }
        }
    }

    fun setOrderPaid(orderId: Long, paid: Boolean) {
        viewModelScope.launch {
            try {
                val updated = db.withTransaction {
                    val order = orderDao.getById(orderId) ?: return@withTransaction false
                    if (orderDao.setPaid(orderId, paid) == 0) return@withTransaction false
                    recalculateClosedDay(order.businessDayId)
                    true
                }
                say(if (!updated) "Cannot update cancelled order" else if (paid) "Marked paid" else "Marked unpaid")
            } catch (e: Exception) { say("Payment update failed: ${e.message}") }
        }
    }

    fun setOrderDelivered(orderId: Long, delivered: Boolean) {
        viewModelScope.launch {
            try {
                if (orderDao.setDelivered(orderId, delivered) == 0) say("Cannot update cancelled order")
                else say(if (delivered) "Marked delivered" else "Marked not delivered")
            } catch (e: Exception) { say("Delivery update failed: ${e.message}") }
        }
    }

    fun cancelOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val order = orderDao.getById(orderId) ?: return@withTransaction false
                    val day = dayDao.getById(order.businessDayId)
                    if (day == null) return@withTransaction false
                    cancelOrderInternal(orderId)
                    recalculateClosedDay(order.businessDayId)
                    true
                }
                say(if (ok) "Order #$orderId cancelled" else "Could not cancel order")
            } catch (e: Exception) {
                say("Could not cancel order: ${e.message}")
            }
        }
    }

    /** Must be called inside a transaction. Restores stock and marks the order and its lines. */
    private suspend fun cancelOrderInternal(orderId: Long) {
        val order = orderDao.getById(orderId) ?: return
        if (order.status != STATUS_COMPLETED) return
        val now = System.currentTimeMillis()
        for (line in txDao.getByOrder(orderId)) {
            if (line.status == STATUS_COMPLETED) productDao.adjustStock(line.productId, line.quantity)
        }
        txDao.markUndoneByOrder(orderId, now)
        orderDao.update(order.copy(status = STATUS_CANCELLED, cancelledAt = now))
    }

    // ---------------- expenses ----------------

    fun addExpense(description: String, amountCentavos: Long) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    expenseDao.insert(
                        Expense(
                            businessDayId = day.id,
                            description = description.trim(),
                            amountCentavos = amountCentavos,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    true
                }
                say(if (ok) "Expense added" else "Start a business day before adding expenses")
            } catch (e: Exception) {
                say("Could not add expense: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getById(expense.businessDayId)
                    if (day == null || day.closedAt != null) return@withTransaction false
                    expenseDao.delete(expense)
                    true
                }
                say(if (ok) "Expense deleted" else "Expenses of closed days cannot be deleted")
            } catch (e: Exception) {
                say("Could not delete expense: ${e.message}")
            }
        }
    }

    // ---------------- delivery fee reference ----------------

    fun addRate(name: String, feeCentavos: Long, note: String) {
        viewModelScope.launch {
            try {
                rateDao.insert(DeliveryRate(name = name.trim(), feeCentavos = feeCentavos, note = note.trim()))
            } catch (e: Exception) {
                say("Could not add rate: ${e.message}")
            }
        }
    }

    fun updateRate(rate: DeliveryRate) {
        viewModelScope.launch {
            try {
                rateDao.update(rate)
                say("Delivery fee updated")
            } catch (e: Exception) {
                say("Could not update rate: ${e.message}")
            }
        }
    }

    fun deleteRate(rate: DeliveryRate) {
        viewModelScope.launch {
            try {
                rateDao.delete(rate)
            } catch (e: Exception) {
                say("Could not delete rate: ${e.message}")
            }
        }
    }

    // ---------------- business days ----------------

    fun startDay(
        openingFloat: Long,
        openingCash: Long,
        targetUnits: Int,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    if (dayDao.getOpen() != null) {
                        false // a day is already open: never create a second one
                    } else {
                        dayDao.insert(
                            BusinessDay(
                                openedAt = System.currentTimeMillis(),
                                openingFloatCentavos = openingFloat,
                                openingCashCentavos = openingCash,
                                targetUnits = targetUnits
                            )
                        )
                        true
                    }
                }
                say(if (ok) "New business day started" else "A business day is already open")
            } catch (e: Exception) {
                say("Could not start new day: ${e.message}")
            }
            onResult(ok)
        }
    }

    /** Closes the day. Final floating money is derived: Cash on Hand - Remit. */
    fun closeDay(dayId: Long, finalCash: Long, remit: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    val day = dayDao.getById(dayId)
                    if (day == null || day.closedAt != null) {
                        false
                    } else {
                        dayDao.update(
                            day.copy(
                                closedAt = System.currentTimeMillis(),
                                finalFloatCentavos = finalFloatFrom(finalCash, remit),
                                finalCashCentavos = finalCash,
                                remitCentavos = remit
                            )
                        )
                        true
                    }
                }
                say(if (ok) "Business day closed" else "That day is already closed")
            } catch (e: Exception) {
                say("Could not close day: ${e.message}")
            }
            onResult(ok)
        }
    }

    suspend fun getWastesForDay(dayId: Long): List<Waste> = wasteDao.getAllForDay(dayId)

    /** Correct a closed day's opening values and business date without resetting its transactions. */
    fun editHistoricalDay(dayId: Long, openedAt: Long, openingFloat: Long, targetUnits: Int, remit: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = try { db.withTransaction {
                val day = dayDao.getById(dayId) ?: return@withTransaction false
                if (day.isOpen || openedAt <= 0 || openingFloat < 0 || targetUnits < 0 || remit < 0) return@withTransaction false
                if (day.closedAt != null && openedAt > day.closedAt) return@withTransaction false
                dayDao.update(day.copy(openedAt = openedAt, openingFloatCentavos = openingFloat,
                    openingCashCentavos = openingFloat, targetUnits = targetUnits, remitCentavos = remit))
                recalculateClosedDay(dayId)
                true
            } } catch (e: Exception) { say("Day edit failed: ${e.message}"); false }
            say(if (ok) "Historical day updated" else "Historical day was not updated")
            onResult(ok)
        }
    }

    /** Recompute money from paid orders, single sales and expenses, never from editable total fields. */
    private suspend fun recalculateClosedDay(dayId: Long) {
        val day = dayDao.getById(dayId) ?: return
        if (day.isOpen) return
        val orders = orderDao.getAll().filter { it.businessDayId == dayId && it.status == STATUS_COMPLETED }
        val paidCashOrders = orders.filter { it.paid && it.paymentMethod.equals("Cash", true) }.map { it.id }.toSet()
        val lines = txDao.getForDay(dayId).filter { it.status == STATUS_COMPLETED }
        val cash = lines.filter { it.orderId == null || it.orderId in paidCashOrders }.sumOf { it.totalCentavos } +
            orders.filter { it.id in paidCashOrders }.sumOf { it.deliveryFeeCentavos }
        val expenses = expenseDao.getAll().filter { it.businessDayId == dayId }.sumOf { it.amountCentavos }
        val finalCash = day.openingFloatCentavos + cash - expenses
        val remit = day.remitCentavos ?: 0L
        dayDao.update(day.copy(finalCashCentavos = finalCash,
            finalFloatCentavos = finalFloatFrom(finalCash, remit)))
    }

    fun editHistoricalExpense(expense: Expense, description: String, amount: Long) {
        viewModelScope.launch { try { db.withTransaction {
            require(description.isNotBlank() && amount >= 0)
            require(dayDao.getById(expense.businessDayId)?.closedAt != null)
            expenseDao.update(expense.copy(description = description.trim(), amountCentavos = amount))
            recalculateClosedDay(expense.businessDayId)
        }; say("Expense corrected") } catch (e: Exception) { say("Expense correction failed: ${e.message}") } }
    }
    fun deleteHistoricalExpense(expense: Expense) {
        viewModelScope.launch { try { db.withTransaction {
            require(dayDao.getById(expense.businessDayId)?.closedAt != null)
            expenseDao.delete(expense); recalculateClosedDay(expense.businessDayId)
        }; say("Expense deleted") } catch (e: Exception) { say("Could not delete expense: ${e.message}") } }
    }
    fun addHistoricalExpense(dayId: Long, description: String, amount: Long) {
        viewModelScope.launch { try { db.withTransaction {
            require(dayDao.getById(dayId)?.closedAt != null && description.isNotBlank() && amount >= 0)
            expenseDao.insert(Expense(businessDayId = dayId, description = description.trim(), amountCentavos = amount, createdAt = System.currentTimeMillis()))
            recalculateClosedDay(dayId)
        }; say("Historical expense added") } catch (e: Exception) { say("Could not add expense: ${e.message}") } }
    }
    fun editHistoricalSale(tx: SalesTransaction, quantity: Int, unitPrice: Long) {
        viewModelScope.launch { try { db.withTransaction {
            require(tx.orderId == null && tx.status == STATUS_COMPLETED && quantity > 0 && unitPrice >= 0)
            require(dayDao.getById(tx.businessDayId)?.closedAt != null)
            val delta = tx.quantity - quantity
            require(productDao.adjustStock(tx.productId, delta) > 0) { "Insufficient inventory" }
            txDao.update(tx.copy(quantity = quantity, unitPriceCentavos = unitPrice, totalCentavos = unitPrice * quantity))
            recalculateClosedDay(tx.businessDayId)
        }; say("Historical sale corrected") } catch (e: Exception) { say("Sale correction failed: ${e.message}") } }
    }
    fun undoHistoricalSale(tx: SalesTransaction) {
        viewModelScope.launch { try { db.withTransaction {
            require(tx.orderId == null && tx.status == STATUS_COMPLETED)
            require(dayDao.getById(tx.businessDayId)?.closedAt != null)
            require(productDao.adjustStock(tx.productId, tx.quantity) > 0)
            txDao.markUndone(tx.id, System.currentTimeMillis())
            recalculateClosedDay(tx.businessDayId)
        }; say("Historical sale reversed") } catch (e: Exception) { say("Could not reverse sale: ${e.message}") } }
    }
    fun editHistoricalWaste(waste: Waste, quantity: Int, reason: String, note: String) {
        viewModelScope.launch { try { db.withTransaction {
            require(quantity > 0 && reason.isNotBlank() && dayDao.getById(waste.businessDayId)?.closedAt != null)
            require(productDao.adjustStock(waste.productId, waste.quantity - quantity) > 0) { "Insufficient stock" }
            wasteDao.update(waste.copy(quantity = quantity, reason = reason.trim(), note = note.trim()))
        }; say("Waste corrected") } catch (e: Exception) { say("Waste correction failed: ${e.message}") } }
    }
    fun deleteHistoricalWaste(waste: Waste) {
        viewModelScope.launch { try { db.withTransaction {
            require(dayDao.getById(waste.businessDayId)?.closedAt != null)
            require(productDao.adjustStock(waste.productId, waste.quantity) > 0)
            wasteDao.deleteById(waste.id)
        }; say("Waste deleted") } catch (e: Exception) { say("Could not delete waste: ${e.message}") } }
    }
    fun recalculateHistoricalDay(dayId: Long) {
        viewModelScope.launch { try { db.withTransaction { recalculateClosedDay(dayId) } }
            catch (e: Exception) { say("Recalculation failed: ${e.message}") } }
    }

    /** Edits finals of a closed day. If the day has a remit, floating money is recomputed from it. */
    fun updateFinals(dayId: Long, finalFloat: Long, finalCash: Long, remit: Long? = null) {
        viewModelScope.launch {
            try {
                db.withTransaction {
                    val day = dayDao.getById(dayId)
                    if (day != null && day.closedAt != null) {
                        dayDao.update(
                            if (day.remitCentavos != null && remit != null) {
                                day.copy(
                                    finalFloatCentavos = finalFloatFrom(finalCash, remit),
                                    finalCashCentavos = finalCash,
                                    remitCentavos = remit
                                )
                            } else {
                                day.copy(finalFloatCentavos = finalFloat, finalCashCentavos = finalCash)
                            }
                        )
                    }
                }
                say("Final amounts updated")
            } catch (e: Exception) {
                say("Could not update: ${e.message}")
            }
        }
    }

    // ---------------- products ----------------

    fun addProduct(name: String, priceCentavos: Long, profitCentavos: Long, stock: Int, categoryId: Long? = null) {
        if (stock < 0) { say("Stock cannot be negative"); return }
        viewModelScope.launch {
            try {
                productDao.insert(
                    Product(
                        name = name.trim(),
                        priceCentavos = priceCentavos,
                        profitCentavos = profitCentavos,
                        stock = stock,
                        categoryId = validCategoryId(categoryId)
                    )
                )
            } catch (e: Exception) {
                say("Could not add product: ${e.message}")
            }
        }
    }

    fun updateProduct(product: Product) {
        if (product.stock < 0) { say("Stock cannot be negative"); return }
        viewModelScope.launch {
            try {
                productDao.update(product.copy(categoryId = validCategoryId(product.categoryId)))
            } catch (e: Exception) {
                say("Could not update product: ${e.message}")
            }
        }
    }

    /** History is safe: transactions keep their own name/price/profit snapshot. */
    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            try {
                productDao.delete(product)
                say("Deleted ${product.name}")
            } catch (e: Exception) {
                say("Could not delete product: ${e.message}")
            }
        }
    }

    // ---------------- categories ----------------

    /** A category id that no longer exists is stored as null (Uncategorized), never as a dangling id. */
    private suspend fun validCategoryId(id: Long?): Long? =
        if (id != null && categoryDao.getById(id) != null) id else null

    /** Returns an error message, or null when [name] is acceptable. Must run inside a transaction. */
    private suspend fun categoryNameError(name: String, ignoreId: Long?): String? {
        if (name.isEmpty()) return "Enter a category name"
        if (name.length > CATEGORY_NAME_MAX) return "Category name is too long (max $CATEGORY_NAME_MAX)"
        if (name.equals("Uncategorized", ignoreCase = true)) return "\"Uncategorized\" is reserved for products with no category"
        val clash = categoryDao.findByName(name)
        if (clash != null && clash.id != ignoreId) return "A category named \"${clash.name}\" already exists"
        return null
    }

    fun addCategory(rawName: String, onResult: (CategoryResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                db.withTransaction {
                    val name = cleanCategoryName(rawName)
                    val err = categoryNameError(name, null)
                    if (err != null) {
                        CategoryResult.Error(err)
                    } else {
                        categoryDao.insert(Category(name = name))
                        CategoryResult.Ok
                    }
                }
            } catch (e: Exception) {
                CategoryResult.Error("Could not add category: ${e.message}")
            }
            onResult(result)
        }
    }

    fun renameCategory(id: Long, rawName: String, onResult: (CategoryResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                db.withTransaction {
                    val existing = categoryDao.getById(id)
                    val name = cleanCategoryName(rawName)
                    if (existing == null) {
                        CategoryResult.Error("That category no longer exists")
                    } else {
                        val err = categoryNameError(name, id)
                        if (err != null) {
                            CategoryResult.Error(err)
                        } else {
                            categoryDao.update(existing.copy(name = name))
                            CategoryResult.Ok
                        }
                    }
                }
            } catch (e: Exception) {
                CategoryResult.Error("Could not rename category: ${e.message}")
            }
            onResult(result)
        }
    }

    /**
     * Deletes a category. If it still contains products, [moveTo] is REQUIRED: the products are
     * moved first and the category is deleted only afterwards, in one transaction (all or nothing).
     * Without a target the delete is refused, so products can never be orphaned by accident.
     * Products are never deleted by this function.
     */
    fun deleteCategory(id: Long, moveTo: CategoryTarget? = null, onResult: (CategoryResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                db.withTransaction {
                    val cat = categoryDao.getById(id)
                    if (cat == null) {
                        CategoryResult.Error("That category no longer exists")
                    } else {
                        val inside = productDao.countInCategory(id)
                        val targetId: Long? = when (moveTo) {
                            null -> null
                            CategoryTarget.Uncategorized -> null
                            is CategoryTarget.To -> moveTo.categoryId
                        }
                        when {
                            inside > 0 && moveTo == null ->
                                CategoryResult.Error(
                                    "\"${cat.name}\" still has $inside product${if (inside == 1) "" else "s"}. " +
                                        "Choose where to move them first."
                                )
                            moveTo is CategoryTarget.To && moveTo.categoryId == id ->
                                CategoryResult.Error("Choose a different category to move the products to")
                            moveTo is CategoryTarget.To && categoryDao.getById(moveTo.categoryId) == null ->
                                CategoryResult.Error("The category to move products to no longer exists")
                            else -> {
                                if (inside > 0) productDao.moveCategory(id, targetId)
                                categoryDao.deleteById(id)
                                CategoryResult.Ok
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                CategoryResult.Error("Could not delete category: ${e.message}")
            }
            if (result is CategoryResult.Ok) say("Category deleted")
            onResult(result)
        }
    }

    // ---------------- export ----------------

    fun export(uri: Uri, scope: ExportScope) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val days: List<BusinessDay> = when (scope) {
                        ExportScope.Today -> listOfNotNull(dayDao.getOpen() ?: dayDao.getLatest())
                        is ExportScope.Day -> listOfNotNull(dayDao.getById(scope.dayId))
                        ExportScope.All -> dayDao.getAll()
                    }
                    if (days.isEmpty()) throw IOException("There are no business days to export")
                    val ids = days.map { it.id }.toSet()
                    val txns = txDao.getAll().filter { it.businessDayId in ids }
                    val orders = orderDao.getAll().filter { it.businessDayId in ids }
                    val expenses = expenseDao.getAll().filter { it.businessDayId in ids }
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("Could not open the chosen file")
                    XlsxExporter.write(out, days, txns, orders, expenses)
                }
                say("Excel file saved")
            } catch (e: Exception) {
                say("Export failed: ${e.message}")
            }
        }
    }
}
