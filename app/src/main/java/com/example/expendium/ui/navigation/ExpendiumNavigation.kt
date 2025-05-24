// ui/navigation/ExpendiumNavigation.kt
package com.example.expendium.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController // Added for extension functions
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expendium.ui.screen.AddEditCategoryScreen
import com.example.expendium.ui.screen.AddTransactionScreen
// Make sure CategoriesScreen is used or remove if not.
// import com.example.expendium.ui.screen.CategoriesScreen
import com.example.expendium.ui.screen.MainScreen
import com.example.expendium.ui.screen.TransactionDetailScreen


object AppDestinations {
    const val MAIN_ROUTE = "main"

    // Transaction Routes
    const val ADD_TRANSACTION_BASE_ROUTE = "add_transaction" // Base for add/edit
    const val TRANSACTION_ID_ARG_KEY = "transactionId"
    const val OPTIONAL_TRANSACTION_ID_ARG = "?$TRANSACTION_ID_ARG_KEY={$TRANSACTION_ID_ARG_KEY}" // For optional ID (add/edit)
    const val REQUIRED_TRANSACTION_ID_ARG = "/{$TRANSACTION_ID_ARG_KEY}" // For required ID (detail)

    const val ADD_EDIT_TRANSACTION_ROUTE = "$ADD_TRANSACTION_BASE_ROUTE$OPTIONAL_TRANSACTION_ID_ARG"
    const val TRANSACTION_DETAIL_BASE_ROUTE = "transaction_detail"
    const val TRANSACTION_DETAIL_ROUTE = "$TRANSACTION_DETAIL_BASE_ROUTE$REQUIRED_TRANSACTION_ID_ARG"


    // Category Routes (assuming similar pattern)
    const val CATEGORIES_ROUTE = "categories" // List of categories, if you have a dedicated screen
    const val ADD_EDIT_CATEGORY_BASE_ROUTE = "add_edit_category"
    const val CATEGORY_ID_ARG_KEY = "categoryId"
    const val OPTIONAL_CATEGORY_ID_ARG = "?$CATEGORY_ID_ARG_KEY={$CATEGORY_ID_ARG_KEY}"

    const val ADD_EDIT_CATEGORY_ROUTE = "$ADD_EDIT_CATEGORY_BASE_ROUTE$OPTIONAL_CATEGORY_ID_ARG"

    // TODO: Define other routes like reports, settings
}

// Navigation Helper Extension Functions (good practice)

fun NavController.navigateToAddTransaction() {
    this.navigate(AppDestinations.ADD_TRANSACTION_BASE_ROUTE) // Navigates to add (no ID)
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
            MainScreen(navController = navController)
        }

        composable(
            route = AppDestinations.ADD_EDIT_TRANSACTION_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L // Indicates adding a new transaction if ID is not present or -1
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY) ?: -1L
            AddTransactionScreen(navController = navController, transactionId = transactionId)
        }

        composable(
            route = AppDestinations.ADD_EDIT_CATEGORY_ROUTE,
            arguments = listOf(navArgument(AppDestinations.CATEGORY_ID_ARG_KEY) {
                type = NavType.LongType
                defaultValue = -1L // Indicates adding a new category
            })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong(AppDestinations.CATEGORY_ID_ARG_KEY) ?: -1L
            AddEditCategoryScreen(navController = navController, categoryId = categoryId)
        }

        // Route for TransactionDetailScreen
        composable(
            route = AppDestinations.TRANSACTION_DETAIL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TRANSACTION_ID_ARG_KEY) {
                type = NavType.LongType
                // No defaultValue, as transactionId is required for this screen.
                // The route itself makes it non-nullable.
            })
        ) { backStackEntry ->
            // For a required argument (/{transactionId}), it should always be present.
            // If it could be null due to a programming error in navigation,
            // getLong will throw, or you can use getLongOrNull and handle it.
            val transactionId = backStackEntry.arguments?.getLong(AppDestinations.TRANSACTION_ID_ARG_KEY)

            if (transactionId != null && transactionId != -1L) { // defensive check, -1L shouldn't occur for this path
                TransactionDetailScreen(navController = navController, transactionId = transactionId)
            } else {
                // This state should ideally not be reached if navigation is set up correctly.
                // It means the argument was not passed or was invalid.
                // Consider logging an error here for debugging.
                // Log.e("NavigationError", "Invalid transactionId for TransactionDetailScreen: $transactionId")
                navController.popBackStack() // Go back as a fallback
            }
        }

        // composable(AppDestinations.CATEGORIES_ROUTE) {
        //     CategoriesScreen(navController = navController) // If you have this screen
        // }

        // composable("reports") {
        //     // Will implement in next phase
        // }

        // composable("settings") {
        //     // Will implement in next phase
        // }
    }
}