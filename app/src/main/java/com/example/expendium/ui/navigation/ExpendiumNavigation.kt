// ui/navigation/ExpendiumNavigation.kt
package com.example.expendium.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.expendium.ui.screen.AddTransactionScreen // <-- Add this import
import com.example.expendium.ui.screen.MainScreen

object AppDestinations {
    const val MAIN_ROUTE = "main"
    const val ADD_TRANSACTION_ROUTE = "add_transaction"
    // TODO: Define other routes as constants
    // const val TRANSACTION_DETAIL_ROUTE = "transaction_detail/{transactionId}"
    // const val CATEGORIES_ROUTE = "categories"
    // const val REPORTS_ROUTE = "reports"
    // const val SETTINGS_ROUTE = "settings"
}

@Composable
fun ExpendiumNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN_ROUTE, // Use the constant
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN_ROUTE) {
            MainScreen(navController = navController) // Pass navController to MainScreen
        }

        composable(AppDestinations.ADD_TRANSACTION_ROUTE) { // Use the constant
            AddTransactionScreen(navController = navController) // Pass navController
        }

        // composable("transaction_detail/{transactionId}") { backStackEntry ->
        //     // Will implement in next phase
        // }

        // composable("categories") {
        //     // Will implement in next phase
        // }

        // composable("reports") {
        //     // Will implement in next phase
        // }

        // composable("settings") {
        //     // Will implement in next phase
        // }
    }
}