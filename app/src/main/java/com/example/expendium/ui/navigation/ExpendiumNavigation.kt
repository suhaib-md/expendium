// ui/navigation/ExpendiumNavigation.kt
package com.example.expendium.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.expendium.ui.screen.MainScreen

@Composable
fun ExpendiumNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            MainScreen()
        }

        composable("add_transaction") {
            // Will implement in next phase
        }

        composable("transaction_detail/{transactionId}") { backStackEntry ->
            // Will implement in next phase
        }

        composable("categories") {
            // Will implement in next phase
        }

        composable("reports") {
            // Will implement in next phase
        }

        composable("settings") {
            // Will implement in next phase
        }
    }
}