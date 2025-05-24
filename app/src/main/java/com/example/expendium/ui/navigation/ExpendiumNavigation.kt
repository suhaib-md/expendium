// ui/navigation/ExpendiumNavigation.kt
package com.example.expendium.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings // For Settings Icon (Optional usage here)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector // For Screen class if using icons
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expendium.ui.screen.AddEditCategoryScreen
import com.example.expendium.ui.screen.AddTransactionScreen
import com.example.expendium.ui.screen.CategoriesScreen // Keep if used
import com.example.expendium.ui.screen.SettingsScreen // Ensure this import is present
import com.example.expendium.ui.screen.MainScreen
import com.example.expendium.ui.screen.TransactionDetailScreen


object AppDestinations {
    const val MAIN_ROUTE = "main"

    // Transaction Routes
    const val ADD_TRANSACTION_BASE_ROUTE = "add_transaction"
    const val TRANSACTION_ID_ARG_KEY = "transactionId"
    const val OPTIONAL_TRANSACTION_ID_ARG = "?$TRANSACTION_ID_ARG_KEY={$TRANSACTION_ID_ARG_KEY}"
    const val REQUIRED_TRANSACTION_ID_ARG = "/{$TRANSACTION_ID_ARG_KEY}"

    const val ADD_EDIT_TRANSACTION_ROUTE = "$ADD_TRANSACTION_BASE_ROUTE$OPTIONAL_TRANSACTION_ID_ARG"
    const val TRANSACTION_DETAIL_BASE_ROUTE = "transaction_detail"
    const val TRANSACTION_DETAIL_ROUTE = "$TRANSACTION_DETAIL_BASE_ROUTE$REQUIRED_TRANSACTION_ID_ARG"

    // Category Routes
    const val CATEGORIES_ROUTE = "categories"
    const val ADD_EDIT_CATEGORY_BASE_ROUTE = "add_edit_category"
    const val CATEGORY_ID_ARG_KEY = "categoryId"
    const val OPTIONAL_CATEGORY_ID_ARG = "?$CATEGORY_ID_ARG_KEY={$CATEGORY_ID_ARG_KEY}"
    const val ADD_EDIT_CATEGORY_ROUTE = "$ADD_EDIT_CATEGORY_BASE_ROUTE$OPTIONAL_CATEGORY_ID_ARG"

    // Settings Route
    const val SETTINGS_ROUTE = "settings" // Added Settings Route
}

// Navigation Helper Extension Functions

fun NavController.navigateToAddTransaction() {
    this.navigate(AppDestinations.ADD_TRANSACTION_BASE_ROUTE)
}

fun NavController.navigateToEditTransaction(transactionId: Long) {
    this.navigate("${AppDestinations.ADD_TRANSACTION_BASE_ROUTE}?${AppDestinations.TRANSACTION_ID_ARG_KEY}=$transactionId")
}

fun NavController.navigateToTransactionDetail(transactionId: Long) {
    this.navigate("${AppDestinations.TRANSACTION_DETAIL_BASE_ROUTE}/$transactionId")
}

fun NavController.navigateToAddCategory() {
    this.navigate(AppDestinations.ADD_EDIT_CATEGORY_BASE_ROUTE)
}

fun NavController.navigateToEditCategory(categoryId: Long) {
    this.navigate("${AppDestinations.ADD_EDIT_CATEGORY_BASE_ROUTE}?${AppDestinations.CATEGORY_ID_ARG_KEY}=$categoryId")
}

fun NavController.navigateToSettings() { // Added helper for Settings
    this.navigate(AppDestinations.SETTINGS_ROUTE)
}


@Composable
fun ExpendiumNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN_ROUTE,
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN_ROUTE) {
            // MainScreen now needs to know how to navigate to settings.
            // If settings is a top-level item accessible from MainScreen's own UI (e.g. bottom bar has settings tab)
            // then MainScreen will handle it.
            // If settings is accessed from a screen within MainScreen (like TransactionListScreen's TopAppBar),
            // then that specific screen needs the navigation callback.
            MainScreen(navController = navController)
        }

        composable(
            route = AppDestinations.ADD_EDIT_TRANSACTION_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY) ?: -1L
            AddTransactionScreen(navController = navController, transactionId = transactionId)
        }

        composable(
            route = AppDestinations.ADD_EDIT_CATEGORY_ROUTE,
            arguments = listOf(navArgument(AppDestinations.CATEGORY_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong(AppDestinations.CATEGORY_ID_ARG_KEY) ?: -1L
            AddEditCategoryScreen(navController = navController, categoryId = categoryId)
        }

        composable(
            route = AppDestinations.TRANSACTION_DETAIL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY)
            if (transactionId != null && transactionId != -1L) {
                TransactionDetailScreen(navController = navController, transactionId = transactionId)
            } else {
                navController.popBackStack()
            }
        }

        // Optional: Route for listing categories if you have a dedicated screen for it
        // composable(AppDestinations.CATEGORIES_ROUTE) {
        //     CategoriesScreen(navController = navController)
        // }

        // Added Composable for SettingsScreen
        composable(AppDestinations.SETTINGS_ROUTE) {
            SettingsScreen(navController = navController)
        }

        // composable("reports") {
        //     // Placeholder for Reports Screen
        // }
    }
}