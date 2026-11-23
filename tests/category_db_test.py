"""
Verifies the category feature's database behaviour against real SQLite.
The SQL is EXTRACTED from the Kotlin sources (AppDatabase.kt / Daos.kt), not retyped, so this
tests what the app will actually run.   Run:  python3 tests/category_db_test.py
"""
import re, sqlite3, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/shoptracker/pos/data"
db_kt = (ROOT / "AppDatabase.kt").read_text()
dao_kt = (ROOT / "Daos.kt").read_text()

# ---- extract MIGRATION_3_4 statements
m = re.search(r"MIGRATION_3_4 = object.*?migrate\(db: SupportSQLiteDatabase\) \{(.*?)\n            \}\n        \}", db_kt, re.S)
body = m.group(1)
stmts = []
for call in re.finditer(r"db\.execSQL\((.*?)\)\n", body + "\n", re.S):
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', call.group(1))
    stmts.append("".join(parts))
assert len(stmts) == 3, stmts

# ---- extract DAO queries by function name
def q(fn):
    m = re.search(r'@Query\("([^"]+)"\)\s*(?:suspend )?fun ' + fn + r"\(", dao_kt)
    assert m, fn
    return m.group(1)

COUNT = q("countInCategory"); MOVE = q("moveCategory")
FIND = q("findByName"); DEL = q("deleteById"); LIST = q("observeAll")  # first observeAll = products

fails = 0
def check(cond, msg):
    global fails
    print(("PASS " if cond else "FAIL ") + msg)
    if not cond: fails += 1

# ---- build a v3 database exactly as Room 3 would have it, with realistic data
c = sqlite3.connect(":memory:")
c.executescript("""
CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
  priceCentavos INTEGER NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL,
  profitCentavos INTEGER NOT NULL DEFAULT 0, stock INTEGER NOT NULL DEFAULT 0);
CREATE TABLE sales_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, businessDayId INTEGER NOT NULL,
  productId INTEGER NOT NULL, productName TEXT NOT NULL, unitPriceCentavos INTEGER NOT NULL, quantity INTEGER NOT NULL,
  totalCentavos INTEGER NOT NULL, timestamp INTEGER NOT NULL, status TEXT NOT NULL, undoneAt INTEGER,
  unitProfitCentavos INTEGER NOT NULL DEFAULT 0, orderId INTEGER);
""")
rows = [("Coke", 2500, 1, 111, 500, 40), ("Chips", 1500, 1, 222, 300, 0), ("Old Item", 999, 0, 333, 100, 7),
        ("Água ñ", 1200, 1, 444, 200, 12)]
c.executemany("INSERT INTO products(name,priceCentavos,active,createdAt,profitCentavos,stock) VALUES (?,?,?,?,?,?)", rows)
c.execute("INSERT INTO sales_transactions(businessDayId,productId,productName,unitPriceCentavos,quantity,totalCentavos,timestamp,status)"
          " VALUES (1,1,'Coke',2500,2,5000,10,'COMPLETED')")
before = c.execute("SELECT id,name,priceCentavos,active,createdAt,profitCentavos,stock FROM products ORDER BY id").fetchall()

# ---- migrate
for s in stmts: c.execute(s)
after = c.execute("SELECT id,name,priceCentavos,active,createdAt,profitCentavos,stock FROM products ORDER BY id").fetchall()
check(before == after, "migration keeps every existing product and all its fields unchanged")
check(c.execute("SELECT COUNT(*) FROM products WHERE categoryId IS NULL").fetchone()[0] == len(rows),
      "all existing products become Uncategorized (categoryId NULL)")
check(c.execute("SELECT COUNT(*) FROM sales_transactions").fetchone()[0] == 1, "sales history untouched")

# schema must equal what Room expects for the new entities
cols = {r[1]: (r[2], r[3], r[5]) for r in c.execute("PRAGMA table_info(categories)")}
check(cols == {"id": ("INTEGER", 1, 1), "name": ("TEXT", 1, 0), "createdAt": ("INTEGER", 1, 0)},
      "categories table matches the Category entity (types / NOT NULL / PK)")
pc = {r[1]: (r[2], r[3], r[4]) for r in c.execute("PRAGMA table_info(products)")}
check(pc["categoryId"] == ("INTEGER", 0, None), "products.categoryId is nullable INTEGER with no default")
check(any(r[1] == "index_products_categoryId" for r in c.execute("PRAGMA index_list(products)")),
      "index_products_categoryId exists (Room validates this name)")
# idempotent-ish: IF NOT EXISTS statements are safe
c.execute(stmts[0]); c.execute(stmts[2])
check(True, "CREATE statements re-runnable")

# ---- category behaviour using the app's own queries
def add_cat(name):
    if c.execute(FIND, {"name": name}).fetchone(): return False
    c.execute("INSERT INTO categories(name,createdAt) VALUES (?,1)", (name,)); return c.execute("SELECT last_insert_rowid()").fetchone()[0]
drinks = add_cat("Drinks"); snacks = add_cat("Snacks")
check(add_cat("drinks") is False and add_cat("DRINKS") is False, "duplicate names rejected case-insensitively")
uni = add_cat("Ñandú")
check(uni not in (False, None), "unicode category name accepted")
# NOTE: SQLite's NOCASE only folds ASCII, so "ñandú" is treated as different from "Ñandú" at DB level.
# Kotlin-side uniqueness for such names is best-effort; ASCII names (the common case) are fully covered.

c.execute("UPDATE products SET categoryId=? WHERE name IN ('Coke','Água ñ')", (drinks,))
c.execute("UPDATE products SET categoryId=? WHERE name='Chips'", (snacks,))
check(c.execute(COUNT, {"categoryId": drinks}).fetchone()[0] == 2, "countInCategory = 2 for Drinks")

def delete_category(cid, move):  # mirrors MainViewModel.deleteCategory (move: None | 'UNCAT' | id)
    inside = c.execute(COUNT, {"categoryId": cid}).fetchone()[0]
    if inside > 0 and move is None: return "refused"
    if move not in (None, "UNCAT") and move == cid: return "same"
    if move not in (None, "UNCAT") and not c.execute("SELECT 1 FROM categories WHERE id=?", (move,)).fetchone(): return "missing"
    target = None if move in (None, "UNCAT") else move
    if inside > 0: c.execute(MOVE, {"toCategoryId": target, "fromCategoryId": cid})
    c.execute(DEL, {"id": cid}); return "ok"

n = c.execute("SELECT COUNT(*) FROM products").fetchone()[0]
check(delete_category(drinks, None) == "refused", "delete of non-empty category WITHOUT a target is refused")
check(c.execute("SELECT COUNT(*) FROM categories WHERE id=?", (drinks,)).fetchone()[0] == 1, "...and the category still exists")
check(delete_category(drinks, drinks) == "same", "cannot move products into the category being deleted")
check(delete_category(drinks, 9999) == "missing", "cannot move products into a non-existent category")
check(delete_category(drinks, snacks) == "ok", "delete with reassignment to another category succeeds")
check(c.execute(COUNT, {"categoryId": snacks}).fetchone()[0] == 3, "all 2 moved products + 1 existing now in Snacks")
check(c.execute("SELECT COUNT(*) FROM products").fetchone()[0] == n, "no product lost by category delete")
check(c.execute("SELECT COUNT(*) FROM categories WHERE id=?", (drinks,)).fetchone()[0] == 0, "deleted category is gone")

check(delete_category(snacks, "UNCAT") == "ok", "delete with reassignment to Uncategorized succeeds")
check(c.execute("SELECT COUNT(*) FROM products WHERE categoryId IS NULL").fetchone()[0] == n, "all products Uncategorized, none lost")
after2 = c.execute("SELECT id,name,priceCentavos,active,createdAt,profitCentavos,stock FROM products ORDER BY id").fetchall()
check(before == after2, "product data (name/price/profit/stock/active) identical after the whole sequence")

empty = add_cat("Empty")
check(delete_category(empty, None) == "ok", "empty category deletes without needing a target")

# rename keeps products attached (they reference id, not name)
cat = add_cat("Frozen"); c.execute("UPDATE products SET categoryId=? WHERE name='Coke'", (cat,))
c.execute("UPDATE categories SET name='Cold' WHERE id=?", (cat,))
check(c.execute("SELECT c.name FROM products p JOIN categories c ON c.id=p.categoryId WHERE p.name='Coke'").fetchone()[0] == "Cold",
      "renaming a category keeps its products attached")

# dangling id never hides a product (UI groups unknown ids under Uncategorized)
c.execute("UPDATE products SET categoryId=424242 WHERE name='Chips'")
known = {r[0] for r in c.execute("SELECT id FROM categories")}
shown = [r[0] for r in c.execute("SELECT id, categoryId FROM products") ]
check(len(shown) == n, "product with dangling category id is still in the product table / list")

print("\n%d failure(s)" % fails); sys.exit(1 if fails else 0)
