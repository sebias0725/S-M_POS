# Historical day corrections — v1.9.1

History > select a closed day now provides:
- Edit business date, opening float, target units, and remittance.
- Add/edit/delete expenses.
- Edit order customer, type, location, delivery fee, payment method/status, time, notes, quantities and historical unit prices; cancel/delete historical orders.
- Correct or reverse individual non-order sales.
- Edit/delete historical waste records.
- Recalculate closing cash from opening float + received cash - expenses, and ending float after remittance.

Inventory corrections are transactional and reject insufficient stock. Historical sales and order lines retain their original business day. Existing data is preserved (no destructive schema migration).

## Important
The historical editor is source-level only and has NOT been Android-compiled or tested on a device in this environment. Back up production data before installing. Historic payments recorded on a different day are not represented as separate payment events by the current database, so the calculation treats paid orders as received on their order day. The database also does not yet record an audit trail of individual edits. Historical business-date edits update the day date, not the original order timestamps.
