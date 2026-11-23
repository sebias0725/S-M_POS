package com.shoptracker.pos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.DayData

/** Everything recorded for the given business days, kept live as sales/orders/expenses change. */
@Composable
fun rememberDayData(vm: MainViewModel, dayIds: Set<Long>): DayData {
    val txns by vm.allTxns.collectAsState()
    val orders by vm.allOrders.collectAsState()
    val expenses by vm.allExpenses.collectAsState()
    return remember(dayIds, txns, orders, expenses) {
        DayData(
            txns = txns.filter { it.businessDayId in dayIds },
            orders = orders.filter { it.businessDayId in dayIds },
            expenses = expenses.filter { it.businessDayId in dayIds }
        )
    }
}
