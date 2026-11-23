# S&M POS — v1.9.0

- Independent Android application ID. Chanos retains the legacy `com.shoptracker.pos` ID for update compatibility.
- Fresh installs seed only the relevant business products. Existing installs are NOT automatically purged of the other business's products or transaction history.
- Product and category seed callback runs only when database is first created, not at every app start.
- Start day requests only opening float; cash opening amount mirrors the float.
- Order payment methods: Cash / QR, with paid or on-delivery/pickup status.
- Pickup time selection and customer/backup features carried forward from v1.8.6.

## Not yet completed in this build
- Payment reconciliation uses order Paid flags and treats counter sales as cash; older orders with blank/legacy payment methods may require manual correction.
- Full editing of historical day records (existing final-amount editing remains).

Do not uninstall the existing app before making a verified backup. Android build was not run here.
