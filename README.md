# Shop Sales Tracker (Android POS) - v1.1

Offline POS / daily sales tracker. Kotlin, Jetpack Compose, Material 3, Room, MVVM. Money is stored as whole centavos (Long). No login, no cloud, no internet permission.

## Getting the APK

**GitHub Actions (no tools needed)**
1. Upload the contents of this folder to your GitHub repo (replace existing files; keep the `.github` folder and `app/shop-debug.keystore`).
2. Actions tab > "Build debug APK" runs automatically (or Run workflow).
3. Download `ShopSalesTracker-debug-apk` from the finished run, unzip, install `app-debug.apk`.

**Android Studio**: open this folder, Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Updating from v1.0
The project is now signed with a fixed key (`app/shop-debug.keystore`). v1.0 builds were signed with a random key, so the FIRST time you must uninstall v1.0 (export to Excel first if you need its data). After that, every new build installs over the previous one and keeps your data. The database migrates automatically.

## Product categories (v1.4)
Products > CATEGORIES: add, rename and delete categories. The Add/Edit product form has a Category dropdown, and the product list is grouped by category (A-Z, then "Uncategorized"). A category that still has products cannot be deleted until you choose where to move them (another category or Uncategorized); products are never deleted by this. Database migrates 3 -> 4; every existing product becomes Uncategorized with all its data unchanged. `python3 tests/category_db_test.py` checks the migration and delete rules against SQLite.

## Daily closing: Remit (v1.2)
Delivery Rates: the old "DF Rates" screen, renamed. Add, edit (Edit) and delete rates, each a location/name plus a delivery fee. 47 starter rates (Dumaguete, Sibulan, Bacong, Valencia) are added once on upgrade (DB 4 -> 5) or on a fresh install; existing rates are kept and nothing is overwritten. New Order's Delivery fee is a dropdown: "Custom" is always first (shows a manual amount box); every Delivery Rate follows and applies its fee automatically. `python3 tests/delivery_fee_dropdown_test.py` checks this. Orders store their own copy of the fee, so editing or deleting a rate never changes past orders. `python3 tests/delivery_rates_test.py` checks this against SQLite.

New Order now has a Payment Method dropdown (COD, QR, Bank Transfer) and, for delivery orders, a Preferred Delivery Time dropdown whose FIRST option is Pending (customer has not confirmed a time). Both are saved with the order and shown in Orders and in the Excel export (two new columns at the end of the Orders sheet). Orders > "Edit delivery time" changes only the time, at any time. The time slots are the list DELIVERY_TIME_SLOTS in data/Models.kt. Database migrates 5 -> 6; older orders keep everything and show blank payment / "Not recorded" time. `python3 tests/order_fields_test.py` checks this against SQLite.

Close Day now takes Final cash on hand + Remit; Final floating money = Cash on hand - Remit (shown before confirming). Remit is stored in its own column (not an expense). Database migrates 2 -> 3; older closed days are untouched (Remit blank).

## What's new in 1.1
- Products: profit per unit and stocks; remaining stock shown on each card (red when out).
- Main cards: + and - buttons (tap the card or + to add, - to remove one); UNDO stays.
- Confirm taps: every add/remove asks first (switch "Confirm taps" on the main screen).
- NEW ORDER: bulk/pickup/delivery orders with quantities; delivery asks for the fee.
- Orders screen with cancel; Delivery Rates (formerly DF Rates; add / edit / delete); Expenses (popup form) tallied on closing.
- CHART: Today / Last Week / Last Month / Custom (calendar), delivery fees included in sales.
- Excel export now has Orders and Expenses sheets plus profit and delivery fee columns.

## November 2026 incremental update (source delivery)

- Added Room migrations 6→7 (independent paid/delivered order flags) and 7→8 (wastes table); old transactions remain stored.
- Orders now show a details dialog with independently editable Paid and Delivered checkboxes. Copy-to-clipboard reflects both flags, and orders without a confirmed time sort last.
- Added a collapsible navigation rail with persisted light/dark preference, Wastes page, and Excel export entry.
- Wastes reduce stock and are recorded separately from sales and expenses; deleting an active-day waste restores stock.
- GitHub Actions `.github/workflows/build-apk.yml` is retained; upload this project to a GitHub repository and run **Build debug APK** from Actions.

### Known outstanding requirements / verification

This is **not a fully verified implementation** of the entire requested specification. In particular, branding/logo-based palette, swipe-left delivery gesture, full editing of existing order line items, comprehensive waste history/export integration, financial reconciliation improvements, and end-to-end Android build/device verification remain outstanding. Do not replace a production installation without backing up its data and validating the APK first. No Gradle/Android SDK toolchain was available in the authoring environment, so successful compilation is not asserted.


## v1.8.6 changes
- Customers sidebar: saved customer profiles, editable defaults, order-history cards with expandable transaction details. Existing orders are matched by customer name (case-insensitive), so changing a saved customer's name will not automatically relink older orders.
- New Order: starts-with autocomplete for saved customer names, default delivery address/fee and notes; optional save new customer. Pickup and delivery both have a preferred-time picker.
- Settings: visually separated category headings and a portable ZIP database backup/restore. The backup contains the SQLite snapshot, settings, and profile photo. Save it outside the phone. Restore replaces all local app data and closes the app; reopen afterward.
- Existing Room schema upgrades from v10 to v11 without dropping existing data.
