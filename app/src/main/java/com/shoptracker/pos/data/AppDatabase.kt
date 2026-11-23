package com.shoptracker.pos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class,
        BusinessDay::class,
        SalesTransaction::class,
        SalesOrder::class,
        Expense::class,
        DeliveryRate::class,
        Category::class,
        Waste::class,
        Customer::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun businessDayDao(): BusinessDayDao
    abstract fun transactionDao(): TransactionDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun rateDao(): RateDao
    abstract fun categoryDao(): CategoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun wasteDao(): WasteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: adds profit/stock, orders, expenses and delivery fee reference. Keeps all data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN profitCentavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales_transactions ADD COLUMN unitProfitCentavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales_transactions ADD COLUMN orderId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS orders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "businessDayId INTEGER NOT NULL, " +
                        "customerName TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "address TEXT NOT NULL, " +
                        "deliveryFeeCentavos INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "cancelledAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_businessDayId ON orders (businessDayId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS expenses (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "businessDayId INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "amountCentavos INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_businessDayId ON expenses (businessDayId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS delivery_rates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "feeCentavos INTEGER NOT NULL, " +
                        "note TEXT NOT NULL)"
                )
            }
        }

        /** v2 -> v3: adds nullable remit to business days. Existing rows keep NULL (unchanged). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_days ADD COLUMN remitCentavos INTEGER")
            }
        }

        /**
         * v3 -> v4: adds categories. Products get a NULL categoryId, which means "Uncategorized",
         * so every existing product keeps all of its data and stays visible and sellable.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE products ADD COLUMN categoryId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_categoryId ON products (categoryId)")
            }
        }

        /**
         * Starter Delivery Rates: one line per rate, "name|fee in centavos|note". Used both when a
         * brand-new database is created and by MIGRATION_4_5. Seeding NEVER overwrites or removes
         * anything: a rate is only inserted if no rate with the same name already exists.
         * (Bacong / Valencia are quoted as a ₱80–₱90 range: the lower fee is stored and the range is
         * in the note, so the exact fee can be typed on the order.)
         */
        val DEFAULT_RATES: List<String> = listOf(
            "Bagacay, Dumaguete|5000|",
            "Bajumpandan, Dumaguete|7000|",
            "Balugo, Dumaguete|7000|",
            "Banilad, Dumaguete|6000|",
            "Bantayan, Dumaguete|4000|",
            "Batinguel, Dumaguete|6000|",
            "Bunao, Dumaguete|4000|",
            "Cadawinonan, Dumaguete|6000|",
            "Calindagan, Dumaguete|4000|",
            "Camancjac, Dumaguete|7000|",
            "Candau-ay, Dumaguete|7000|",
            "Cantil-e, Dumaguete|7000|",
            "Daro, Dumaguete|4000|",
            "Junob, Dumaguete|5000|",
            "Looc, Dumaguete|4000|",
            "Mangnao-Canal, Dumaguete|6000|",
            "Motong, Dumaguete|4000|",
            "Piapi, Dumaguete|4000|",
            "Poblacion No. 1 (Tinago), Dumaguete|4000|",
            "Poblacion No. 2 (Upper Luke Wright), Dumaguete|4000|",
            "Poblacion No. 3 (Colon Extension), Dumaguete|4000|",
            "Poblacion No. 4 (Boulevard area), Dumaguete|4000|",
            "Poblacion No. 5 (Silliman Area), Dumaguete|4000|",
            "Poblacion No. 6 (Cambagroy), Dumaguete|4000|",
            "Poblacion No. 7 (Mangga), Dumaguete|4000|",
            "Poblacion No. 8 (Cervantes), Dumaguete|4000|",
            "Pulantubig, Dumaguete|4000|",
            "Tabuctubig, Dumaguete|5000|",
            "Taclobo, Dumaguete|5000|",
            "Talay, Dumaguete|7000|",
            "Cangmating, Sibulan|4000|",
            "Calabnugan, Sibulan|10000|",
            "Looc, Sibulan|6000|",
            "Maslog, Sibulan|4000|",
            "Bolocboloc, Sibulan|4000|",
            "Agan-an, Sibulan|4000|",
            "Balugo, Sibulan|10000|",
            "Poblacion, Sibulan|4000|",
            "Tubtubon, Sibulan|5000|",
            "Magatas, Sibulan|5000|",
            "Tubigon, Sibulan|10000|",
            "San Antonio, Sibulan|10000|",
            "Maningcao, Sibulan|10000|",
            "Ajong, Sibulan|7000|",
            "Enrique Villanueva, Sibulan|10000|"
        )

        /**
         * v5 -> v6: orders get a payment method and a preferred delivery time. Both default to ''
         * ("not recorded"), so every existing order keeps all of its data and totals unchanged.
         */
        /** Adds starter inventory without modifying existing products, stock or transactions. */
        private fun seedStarterInventory(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            for (category in listOf("S&M")) {
                db.execSQL(
                    "INSERT INTO categories (name, createdAt) SELECT ?, ? WHERE NOT EXISTS " +
                        "(SELECT 1 FROM categories WHERE name = ? COLLATE NOCASE)",
                    arrayOf<Any>(category, now, category)
                )
            }
            val products = listOf(
                arrayOf("Classic Lumpia", "S&M", 18500L, 5000L, 20),
                arrayOf("Cheese Lumpia", "S&M", 22000L, 5000L, 25),
                arrayOf("Longganisa Patty", "S&M", 18500L, 7500L, 20),
                arrayOf("Chicken Cordon Bleu", "S&M", 22000L, 5500L, 20),
                arrayOf("Daing Bangus", "S&M", 22000L, 6000L, 10),
                arrayOf("Cheese Sticks", "S&M", 8000L, 2500L, 20)
            )
            for (product in products) {
                val name = product[0] as String
                val category = product[1] as String
                db.execSQL(
                    "INSERT INTO products (name, priceCentavos, active, createdAt, profitCentavos, stock, categoryId) " +
                        "SELECT ?, ?, 1, ?, ?, ?, (SELECT id FROM categories WHERE name = ? COLLATE NOCASE ORDER BY id LIMIT 1) " +
                        "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = ? COLLATE NOCASE)",
                    arrayOf<Any>(name, product[2] as Long, now, product[3] as Long, product[4] as Int, category, name)
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No reseeding on upgrade.
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS wastes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, businessDayId INTEGER NOT NULL, productId INTEGER NOT NULL, productName TEXT NOT NULL, quantity INTEGER NOT NULL, unitValueCentavos INTEGER NOT NULL, reason TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wastes_businessDayId ON wastes (businessDayId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN paid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN delivered INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN deliveryTime TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun seedDefaultRates(db: SupportSQLiteDatabase) {
            for (line in DEFAULT_RATES) {
                val (name, fee, note) = line.split("|")
                db.execSQL(
                    "INSERT INTO delivery_rates (name, feeCentavos, note) SELECT ?, ?, ? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM delivery_rates WHERE name = ? COLLATE NOCASE)",
                    arrayOf<Any>(name, fee.toLong(), note, name)
                )
            }
        }

        /**
         * v4 -> v5: no table changes. Pre-fills the Delivery Rates list. Existing rates and all
         * orders (which store their own delivery fee) are left untouched.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedDefaultRates(db)
            }
        }

        /** Upgrade existing v9 installations without changing their tables or records. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Idempotent: only absent starter entries are inserted.
                // Existing user choices, including deletions, are authoritative.
            }
        }

        /**
         * A fresh Room database is first opened after its tables are created. Seed on OPEN,
         * not only on CREATE, so new installs and upgrades reliably receive missing defaults.
         * The NOT EXISTS guards never reset stock, edited prices, or existing delivery rates.
         */
        private val ENSURE_STARTER_DATA = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    seedStarterInventory(db)
                    seedDefaultRates(db)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, deliveryFeeCentavos INTEGER NOT NULL, note TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_name ON customers (name)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shop_tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .addCallback(ENSURE_STARTER_DATA)
                    .build().also { instance = it }
            }
    }
}
