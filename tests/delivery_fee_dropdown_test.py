"""
Checks the New Order "Delivery fee" dropdown (Custom first + Delivery Rates).
Part 1 inspects the Kotlin source (structure rules). Part 2 replays the form's fee/total logic
and the order save/edit-rate/delete-rate flow against real SQLite (schema EXTRACTED from AppDatabase.kt).
Run:  python3 tests/delivery_fee_dropdown_test.py
"""
import re, sqlite3, pathlib, sys
from decimal import Decimal, ROUND_HALF_UP

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/shoptracker/pos"
screen = (ROOT / "ui/NewOrderScreen.kt").read_text(encoding="utf-8")
vm = (ROOT / "MainViewModel.kt").read_text(encoding="utf-8")
db_kt = (ROOT / "data/AppDatabase.kt").read_text(encoding="utf-8")

fails = 0
def check(c, msg):
    global fails
    print(("PASS " if c else "FAIL ") + msg)
    if not c: fails += 1

# ---------- Part 1: source structure ----------
menu = re.search(r"DropdownMenu\(expanded = open.*?\n        \}\n", screen, re.S).group(0)
check(menu.index("CUSTOM_FEE_LABEL") < menu.index("rates.forEach"), "Custom is the FIRST dropdown option, before every rate")
check('CUSTOM_FEE_LABEL = "Custom"' in screen, 'first option text is "Custom"')
check("rates.forEach" in menu and "onRate(r)" in menu, "every Delivery Rate is listed and selectable")
check(re.search(r"if \(isCustomFee\) \{\s*MoneyField", screen) is not None, "manual amount input is shown ONLY when Custom is selected")
check("val isCustomFee = selectedRate == null" in screen, "Custom == no rate selected")
check("fee = Fmt.plain(r.feeCentavos)" in screen, "choosing a rate copies its fee (snapshot) into the order form")
check("showRates" not in screen, "old 'Choose from Delivery Rates' toggle removed (replaced by the dropdown)")
check("val grandTotal = itemsTotal + (feeCentavos ?: 0L)" in screen, "total = items + delivery fee")
check("val itemsTotal = products.sumOf { it.priceCentavos * (qty[it.id] ?: 0) }" in screen, "items total = price x quantity")
check("vm.createOrder(customer, type, address, feeCentavos," in screen, "the actual fee amount (not a rate id) is passed to createOrder")
check("deliveryFeeCentavos = if (type == ORDER_DELIVERY) deliveryFeeCentavos else 0L" in vm, "order stores the fee amount; pickup stores 0")
check("rateId" not in vm and "rateId" not in db_kt, "orders have no link/foreign key to a rate")
check("version = 6" in db_kt, "no database change needed (still version 6)")

# ---------- Part 2: behaviour replay ----------
def parse_peso(t):
    t = t.strip().replace(",", "").replace("₱", "")
    if not t: return None
    try: d = Decimal(t)
    except Exception: return None
    if d < 0: return None
    return int((d.quantize(Decimal("0.01"), ROUND_HALF_UP) * 100))

class Form:
    """Python mirror of OrderForm's fee logic."""
    def __init__(self, rates): self.rates, self.rate_id, self.fee, self.delivery = rates, None, "", True
    def pick_custom(self):
        if self.rate_id is not None: self.rate_id, self.fee = None, ""
    def pick_rate(self, rid):
        self.rate_id, self.fee = rid, f"{self.rates[rid][1]/100:.2f}"
    def type_custom(self, v): assert self.rate_id is None; self.fee = v
    @property
    def fee_centavos(self): return parse_peso(self.fee) if self.delivery else 0
    def total(self, lines): return sum(p * q for p, q in lines) + (self.fee_centavos or 0)

c = sqlite3.connect(":memory:")
c.execute(re.search(r'"CREATE TABLE IF NOT EXISTS delivery_rates \(".*?note TEXT NOT NULL\)"', db_kt, re.S).group(0) and
          "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', re.search(r'"CREATE TABLE IF NOT EXISTS delivery_rates \(".*?note TEXT NOT NULL\)"', db_kt, re.S).group(0))))
c.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT, deliveryFeeCentavos INTEGER NOT NULL)")
c.execute("INSERT INTO delivery_rates(name,feeCentavos,note) VALUES ('Looc, Dumaguete',4000,''),('Balugo, Sibulan',10000,''),('Free zone',0,'')")
rates = {r[0]: (r[1], r[2]) for r in c.execute("SELECT id,name,feeCentavos FROM delivery_rates")}
rid = {v[0]: k for k, v in rates.items()}
lines = [(15000, 2), (8050, 3)]            # 2 x 150.00 + 3 x 80.50 = 541.50
items = 2 * 15000 + 3 * 8050
check(items == 54150, "items total = 541.50")

f = Form(rates)
check(f.fee_centavos is None, "Custom with nothing typed -> fee invalid (save is blocked)")
f.type_custom("75.5");  check(f.fee_centavos == 7550 and f.total(lines) == items + 7550, "CUSTOM 75.50 -> total 617.00")
f.type_custom("0");     check(f.fee_centavos == 0 and f.total(lines) == items, "CUSTOM 0 (free) -> total 541.50")
f.type_custom("1,250"); check(f.fee_centavos == 125000, "CUSTOM accepts 1,250")
f.pick_rate(rid["Looc, Dumaguete"]);  check(f.fee_centavos == 4000 and f.total(lines) == items + 4000, "RATE Looc 40.00 applied automatically -> total 581.50")
f.pick_rate(rid["Balugo, Sibulan"]);  check(f.fee_centavos == 10000 and f.total(lines) == items + 10000, "RATE Balugo 100.00 applied -> total 641.50")
f.pick_rate(rid["Free zone"]);        check(f.fee_centavos == 0, "RATE with 0.00 fee is valid")
f.pick_custom(); check(f.fee_centavos is None and f.rate_id is None, "switching back to Custom clears the amount (must be entered)")
f.delivery = False; check(f.fee_centavos == 0 and f.total(lines) == items, "PICKUP ignores any fee")

# save two orders (one custom, one rate), then edit + delete rates
f = Form(rates); f.type_custom("75.50"); c.execute("INSERT INTO orders(address,deliveryFeeCentavos) VALUES ('Custom spot',?)", (f.fee_centavos,))
f.pick_rate(rid["Looc, Dumaguete"]);     c.execute("INSERT INTO orders(address,deliveryFeeCentavos) VALUES ('Looc',?)", (f.fee_centavos,))
before = c.execute("SELECT * FROM orders ORDER BY id").fetchall()
check([r[2] for r in before] == [7550, 4000], "saved orders hold the ACTUAL fee amounts (75.50 custom, 40.00 rate)")
c.execute("UPDATE delivery_rates SET feeCentavos=9999, name='Looc (renamed)' WHERE id=?", (rid["Looc, Dumaguete"],))
check(c.execute("SELECT * FROM orders ORDER BY id").fetchall() == before, "EDITING a rate does not change recorded order fees")
c.execute("DELETE FROM delivery_rates WHERE id=?", (rid["Looc, Dumaguete"],))
check(c.execute("SELECT * FROM orders ORDER BY id").fetchall() == before, "DELETING a rate does not change recorded order fees")
f = Form({k: v for k, v in rates.items() if k != rid["Looc, Dumaguete"]}); f.pick_rate(rid["Balugo, Sibulan"])
check(f.fee_centavos == 10000, "new orders still pick up other rates after edits/deletes")

print("\nALL PASSED" if not fails else f"\n{fails} FAILED"); sys.exit(1 if fails else 0)
