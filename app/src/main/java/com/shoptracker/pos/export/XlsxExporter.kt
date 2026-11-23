package com.shoptracker.pos.export

import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.Expense
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.util.Fmt
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal dependency-free .xlsx writer (SpreadsheetML via zip). Produces headers, currency
 * formatting (₱), percent formatting, frozen header row and column widths.
 */
object XlsxExporter {

    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_CURRENCY = 2
    private const val S_PERCENT = 3
    private const val S_INT = 4

    private sealed class Cell {
        data class Text(val value: String, val style: Int = S_DEFAULT) : Cell()
        data class Num(val plain: String, val style: Int) : Cell()
        data object Empty : Cell()
    }

    private class Sheet(val name: String, val widths: List<Double>, val rows: List<List<Cell>>)

    fun write(
        out: OutputStream,
        days: List<BusinessDay>,
        txns: List<SalesTransaction>,
        orders: List<SalesOrder>,
        expenses: List<Expense>
    ) {
        val dayById = days.associateBy { it.id }
        val sheets = listOf(
            summarySheet(days, txns, orders, expenses),
            productSheet(dayById, txns),
            transactionSheet(dayById, txns),
            orderSheet(dayById, txns, orders),
            expenseSheet(dayById, expenses)
        )
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(sheets.size))
            put(zip, "_rels/.rels", ROOT_RELS)
            put(zip, "xl/workbook.xml", workbook(sheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put(zip, "xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put(zip, "xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    // ---------- sheets ----------

    private fun header(vararg names: String): List<Cell> = names.map { Cell.Text(it, S_HEADER) }

    private fun money(c: Long?): Cell = if (c == null) Cell.Empty else Cell.Num(Fmt.plain(c), S_CURRENCY)

    private fun dateOf(day: BusinessDay?): String = if (day != null) Fmt.isoDate(day.openedAt) else ""

    private fun summarySheet(
        days: List<BusinessDay>,
        txns: List<SalesTransaction>,
        orders: List<SalesOrder>,
        expenses: List<Expense>
    ): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += header(
            "Business Date", "Total Units Sold", "Product Sales", "Delivery Fees", "Total Gross Sales",
            "Profit (products)", "Total Expenses", "Net (profit + delivery fees - expenses)",
            "Opening Floating Money", "Opening Cash on Hand", "Final Floating Money", "Final Cash on Hand",
            "Target Sales (units)", "Target Achievement %", "Opened At", "Closed At", "Remit"
        )
        for (d in days) {
            val stats = computeStats(
                txns.filter { it.businessDayId == d.id },
                orders.filter { it.businessDayId == d.id }
            )
            val expenseTotal = expenses.filter { it.businessDayId == d.id }.sumOf { it.amountCentavos }
            val net = stats.profit + stats.deliveryFees - expenseTotal
            val frac = Fmt.targetFraction(stats.units, d.targetUnits)
            rows += listOf(
                Cell.Text(Fmt.isoDate(d.openedAt)),
                Cell.Num(stats.units.toString(), S_INT),
                money(stats.productSales),
                money(stats.deliveryFees),
                money(stats.gross),
                money(stats.profit),
                money(expenseTotal),
                money(net),
                money(d.openingFloatCentavos),
                money(d.openingCashCentavos),
                money(d.finalFloatCentavos),
                money(d.finalCashCentavos),
                Cell.Num(d.targetUnits.toString(), S_INT),
                if (frac == null) Cell.Empty else Cell.Num(frac, S_PERCENT),
                Cell.Text(Fmt.isoDateTime(d.openedAt)),
                if (d.closedAt == null) Cell.Text("Still open") else Cell.Text(Fmt.isoDateTime(d.closedAt)),
                if (d.remitCentavos == null) Cell.Empty else money(d.remitCentavos)
            )
        }
        return Sheet(
            "Daily Summary",
            listOf(15.0, 16.0, 16.0, 15.0, 18.0, 17.0, 16.0, 26.0, 22.0, 20.0, 20.0, 19.0, 18.0, 20.0, 20.0, 20.0, 14.0),
            rows
        )
    }

    private fun productSheet(dayById: Map<Long, BusinessDay>, txns: List<SalesTransaction>): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += header("Business Date", "Product Name", "Selling Price", "Quantity Sold", "Total Revenue", "Total Profit")
        val grouped = txns.filter { it.status == STATUS_COMPLETED }
            .groupBy { Triple(it.businessDayId, it.productName, it.unitPriceCentavos) }
        val keys = grouped.keys.sortedWith(
            compareBy<Triple<Long, String, Long>> { it.first }.thenBy { it.second.lowercase() }.thenBy { it.third }
        )
        for (k in keys) {
            val list = grouped.getValue(k)
            rows += listOf(
                Cell.Text(dateOf(dayById[k.first])),
                Cell.Text(k.second),
                money(k.third),
                Cell.Num(list.sumOf { it.quantity }.toString(), S_INT),
                money(list.sumOf { it.totalCentavos }),
                money(list.sumOf { it.unitProfitCentavos * it.quantity })
            )
        }
        return Sheet("Product Sales", listOf(15.0, 30.0, 16.0, 16.0, 18.0, 16.0), rows)
    }

    private fun transactionSheet(dayById: Map<Long, BusinessDay>, txns: List<SalesTransaction>): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += header(
            "Transaction ID", "Date", "Time", "Product Name", "Quantity", "Unit Price",
            "Total Amount", "Status", "Business Date", "Order ID"
        )
        for (t in txns) {
            val orderId = t.orderId
            rows += listOf(
                Cell.Num(t.id.toString(), S_INT),
                Cell.Text(Fmt.isoDate(t.timestamp)),
                Cell.Text(Fmt.isoTime(t.timestamp)),
                Cell.Text(t.productName),
                Cell.Num(t.quantity.toString(), S_INT),
                money(t.unitPriceCentavos),
                money(t.totalCentavos),
                Cell.Text(if (t.status == STATUS_COMPLETED) "Completed" else "Undone"),
                Cell.Text(dateOf(dayById[t.businessDayId])),
                if (orderId == null) Cell.Empty else Cell.Num(orderId.toString(), S_INT)
            )
        }
        return Sheet("Transaction History", listOf(15.0, 13.0, 11.0, 30.0, 10.0, 14.0, 16.0, 12.0, 15.0, 10.0), rows)
    }

    private fun orderSheet(
        dayById: Map<Long, BusinessDay>,
        txns: List<SalesTransaction>,
        orders: List<SalesOrder>
    ): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += header(
            "Order ID", "Business Date", "Date", "Time", "Customer", "Type", "Address / Area",
            "Items", "Items Total", "Delivery Fee", "Order Total", "Status", "Note",
            "Payment Method", "Delivery Time"
        )
        val byOrder = txns.filter { it.orderId != null }.groupBy { it.orderId }
        for (o in orders) {
            val lines = byOrder[o.id] ?: emptyList()
            val itemsTotal = lines.sumOf { it.totalCentavos }
            val itemsText = lines.joinToString("; ") { "${it.quantity} x ${it.productName}" }
            rows += listOf(
                Cell.Num(o.id.toString(), S_INT),
                Cell.Text(dateOf(dayById[o.businessDayId])),
                Cell.Text(Fmt.isoDate(o.createdAt)),
                Cell.Text(Fmt.isoTime(o.createdAt)),
                Cell.Text(o.customerName),
                Cell.Text(if (o.type == ORDER_DELIVERY) "Delivery" else "Pickup"),
                Cell.Text(o.address),
                Cell.Text(itemsText),
                money(itemsTotal),
                money(o.deliveryFeeCentavos),
                money(itemsTotal + o.deliveryFeeCentavos),
                Cell.Text(if (o.status == STATUS_COMPLETED) "Completed" else "Cancelled"),
                Cell.Text(o.note),
                Cell.Text(o.paymentMethod),
                Cell.Text(o.deliveryTime)
            )
        }
        return Sheet("Orders", listOf(10.0, 15.0, 13.0, 11.0, 22.0, 11.0, 28.0, 40.0, 14.0, 14.0, 15.0, 12.0, 30.0, 16.0, 22.0), rows)
    }

    private fun expenseSheet(dayById: Map<Long, BusinessDay>, expenses: List<Expense>): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += header("Business Date", "Date", "Time", "Description", "Amount")
        for (e in expenses) {
            rows += listOf(
                Cell.Text(dateOf(dayById[e.businessDayId])),
                Cell.Text(Fmt.isoDate(e.createdAt)),
                Cell.Text(Fmt.isoTime(e.createdAt)),
                Cell.Text(e.description),
                money(e.amountCentavos)
            )
        }
        return Sheet("Expenses", listOf(15.0, 13.0, 11.0, 40.0, 16.0), rows)
    }

    // ---------- xml ----------

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> Unit // invalid in XML 1.0
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + n % 26).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews>")
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        sb.append("<cols>")
        sheet.widths.forEachIndexed { i, w ->
            sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 1
            if (rowNum == 1) sb.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">") else sb.append("<row r=\"$rowNum\">")
            row.forEachIndexed { c, cell ->
                val ref = colName(c) + rowNum
                when (cell) {
                    is Cell.Text ->
                        sb.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(cell.value)}</t></is></c>")
                    is Cell.Num ->
                        sb.append("<c r=\"$ref\" s=\"${cell.style}\"><v>${cell.plain}</v></c>")
                    Cell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheetCount) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun workbook(sheets: List<Sheet>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
        sheets.forEachIndexed { i, s ->
            sb.append("<sheet name=\"${esc(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            sb.append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        sb.append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    private const val ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;₱&quot;#,##0.00\"/></numFmts>" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"3\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF00796B\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>" +
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"10\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
}
