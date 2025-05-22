// ui/screen/MainScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expendium.ui.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    transactionViewModel: TransactionViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val navigationItems = listOf(
        NavigationItem("Transactions", Icons.Filled.List),
        NavigationItem("Reports", Icons.Filled.Analytics),
        NavigationItem("Categories", Icons.Filled.Category),
        NavigationItem("Settings", Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) { // Show FAB only on transactions tab
                FloatingActionButton(
                    onClick = {
                        // TODO: Navigate to add transaction screen
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> TransactionListScreen(viewModel = transactionViewModel)
                1 -> ReportsScreen()
                2 -> CategoriesScreen()
                3 -> SettingsScreen()
            }
        }
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun ReportsScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Reports Screen - Coming Soon!")
    }
}

@Composable
fun CategoriesScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Categories Screen - Coming Soon!")
    }
}

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Settings Screen - Coming Soon!")
    }
}