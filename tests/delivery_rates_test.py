"""
Checks Delivery Rates behaviour against real SQLite. The seed list, the seeding SQL and the
create-table SQL are EXTRACTED from AppDatabase.kt / Daos.kt, not retyped.
Run:  python3 tests/delivery_rates_test.py
"""
import re, sqlite3, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/shoptracker/pos"
db_kt = (ROOT / "data/AppDatabase.kt").read_text()
dao_kt = (ROOT / "data/Daos.kt").read_text()

seed = re.findall(r'^\s+"([^"|]+\|\d+\|[^"]*)"[,]?$', db_kt, re.M)
SEED = [tuple(l.split("|")) for l in seed]
m = re.search(r'"(INSERT INTO delivery_rates[^"]*)"\s*\+\s*"(WHERE NOT EXISTS[^"]*)"', db_kt)
SEED_SQL = m.group(1) + m.group(2)
# rebuild the multi-part CREATE from MIGRATION_1_2
blk = re.search(r'"CREATE TABLE IF NOT EXISTS delivery_rates \(".*?note TEXT NOT NULL\)"', db_kt, re.S).group(0)
CREATE = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', blk))
LIST = re.search(r'@Query\("(SELECT \* FROM delivery_rates[^"]*)"\)', dao_kt).group(1)

fails = 0
def check(c, msg):
    global fails
    print(("PASS " if c else "FAIL ") + msg)
    if not c: fails += 1

def seed_db(c):
    for n, f, note in SEED: c.execute(SEED_SQL, (n, int(f), note, n))

check(len(SEED) == 47, f"47 starter rates found in source (got {len(SEED)})")
check(len({n.lower() for n, _, _ in SEED}) == 47, "starter names are unique")
check(all(ord(ch) < 128 or ch in "₱–" for n, _, nt in SEED for ch in n + nt), "no look-alike (non-ASCII) letters in names")

# ---- v4 database with an existing rate, an order, and a custom rate
c = sqlite3.connect(":memory:")
c.execute(CREATE)
c.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, address TEXT NOT NULL, deliveryFeeCentavos INTEGER NOT NULL)")
c.execute("INSERT INTO delivery_rates(name,feeCentavos,note) VALUES ('Bagacay, Dumaguete', 9900, 'my own fee')")
c.execute("INSERT INTO delivery_rates(name,feeCentavos,note) VALUES ('My Special Place', 12345, 'keep me')")
c.execute("INSERT INTO orders(address,deliveryFeeCentavos) VALUES ('Piapi', 4000), ('Bacong', 8500)")
orders_before = c.execute("SELECT * FROM orders ORDER BY id").fetchall()

seed_db(c)  # == MIGRATION_4_5
n = c.execute("SELECT COUNT(*) FROM delivery_rates").fetchone()[0]
check(n == 48, f"upgrade: 2 existing + 47 seeds - 1 duplicate = 48 rows (got {n})")
check(c.execute("SELECT feeCentavos,note FROM delivery_rates WHERE name='Bagacay, Dumaguete'").fetchall() == [(9900, 'my own fee')],
      "existing rate with same name NOT overwritten")
check(c.execute("SELECT feeCentavos FROM delivery_rates WHERE name='My Special Place'").fetchone()[0] == 12345, "existing custom rate preserved")
seed_db(c)
check(c.execute("SELECT COUNT(*) FROM delivery_rates").fetchone()[0] == 48, "seeding twice adds no duplicates")
check(c.execute("SELECT * FROM orders ORDER BY id").fetchall() == orders_before, "migration leaves orders untouched")

# ---- spot-check the requested prices
want = {"Bajumpandan, Dumaguete": 7000, "Camancjac, Dumaguete": 7000, "Poblacion No. 5 (Silliman Area), Dumaguete": 4000,
        "Looc, Dumaguete": 4000, "Looc, Sibulan": 6000, "Balugo, Dumaguete": 7000, "Balugo, Sibulan": 10000,
        "Enrique Villanueva, Sibulan": 10000, "Bacong": 8000, "Valencia Area": 8000}
for k, v in want.items():
    r = c.execute("SELECT feeCentavos FROM delivery_rates WHERE name=?", (k,)).fetchone()
    check(r and r[0] == v, f"{k} = {v/100:.2f}")
check(c.execute("SELECT COUNT(*) FROM delivery_rates WHERE name LIKE '%Dumaguete'").fetchone()[0] == 30, "30 Dumaguete barangays")
check(c.execute("SELECT COUNT(*) FROM delivery_rates WHERE name LIKE '%Sibulan'").fetchone()[0] == 15, "15 Sibulan barangays")

# ---- ADD / EDIT / DELETE, and orders stay frozen
c.execute("INSERT INTO delivery_rates(name,feeCentavos,note) VALUES ('Tanjay',15000,'')")
check(c.execute("SELECT feeCentavos FROM delivery_rates WHERE name='Tanjay'").fetchone()[0] == 15000, "ADD: new rate saved")
c.execute("UPDATE delivery_rates SET feeCentavos=5500, name='Piapi, Dumaguete (edited)' WHERE name='Piapi, Dumaguete'")
check(c.execute("SELECT feeCentavos FROM delivery_rates WHERE name='Piapi, Dumaguete (edited)'").fetchone()[0] == 5500, "EDIT: name and fee changed")
check(c.execute("SELECT COUNT(*) FROM delivery_rates WHERE name='Piapi, Dumaguete'").fetchone()[0] == 0, "EDIT: no duplicate left behind")
c.execute("DELETE FROM delivery_rates WHERE name='Tanjay'")
check(c.execute("SELECT COUNT(*) FROM delivery_rates WHERE name='Tanjay'").fetchone()[0] == 0, "DELETE: rate removed")
c.execute("DELETE FROM delivery_rates WHERE name LIKE 'Piapi%'")
check(c.execute("SELECT * FROM orders ORDER BY id").fetchall() == orders_before,
      "orders keep original delivery fee after rates were edited AND deleted")
names = [r[1] for r in c.execute(LIST)]
check(names == sorted(names, key=str.lower), "list is sorted A-Z")

# ---- fresh install path
f = sqlite3.connect(":memory:"); f.execute(CREATE); seed_db(f)
check(f.execute("SELECT COUNT(*) FROM delivery_rates").fetchone()[0] == 47, "fresh install gets all 47 starter rates")

print("\nALL PASSED" if not fails else f"\n{fails} FAILED"); sys.exit(1 if fails else 0)
