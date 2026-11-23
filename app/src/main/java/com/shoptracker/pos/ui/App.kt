package com.shoptracker.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Products : Screen
    data object Orders : Screen
    data object NewOrder : Screen
    data class EditOrder(val id: Long) : Screen
    data object Customers : Screen
    data object Settings : Screen
    data object Expenses : Screen
    data object Wastes : Screen
    data object Export : Screen
    data object Rates : Screen
    data object History : Screen
    data class DayDetail(val dayId: Long) : Screen
    data object Chart : Screen
    data object Compare : Screen
    data object Profile : Screen
    data object NewDay : Screen
}

@Composable
fun App(vm: MainViewModel) {
    if (UserProfile.name.isBlank()) {
        CreateUserScreen(onCreated = {})
        return
    }
    val stack = remember { mutableStateListOf<Screen>(Screen.Main) }
    val screen = stack.last()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var sidebar by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        LocalThemePreference.dark = themePrefs.getBoolean("dark_mode", false)
    }

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = sidebar || stack.size > 1) { if (sidebar) sidebar = false else pop() }

    LaunchedEffect(Unit) {
        vm.events.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg, duration = SnackbarDuration.Short) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main content keeps its full width; the navigation drawer floats above it.
            Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.Main -> MainScreen(
                    vm = vm,
                    onProducts = { push(Screen.Products) },
                    onOrders = { push(Screen.Orders) },
                    onExpenses = { push(Screen.Expenses) },
                    onRates = { push(Screen.Rates) },
                    onHistory = { push(Screen.History) },
                    onNewOrder = { push(Screen.NewOrder) },
                    onNewDay = { push(Screen.NewDay) },
                    onChart = { push(Screen.Chart) },
                    onCompare = { push(Screen.Compare) },
                    onOpenDay = { push(Screen.DayDetail(it)) }
                )
                Screen.Profile -> CustomizeProfileScreen(onBack = { pop() })
                Screen.Products -> ProductsScreen(vm, onBack = { pop() })
                Screen.Orders -> OrdersScreen(vm, onBack = { pop() }, onNewOrder = { push(Screen.NewOrder) }, onEditOrder = { push(Screen.EditOrder(it)) })
                is Screen.EditOrder -> EditOrderScreen(vm, screen.id, onBack = { pop() })
                Screen.Customers -> CustomersScreen(vm, onBack = { pop() })
                Screen.Settings -> SettingsScreen(vm, onBack = { pop() })
                Screen.NewOrder -> NewOrderScreen(vm, onBack = { pop() }, onDone = { pop() })
                Screen.Expenses -> ExpensesScreen(vm, onBack = { pop() })
                Screen.Wastes -> WastesScreen(vm, onBack = { pop() })
                Screen.Export -> ExportScreen(vm, onBack = { pop() })
                Screen.Rates -> RatesScreen(vm, onBack = { pop() })
                Screen.History -> HistoryScreen(vm, onBack = { pop() }, onOpenDay = { push(Screen.DayDetail(it)) })
                is Screen.DayDetail -> DayDetailScreen(
                    vm = vm,
                    dayId = screen.dayId,
                    onBack = { pop() },
                    onCloseDay = { push(Screen.NewDay) },
                    onEditOrder = { push(Screen.EditOrder(it)) }
                )
                Screen.Chart -> ChartScreen(vm, onBack = { pop() })
                Screen.Compare -> CompareScreen(vm, onBack = { pop() })
                Screen.NewDay -> NewDayScreen(vm, onBack = { pop() }, onDone = { pop() })
            }
                // A small edge handle remains available without resizing the dashboard.
                if (!sidebar && screen == Screen.Main) {
                    androidx.compose.material3.Button(
                        onClick = { sidebar = true },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                            .padding(bottom = 100.dp)
                            .width(42.dp)
                            .height(52.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = 0.dp, bottomStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp
                        )
                    ) { Text("›", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge) }
                }
                if (sidebar && screen == Screen.Main) {
                    // Tap outside the panel to dismiss; no underlying dashboard interaction.
                    Box(
                        Modifier.fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.38f))
                            .clickable { sidebar = false }
                    )
                    Surface(
                        modifier = Modifier.fillMaxHeight().width(220.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topEnd = 20.dp, bottomEnd = 20.dp
                        ),
                        shadowElevation = 12.dp,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.material3.IconButton(
                                    onClick = { sidebar = false; push(Screen.Profile) },
                                    modifier = Modifier.weight(1f).height(78.dp)
                                ) {
                                    UserPhoto(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape))
                                }
                                androidx.compose.material3.IconButton(onClick = { sidebar = false }) {
                                    Text("‹", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            listOf(
                                "Dashboard" to Screen.Main, "Products" to Screen.Products,
                                "Orders" to Screen.Orders, "Customers" to Screen.Customers, "Expenses" to Screen.Expenses,
                                "Wastes" to Screen.Wastes, "Delivery Rates" to Screen.Rates,
                                "History" to Screen.History, "Chart" to Screen.Chart,
                                "Compare" to Screen.Compare, "Export" to Screen.Export,
                                "Settings" to Screen.Settings
                            ).forEach { (label, destination) ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        stack.clear()
                                        stack.add(Screen.Main)
                                        if (destination != Screen.Main) push(destination)
                                        sidebar = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .height(40.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(label, modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dark Mode")
                                Switch(checked = LocalThemePreference.dark, onCheckedChange = {
                                    LocalThemePreference.dark = it
                                    themePrefs.edit().putBoolean("dark_mode", it).apply()
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}