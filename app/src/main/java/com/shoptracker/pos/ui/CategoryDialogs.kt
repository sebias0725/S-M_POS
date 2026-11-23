package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.CATEGORY_NAME_MAX
import com.shoptracker.pos.data.Category
import com.shoptracker.pos.data.CategoryResult
import com.shoptracker.pos.data.CategoryTarget
import com.shoptracker.pos.data.Product

const val UNCATEGORIZED_LABEL = "Uncategorized"

/** Read-only field that opens a menu of categories. Selected = null means Uncategorized. */
@Composable
fun CategoryDropdown(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category"
) {
    var open by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: UNCATEGORIZED_LABEL
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Text("▾", style = MaterialTheme.typography.titleMedium) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay so a tap anywhere on the field opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(UNCATEGORIZED_LABEL) },
                onClick = { onSelect(null); open = false }
            )
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    onClick = { onSelect(c.id); open = false }
                )
            }
        }
    }
}

/** Manage categories: add, rename, delete. */
@Composable
fun CategoriesDialog(
    vm: MainViewModel,
    categories: List<Category>,
    products: List<Product>,
    onDismiss: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (categories.isEmpty()) {
                    Text(
                        "No categories yet. Add one, then pick it when you create or edit a product.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                categories.forEachIndexed { i, c ->
                    val count = products.count { it.categoryId == c.id }
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "$count product${if (count == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { renaming = c }) { Icon(Icons.Default.Edit, "Edit category") }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Delete, "Delete category") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { adding = true }) { Text("Add category") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (adding) {
        CategoryNameDialog(
            title = "New category",
            initial = "",
            onDismiss = { adding = false },
            onSubmit = { name, done -> vm.addCategory(name, done) }
        ) { adding = false }
    }
    renaming?.let { c ->
        CategoryNameDialog(
            title = "Edit category",
            initial = c.name,
            onDismiss = { renaming = null },
            onSubmit = { name, done -> vm.renameCategory(c.id, name, done) }
        ) { renaming = null }
    }
    deleting?.let { c ->
        val count = products.count { it.categoryId == c.id }
        DeleteCategoryDialog(
            category = c,
            productCount = count,
            otherCategories = categories.filter { it.id != c.id },
            onDismiss = { deleting = null },
            onConfirm = { target, done -> vm.deleteCategory(c.id, target, done) },
            onFinished = { deleting = null }
        )
    }
}

/** Name entry used for both creating and renaming. Stays open and shows the error if rejected. */
@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (String, (CategoryResult) -> Unit) -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= CATEGORY_NAME_MAX) { name = it; error = null } },
                label = { Text("Category name") },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) ({ Text(error!!) }) else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    onSubmit(name) { result ->
                        busy = false
                        when (result) {
                            CategoryResult.Ok -> onSaved()
                            is CategoryResult.Error -> error = result.message
                        }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Empty category: a plain confirmation.
 * Category with products: deletion is blocked until the user picks where the products go; the
 * products are moved, never deleted.
 */
@Composable
private fun DeleteCategoryDialog(
    category: Category,
    productCount: Int,
    otherCategories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (CategoryTarget?, (CategoryResult) -> Unit) -> Unit,
    onFinished: () -> Unit
) {
    // null = nothing chosen yet (only matters when the category has products)
    var target by remember { mutableStateOf<CategoryTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val targetLabel = when (val t = target) {
        null -> "Choose where to move them…"
        CategoryTarget.Uncategorized -> UNCATEGORIZED_LABEL
        is CategoryTarget.To -> otherCategories.firstOrNull { it.id == t.categoryId }?.name ?: UNCATEGORIZED_LABEL
    }
    val canDelete = productCount == 0 || target != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (productCount == 0) "Delete ${category.name}?" else "${category.name} has products") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (productCount == 0) {
                    Text("This category is empty. Deleting it does not affect any product.")
                } else {
                    Text(
                        "$productCount product${if (productCount == 1) " is" else "s are"} in this category. " +
                            "Move ${if (productCount == 1) "it" else "them"} to another category before deleting. " +
                            "No products will be deleted."
                    )
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(targetLabel)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(UNCATEGORIZED_LABEL) },
                                onClick = { target = CategoryTarget.Uncategorized; menuOpen = false }
                            )
                            otherCategories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = { target = CategoryTarget.To(c.id); menuOpen = false }
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canDelete && !busy,
                onClick = {
                    busy = true
                    // No target when empty: if a product appeared meanwhile, the ViewModel refuses the delete.
                    onConfirm(if (productCount == 0) null else target) { result ->
                        busy = false
                        when (result) {
                            CategoryResult.Ok -> onFinished()
                            is CategoryResult.Error -> error = result.message
                        }
                    }
                }
            ) { Text(if (productCount == 0) "Delete" else "Move & delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
