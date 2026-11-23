package com.shoptracker.pos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("UPDATE products SET stock = stock + :delta WHERE id = :id AND stock + :delta >= 0")
    suspend fun adjustStock(id: Long, delta: Int): Int

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: Long): Int

    /** Moves every product of one category to another (null = Uncategorized). Returns rows moved. */
    @Query("UPDATE products SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun moveCategory(fromCategoryId: Long, toCategoryId: Long?): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BusinessDayDao {
    @Query("SELECT * FROM business_days WHERE closedAt IS NULL ORDER BY id DESC LIMIT 1")
    fun observeOpen(): Flow<BusinessDay?>

    @Query("SELECT * FROM business_days WHERE closedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): BusinessDay?

    @Query("SELECT * FROM business_days ORDER BY id DESC")
    fun observeAll(): Flow<List<BusinessDay>>

    @Query("SELECT * FROM business_days ORDER BY id")
    suspend fun getAll(): List<BusinessDay>

    @Query("SELECT * FROM business_days ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): BusinessDay?

    @Query("SELECT * FROM business_days WHERE id = :id")
    suspend fun getById(id: Long): BusinessDay?

    @Insert
    suspend fun insert(day: BusinessDay): Long

    @Update
    suspend fun update(day: BusinessDay)

    @Query("DELETE FROM business_days WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM sales_transactions ORDER BY timestamp, id")
    fun observeAll(): Flow<List<SalesTransaction>>

    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId ORDER BY timestamp, id")
    suspend fun getForDay(dayId: Long): List<SalesTransaction>

    @Query("SELECT * FROM sales_transactions ORDER BY timestamp, id")
    suspend fun getAll(): List<SalesTransaction>

    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId AND status = 'COMPLETED' ORDER BY id DESC LIMIT 1")
    suspend fun getLastCompleted(dayId: Long): SalesTransaction?

    /** Latest completed single sale (not part of an order) of one product. */
    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId AND productId = :productId AND orderId IS NULL AND status = 'COMPLETED' ORDER BY id DESC LIMIT 1")
    suspend fun getLastSingleSale(dayId: Long, productId: Long): SalesTransaction?

    @Query("SELECT * FROM sales_transactions WHERE orderId = :orderId")
    suspend fun getByOrder(orderId: Long): List<SalesTransaction>

    @Query("UPDATE sales_transactions SET status = 'UNDONE', undoneAt = :time WHERE id = :id AND status = 'COMPLETED'")
    suspend fun markUndone(id: Long, time: Long): Int

    @Query("UPDATE sales_transactions SET status = 'UNDONE', undoneAt = :time WHERE orderId = :orderId AND status = 'COMPLETED'")
    suspend fun markUndoneByOrder(orderId: Long, time: Long): Int

    @Insert
    suspend fun insert(tx: SalesTransaction): Long

    @Update
    suspend fun update(tx: SalesTransaction)

    @Query("DELETE FROM sales_transactions WHERE orderId = :orderId")
    suspend fun deleteByOrder(orderId: Long)

    @Query("DELETE FROM sales_transactions WHERE businessDayId = :dayId")
    suspend fun deleteByDay(dayId: Long)

    @Query("SELECT productId AS productId, SUM(quantity) AS units, SUM(totalCentavos) AS revenue FROM sales_transactions WHERE status = 'COMPLETED' GROUP BY productId")
    fun observeLifetime(): Flow<List<ProductTotal>>
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY id")
    fun observeAll(): Flow<List<SalesOrder>>

    @Query("SELECT * FROM orders ORDER BY id")
    suspend fun getAll(): List<SalesOrder>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: Long): SalesOrder?

    @Insert
    suspend fun insert(order: SalesOrder): Long

    /** Changes ONLY the preferred delivery time; nothing else on the order is touched. */
    @Query("UPDATE orders SET deliveryTime = :time WHERE id = :id")
    suspend fun setDeliveryTime(id: Long, time: String): Int

    @Query("UPDATE orders SET paid = :paid WHERE id = :id AND status = 'COMPLETED'")
    suspend fun setPaid(id: Long, paid: Boolean): Int

    @Query("UPDATE orders SET delivered = :delivered WHERE id = :id AND status = 'COMPLETED'")
    suspend fun setDelivered(id: Long, delivered: Boolean): Int

    @Update
    suspend fun update(order: SalesOrder)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM orders WHERE businessDayId = :dayId")
    suspend fun deleteByDay(dayId: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY id")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun getAll(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Insert
    suspend fun insert(expense: Expense): Long

    @Delete
    suspend fun delete(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Query("DELETE FROM expenses WHERE businessDayId = :dayId")
    suspend fun deleteByDay(dayId: Long)
}

@Dao
interface WasteDao {
    @Query("SELECT * FROM wastes ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Waste>>

    @Insert
    suspend fun insert(waste: Waste): Long

    @Query("SELECT * FROM wastes WHERE businessDayId = :dayId")
    suspend fun getAllForDay(dayId: Long): List<Waste>

    @Update
    suspend fun update(waste: Waste)

    @Query("DELETE FROM wastes WHERE businessDayId = :dayId")
    suspend fun deleteByDay(dayId: Long)

    @Query("DELETE FROM wastes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM wastes WHERE id = :id")
    suspend fun getById(id: Long): Waste?
}

@Dao
interface RateDao {
    @Query("SELECT * FROM delivery_rates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DeliveryRate>>

    @Insert
    suspend fun insert(rate: DeliveryRate): Long

    @Update
    suspend fun update(rate: DeliveryRate)

    @Delete
    suspend fun delete(rate: DeliveryRate)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Customer>>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer): Long
    @Update suspend fun update(customer: Customer)
    @Query("DELETE FROM customers WHERE id = :id") suspend fun delete(id: Long)
}
