package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.Category
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.util.Fmt

@Composable
fun ProductsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val lifetime by vm.lifetime.collectAsState()
    val day by vm.openDay.collectAsState()
    val data = rememberDayData(vm, setOfNotNull(day?.id))
    val todayById = remember(data) { data.stats.products.associateBy { it.productId } }

    var editing by remember { mutableStateOf<Product?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var managingCategories by remember { mutableStateOf(false) }

    val groups = remember(products, categories) { groupedProducts(products, categories) }
    val showHeaders = categories.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Products", onBack) {
            TextButton(onClick = { managingCategories = true }) { Text("CATEGORIES") }
        }
        Box(Modifier.weight(1f)) {
            if (products.isEmpty()) {
                EmptyState(
                    "No products yet",
                    "Add the items you sell. They will appear as big tap buttons on the main screen.",
                    Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { (groupName, groupProducts) ->
                    if (showHeaders) {
                        item(key = "header:$groupName") {
                            Text(
                                "$groupName (${groupProducts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    items(groupProducts, key = { it.id }) { p ->
                        val today = todayById[p.id]
                        val life = lifetime[p.id]
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Price ${Fmt.peso(p.priceCentavos)}  •  Profit ${Fmt.peso(p.profitCentavos)} each",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Stocks: ${p.stock}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (p.stock <= 0) negativeColor() else androidx.compose.ui.graphics.Color.Unspecified
                                        )
                                    }
                                    Switch(checked = p.active, onCheckedChange = { vm.updateProduct(p.copy(active = it)) })
                                }
                                Text(
                                    "Today: ${today?.units ?: 0} sold • ${Fmt.peso(today?.revenue ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "All time: ${life?.units ?: 0} sold • ${Fmt.peso(life?.revenue ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (p.active) "Active" else "Disabled (hidden from POS)",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("ADD PRODUCT", fontWeight = FontWeight.Bold) }
    }

    if (adding) {
        ProductDialog(
            initial = null,
            categories = categories,
            onDismiss = { adding = false },
            onSave = { name, price, profit, stock, categoryId ->
                vm.addProduct(name, price, profit, stock, categoryId)
                adding = false
            }
        )
    }
    editing?.let { p ->
        ProductDialog(
            initial = p,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { name, price, profit, stock, categoryId ->
                vm.updateProduct(
                    p.copy(
                        name = name.trim(),
                        priceCentavos = price,
                        profitCentavos = profit,
                        stock = stock,
                        categoryId = categoryId
                    )
                )
                editing = null
            }
        )
    }
    if (managingCategories) {
        CategoriesDialog(
            vm = vm,
            categories = categories,
            products = products,
            onDismiss = { managingCategories = false }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = {
                Text(
                    "The product will be removed from the app. Past sales stay in your records. " +
                        "If you only want to hide it, use the switch to disable it instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProduct(p)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProductDialog(
    initial: Product?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, Int, Long?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(if (initial != null) Fmt.plain(initial.priceCentavos) else "") }
    var profit by remember { mutableStateOf(if (initial != null) Fmt.plain(initial.profitCentavos) else "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var showErrors by remember { mutableStateOf(false) }

    val priceCentavos = Fmt.parsePeso(price)
    val profitCentavos = if (profit.isBlank()) 0L else Fmt.parsePeso(profit)
    val stockN = if (stock.isBlank()) 0 else stock.toIntOrNull()

    val nameError = if (showErrors && name.isBlank()) "Enter a name" else null
    val priceError = if (showErrors && (priceCentavos == null || priceCentavos <= 0L)) "Enter a price above 0" else null
    val profitError = if (showErrors && profitCentavos == null) "Enter a valid amount" else null
    val stockError = if (showErrors && stockN == null) "Enter a whole number" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add product" else "Edit product") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Product name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = if (nameError != null) ({ Text(nameError) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryDropdown(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it }
                )
                MoneyField(price, { price = it }, "Selling price", error = priceError)
                MoneyField(profit, { profit = it }, "Profit per unit", error = profitError)
                NumberField(
                    stock, { stock = it }, "Stocks (units remaining)",
                    error = stockError,
                    hint = "Goes down with each sale, back up on undo / remove"
                )
                if (initial != null) {
                    Text(
                        "Changing the price or profit only affects future sales. Past sales keep their original values.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || priceCentavos == null || priceCentavos <= 0L ||
                    profitCentavos == null || stockN == null
                ) {
                    showErrors = true
                } else {
                    onSave(
                        name, priceCentavos, profitCentavos, stockN,
                        categoryId?.takeIf { id -> categories.any { it.id == id } }
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
