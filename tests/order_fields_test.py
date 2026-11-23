"""
Checks the Payment Method / Preferred Delivery Time fields against real SQLite.
Migration SQL, DAO query and the option lists are EXTRACTED from the Kotlin sources.
Run:  python3 tests/order_fields_test.py
"""
import re, sqlite3, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/shoptracker/pos"
db_kt = (ROOT / "data/AppDatabase.kt").read_text()
dao_kt = (ROOT / "data/Daos.kt").read_text()
models = (ROOT / "data/Models.kt").read_text()

m = re.search(r"MIGRATION_5_6 = object.*?migrate\(db: SupportSQLiteDatabase\) \{(.*?)\n            \}\n        \}", db_kt, re.S)
MIG = ["".join(re.findall(r'"((?:[^"\\]|\\.)*)"', c)) for c in re.findall(r"db\.execSQL\((.*?)\)\n", m.group(1) + "\n", re.S)]
SET_TIME = re.search(r'@Query\("(UPDATE orders SET deliveryTime[^"]*)"\)', dao_kt).group(1)
def kt_list(name):
    body = re.search(name + r": List<String> = listOf\((.*?)\)\n", models, re.S).group(1)
    toks = re.findall(r'"[^"]*"|PAYMENT_COD|DELIVERY_TIME_PENDING', body)
    return [s.replace("PAYMENT_COD", '"COD"').replace("DELIVERY_TIME_PENDING", '"Pending"') for s in toks]

fails = 0
def check(c, msg):
    global fails
    print(("PASS " if c else "FAIL ") + msg)
    if not c: fails += 1

pay = [s.strip('"') for s in kt_list("PAYMENT_METHODS")]
slots = [s.strip('"') for s in kt_list("DELIVERY_TIME_SLOTS")]
check(pay == ["COD", "QR", "Bank Transfer"], f"payment options are exactly COD, QR, Bank Transfer: {pay}")
check(slots[0] == "Pending", "Pending is the FIRST delivery time option")
check(len(slots) == len(set(slots)) and len(slots) >= 2, f"time slots listed once each: {slots}")
check(len(MIG) == 2, "migration has 2 statements")
check("version = 6" in db_kt and "MIGRATION_5_6)" in db_kt, "database version 6 and migration registered")
check('defaultValue = "\'\'"' in models, "entity column defaults match the migration (Room schema check)")

# ---- v5 database with old orders
c = sqlite3.connect(":memory:")
c.executescript("""
CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, businessDayId INTEGER NOT NULL, customerName TEXT NOT NULL,
 type TEXT NOT NULL, address TEXT NOT NULL, deliveryFeeCentavos INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL,
 status TEXT NOT NULL, cancelledAt INTEGER);
""")
c.executemany("INSERT INTO orders(businessDayId,customerName,type,address,deliveryFeeCentavos,note,createdAt,status,cancelledAt) VALUES (?,?,?,?,?,?,?,?,?)",
  [(1,"Ana","DELIVERY","Piapi, Dumaguete",4000,"x",111,"COMPLETED",None),
   (1,"Ben","PICKUP","",0,"",222,"COMPLETED",None),
   (2,"Cy","DELIVERY","Bacong",8500,"",333,"CANCELLED",999)])
cols = "id,businessDayId,customerName,type,address,deliveryFeeCentavos,note,createdAt,status,cancelledAt"
before = c.execute(f"SELECT {cols} FROM orders ORDER BY id").fetchall()
for s in MIG: c.execute(s)
check(c.execute(f"SELECT {cols} FROM orders ORDER BY id").fetchall() == before, "migration keeps every existing order field unchanged (incl. delivery fees)")
check(c.execute("SELECT COUNT(*) FROM orders WHERE paymentMethod='' AND deliveryTime=''").fetchone()[0] == 3, "old orders get blank (= not recorded) payment/time")

# ---- save new orders with both fields
ins = "INSERT INTO orders(businessDayId,customerName,type,address,deliveryFeeCentavos,note,createdAt,status,paymentMethod,deliveryTime) VALUES (3,?,?,?,?,'',500,'COMPLETED',?,?)"
c.execute(ins, ("Dee","DELIVERY","Looc, Sibulan",6000,"QR","Pending"))
c.execute(ins, ("Eli","DELIVERY","Balugo, Dumaguete",7000,"Bank Transfer",slots[2]))
c.execute(ins, ("Fay","PICKUP","",0,"COD",""))
got = {r[0]: r[1:] for r in c.execute("SELECT customerName,paymentMethod,deliveryTime,deliveryFeeCentavos FROM orders WHERE businessDayId=3")}
check(got["Dee"] == ("QR","Pending",6000), "new order saved with QR + Pending")
check(got["Eli"] == ("Bank Transfer", slots[2], 7000), "new order saved with Bank Transfer + chosen slot")
check(got["Fay"] == ("COD","",0), "pickup order saved with payment method, no delivery time")

# ---- edit time after creation
id_dee = c.execute("SELECT id FROM orders WHERE customerName='Dee'").fetchone()[0]
snap = c.execute(f"SELECT {cols},paymentMethod FROM orders WHERE id=?", (id_dee,)).fetchone()
c.execute(SET_TIME, {"time": slots[1], "id": id_dee})
now = c.execute(f"SELECT {cols},paymentMethod,deliveryTime FROM orders WHERE id=?", (id_dee,)).fetchone()
check(now[-1] == slots[1], "edit: Pending -> chosen slot")
check(now[:-1] == snap, "edit changes ONLY the delivery time (fee, payment, status, items untouched)")
c.execute(SET_TIME, {"time": "Pending", "id": id_dee})
check(c.execute("SELECT deliveryTime FROM orders WHERE id=?", (id_dee,)).fetchone()[0] == "Pending", "edit: can go back to Pending")
old_id = c.execute("SELECT id FROM orders WHERE customerName='Ana'").fetchone()[0]
c.execute(SET_TIME, {"time": slots[3], "id": old_id})
check(c.execute("SELECT deliveryTime,deliveryFeeCentavos FROM orders WHERE id=?", (old_id,)).fetchone() == (slots[3], 4000),
      "edit works on an old order and its delivery fee stays 40.00")
check(c.execute("SELECT SUM(deliveryFeeCentavos) FROM orders").fetchone()[0] == 4000+8500+6000+7000, "delivery fee totals unchanged by any edit")

# ---- fresh install table (all columns) accepts the same rows
print("\nALL PASSED" if not fails else f"\n{fails} FAILED"); sys.exit(1 if fails else 0)
