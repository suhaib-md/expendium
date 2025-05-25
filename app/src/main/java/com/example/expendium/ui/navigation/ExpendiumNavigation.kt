// ui/navigation/ExpendiumNavigation.kt
package com.example.expendium.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expendium.ui.screen.AddEditAccountScreen // Import new screen
import com.example.expendium.ui.screen.AccountListScreen    // Import new screen
import com.example.expendium.ui.screen.AddEditCategoryScreen
import com.example.expendium.ui.screen.AddTransactionScreen
// import com.example.expendium.ui.screen.CategoriesScreen // Keep if used for a dedicated category list
import com.example.expendium.ui.screen.SettingsScreen
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
    const val CATEGORIES_ROUTE = "categories" // For listing categories
    const val ADD_EDIT_CATEGORY_BASE_ROUTE = "add_edit_category"
    const val CATEGORY_ID_ARG_KEY = "categoryId"
    const val OPTIONAL_CATEGORY_ID_ARG = "?$CATEGORY_ID_ARG_KEY={$CATEGORY_ID_ARG_KEY}"
    const val ADD_EDIT_CATEGORY_ROUTE = "$ADD_EDIT_CATEGORY_BASE_ROUTE$OPTIONAL_CATEGORY_ID_ARG"

    // Settings Route
    const val SETTINGS_ROUTE = "settings"

    // Account Routes - NEW
    const val ACCOUNT_LIST_ROUTE = "accounts"
    const val ADD_EDIT_ACCOUNT_BASE_ROUTE = "add_edit_account"
    const val ACCOUNT_ID_ARG_KEY = "accountId"
    const val OPTIONAL_ACCOUNT_ID_ARG = "?$ACCOUNT_ID_ARG_KEY={$ACCOUNT_ID_ARG_KEY}"
    const val ADD_EDIT_ACCOUNT_ROUTE = "$ADD_EDIT_ACCOUNT_BASE_ROUTE$OPTIONAL_ACCOUNT_ID_ARG"
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

fun NavController.navigateToCategories() {
    this.navigate(AppDestinations.CATEGORIES_ROUTE)
}

fun NavController.navigateToAddCategory() {
    this.navigate(AppDestinations.ADD_EDIT_CATEGORY_BASE_ROUTE)
}

fun NavController.navigateToEditCategory(categoryId: Long) {
    this.navigate("${AppDestinations.ADD_EDIT_CATEGORY_BASE_ROUTE}?${AppDestinations.CATEGORY_ID_ARG_KEY}=$categoryId")
}

fun NavController.navigateToSettings() {
    this.navigate(AppDestinations.SETTINGS_ROUTE)
}

// Account Navigation Helpers - NEW
fun NavController.navigateToAccountList() {
    this.navigate(AppDestinations.ACCOUNT_LIST_ROUTE)
}

fun NavController.navigateToAddAccount() {
    this.navigate(AppDestinations.ADD_EDIT_ACCOUNT_BASE_ROUTE)
}

fun NavController.navigateToEditAccount(accountId: Long) {
    this.navigate("${AppDestinations.ADD_EDIT_ACCOUNT_BASE_ROUTE}?${AppDestinations.ACCOUNT_ID_ARG_KEY}=$accountId")
}


@Composable
fun ExpendiumNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN_ROUTE, // Or your preferred start destination
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN_ROUTE) {
            MainScreen(navController = navController)
        }

        composable(
            route = AppDestinations.ADD_EDIT_TRANSACTION_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L // Convention for new item
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY)
            // Pass null if -1L, otherwise the ID
            AddTransactionScreen(
                navController = navController,
                transactionId = if (transactionId == -1L) null else transactionId
            )
        }

        composable(
            route = AppDestinations.TRANSACTION_DETAIL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
                // No defaultValue, ID is required for details
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY)
            if (transactionId != null && transactionId != -1L) { // Ensure valid ID
                TransactionDetailScreen(navController = navController, transactionId = transactionId)
            } else {
                // Handle invalid ID, e.g., pop back or navigate to an error screen
                navController.popBackStack()
            }
        }

        composable(AppDestinations.CATEGORIES_ROUTE) {
            // Assuming you have a CategoriesScreen for listing categories
            // If not, and categories are only managed within AddEditCategoryScreen, you might not need this route.
            // CategoriesScreen(navController = navController)
            // For now, let's assume SettingsScreen might be a place to navigate to Category management.
            // If you create a dedicated CategoriesScreen, uncomment and ensure it exists.
        }

        composable(
            route = AppDestinations.ADD_EDIT_CATEGORY_ROUTE,
            arguments = listOf(navArgument(AppDestinations.CATEGORY_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L // Convention for new item
            })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong(AppDestinations.CATEGORY_ID_ARG_KEY)
            AddEditCategoryScreen(
                navController = navController,
                categoryId = if (categoryId == -1L) null else categoryId
            )
        }

        composable(AppDestinations.SETTINGS_ROUTE) {
            SettingsScreen(navController = navController)
        }

        // Account Routes - NEW
        composable(AppDestinations.ACCOUNT_LIST_ROUTE) {
            AccountListScreen(navController = navController)
        }

        composable(
            route = AppDestinations.ADD_EDIT_ACCOUNT_ROUTE,
            arguments = listOf(navArgument(AppDestinations.ACCOUNT_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L // Convention for new account
            })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong(AppDestinations.ACCOUNT_ID_ARG_KEY)
            AddEditAccountScreen(
                navController = navController,
                accountId = if (accountId == -1L) null else accountId
            )
        }

        // composable("reports") {
        //     // Placeholder for Reports Screen
        // }
    }
}