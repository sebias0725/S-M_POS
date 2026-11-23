package com.shoptracker.pos.ui

import com.shoptracker.pos.data.Category
import com.shoptracker.pos.data.Product

/** Category headers are sorted alphabetically; uncategorized items are shown last. */
internal fun groupedProducts(products: List<Product>, categories: List<Category>): List<Pair<String, List<Product>>> {
    val names = categories.associate { it.id to it.name }
    val groups = products.groupBy { names[it.categoryId] ?: UNCATEGORIZED_LABEL }
    return groups.entries.sortedWith(compareBy<Map.Entry<String, List<Product>>> { it.key == UNCATEGORIZED_LABEL }
        .thenBy { it.key.lowercase() }).map { it.key to it.value.sortedBy { p -> p.name.lowercase() } }
}
