package com.shoptracker.pos.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATUS_COMPLETED = "COMPLETED"
const val STATUS_UNDONE = "UNDONE"
const val STATUS_CANCELLED = "CANCELLED"

const val ORDER_PICKUP = "PICKUP"
const val ORDER_DELIVERY = "DELIVERY"

/** Payment methods offered on an order. Stored as plain text on the order. */
const val PAYMENT_COD = "COD"
val PAYMENT_METHODS: List<String> = listOf("Cash", "QR")

/**
 * Preferred delivery time. "Pending" = the customer has not confirmed a time yet; it is ALWAYS the
 * first option. The chosen slot is stored as text on the order, so changing this list later never
 * alters existing orders. "" (blank) means "not recorded" (orders made before this field existed,
 * and pickup orders).
 */
const val DELIVERY_TIME_PENDING = "Pending"
val DELIVERY_TIME_SLOTS: List<String> = listOf(
    DELIVERY_TIME_PENDING,
    "9:00 AM – 11:00 AM",
    "11:00 AM – 1:00 PM",
    "1:00 PM – 3:00 PM",
    "3:00 PM – 5:00 PM",
    "5:00 PM – 7:00 PM"
)

/** All money is stored as whole centavos (Long) to avoid floating point errors. */
@Entity(tableName = "products", indices = [Index("categoryId")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCentavos: Long,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Profit per unit, in centavos (entered by the user). */
    @ColumnInfo(defaultValue = "0") val profitCentavos: Long = 0,
    /** Remaining stock (units). Reduced by sales, restored by undo/remove/cancel. */
    @ColumnInfo(defaultValue = "0") val stock: Int = 0,
    /**
     * Optional category. NULL means "Uncategorized" (every product that existed before categories
     * were added). Not a hard foreign key on purpose: a dangling id is simply shown as Uncategorized,
     * so a product can never become invalid or disappear because of its category.
     */
    val categoryId: Long? = null
)

/** A user-defined product category (e.g. "Drinks", "Snacks"). */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Where products go when their category is deleted. */
sealed interface CategoryTarget {
    data object Uncategorized : CategoryTarget
    data class To(val categoryId: Long) : CategoryTarget
}

/** Outcome of a category operation, so the UI can show a precise message. */
sealed interface CategoryResult {
    data object Ok : CategoryResult
    data class Error(val message: String) : CategoryResult
}

const val CATEGORY_NAME_MAX = 30

/** Trims and collapses inner whitespace. */
fun cleanCategoryName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

@Entity(tableName = "business_days")
data class BusinessDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingFloatCentavos: Long,
    val openingCashCentavos: Long,
    val targetUnits: Int,
    val finalFloatCentavos: Long? = null,
    val finalCashCentavos: Long? = null,
    /**
     * Cash handed over (remitted) when closing. Stored separately from Expenses. Null for days
     * closed before this field existed (their records are left untouched).
     */
    val remitCentavos: Long? = null
)

val BusinessDay.isOpen: Boolean get() = closedAt == null

/** Final Floating Money = Cash on Hand - Remit. */
fun finalFloatFrom(cashOnHand: Long, remit: Long): Long = cashOnHand - remit

/**
 * A single sale line. productName, unitPriceCentavos and unitProfitCentavos are snapshots taken
 * at the time of sale, so later product edits never change history. Undone sales are kept
 * (status = UNDONE). orderId is set when the line belongs to a New Order.
 */
@Entity(
    tableName = "sales_transactions",
    indices = [Index("businessDayId")]
)
data class SalesTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val productId: Long,
    val productName: String,
    val unitPriceCentavos: Long,
    val quantity: Int = 1,
    val totalCentavos: Long,
    val timestamp: Long,
    val status: String = STATUS_COMPLETED,
    val undoneAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val unitProfitCentavos: Long = 0,
    val orderId: Long? = null
)

/** A pickup or delivery order (bulk or regular). Its items are stored as SalesTransactions. */
@Entity(
    tableName = "orders",
    indices = [Index("businessDayId")]
)
data class SalesOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val customerName: String,
    val type: String,
    val address: String,
    val deliveryFeeCentavos: Long,
    val note: String,
    val createdAt: Long,
    val status: String = STATUS_COMPLETED,
    val cancelledAt: Long? = null,
    /** COD / QR / Bank Transfer. Blank for orders created before this field existed. */
    @ColumnInfo(defaultValue = "''") val paymentMethod: String = "",
    /** Preferred delivery time (see DELIVERY_TIME_SLOTS). Blank = not recorded. Editable later. */
    @ColumnInfo(defaultValue = "''") val deliveryTime: String = "",
    @ColumnInfo(defaultValue = "0") val paid: Boolean = false,
    @ColumnInfo(defaultValue = "0") val delivered: Boolean = false
)

@Entity(
    tableName = "expenses",
    indices = [Index("businessDayId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val description: String,
    val amountCentavos: Long,
    val createdAt: Long
)

/** A Delivery Rate: a location / name and its delivery fee. Orders copy the fee, they do not link here. */
@Entity(tableName = "delivery_rates")
data class DeliveryRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val feeCentavos: Long,
    val note: String = ""
)

@Entity(tableName = "wastes", indices = [Index("businessDayId")])
data class Waste(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitValueCentavos: Long,
    val reason: String,
    val note: String,
    val createdAt: Long
)

data class ProductTotal(
    val productId: Long,
    val units: Int,
    val revenue: Long
)

sealed interface ExportScope {
    data object Today : ExportScope
    data class Day(val dayId: Long) : ExportScope
    data object All : ExportScope
}

/** Saved contact details; orders remain historical snapshots and are linked by name. */
@Entity(tableName = "customers", indices = [Index(value = ["name"], unique = true)])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val deliveryFeeCentavos: Long = 0,
    val note: String = ""
)
