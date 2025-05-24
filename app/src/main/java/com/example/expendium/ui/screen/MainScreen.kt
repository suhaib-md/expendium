// ui/screen/MainScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue // Keep this
import androidx.compose.runtime.setValue // Keep this
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.ui.navigation.AppDestinations
import com.example.expendium.ui.navigation.navigateToSettings // Import the helper
import com.example.expendium.ui.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    transactionViewModel: TransactionViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Define titles for comparison or direct use
    val transactionsTabTitle = "Transactions"
    val reportsTabTitle = "Reports"
    val categoriesTabTitle = "Categories"
    val settingsTabTitle = "Settings"

    val navigationItems = listOf(
        NavigationItem(transactionsTabTitle, Icons.Filled.List),
        NavigationItem(reportsTabTitle, Icons.Filled.Analytics),
        NavigationItem(categoriesTabTitle, Icons.Filled.Category),
        NavigationItem(settingsTabTitle, Icons.Filled.Settings) // This is your Settings tab
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = {
                            // Check if the clicked tab is the Settings tab
                            if (item.title == settingsTabTitle) {
                                // Navigate to the SettingsScreen defined in your NavHost
                                navController.navigateToSettings() // Or navController.navigate(AppDestinations.SETTINGS_ROUTE)
                                // Optionally, you might want to keep selectedTab updated if
                                // the NavHost for settings is outside this MainScreen's direct content area
                                // or if you want the tab to appear selected after navigating.
                                // For a simple navigation away, just navigating is enough.
                                // If SETTINGS_ROUTE is a different screen outside this Scaffold's body,
                                // the selectedTab state here might become out of sync or irrelevant
                                // once you navigate away. Consider if selectedTab should be reset or handled
                                // differently when navigating to a full new screen vs. swapping content in the Box.
                            } else {
                                selectedTab = index
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // Show FAB only on transactions tab (selectedTab == 0, assuming "Transactions" is index 0)
            if (selectedTab == 0 && navigationItems[selectedTab].title == transactionsTabTitle) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(AppDestinations.ADD_TRANSACTION_BASE_ROUTE)
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Display content based on selectedTab, but Settings is handled by navigation now
            // The content for other tabs remains within this MainScreen's Box.
            // When navigating to Settings, this Box's content will be replaced by
            // what the NavHost displays for the SETTINGS_ROUTE.
            when (navigationItems.getOrNull(selectedTab)?.title) {
                transactionsTabTitle -> TransactionListScreen(
                    viewModel = transactionViewModel,
                    navController = navController,
                    // Pass the onNavigateToSettings callback if TransactionListScreen needs it
                    // For instance, if TransactionListScreen had its own settings icon/button
                    onNavigateToSettings = { navController.navigateToSettings() }
                )
                reportsTabTitle -> ReportsScreen() // Your existing ReportsScreen
                categoriesTabTitle -> CategoriesScreen(navController = navController) // Your existing CategoriesScreen
                // The Settings tab now navigates, so it won't typically show content here
                // unless you have a specific design where it shows something *before* full navigation,
                // or if settings was a composable swapped here.
                // Given the navigation setup, the SETTINGS_ROUTE will take over.
                // So, we don't need a case for settingsTabTitle here to display content in this Box.
            }
        }
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)

// This ReportsScreen is fine as a placeholder within MainScreen's content area
@Composable
fun ReportsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Reports Screen - Coming Soon!")
    }
}

