// In ui/screen/AccountListScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.data.model.Account
// Import the navigation helper functions
import com.example.expendium.ui.navigation.navigateToAddAccount
import com.example.expendium.ui.navigation.navigateToEditAccount
import com.example.expendium.ui.viewmodel.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    navController: NavController,
    accountViewModel: AccountViewModel = hiltViewModel()
) {
    val accounts by accountViewModel.allAccounts.collectAsState()
    val uiState by accountViewModel.uiState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Accounts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                accountViewModel.prepareNewAccount() // Prepare for new account
                navController.navigateToAddAccount()
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Account")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isLoading && accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No accounts yet. Tap the '+' button to add one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts, key = { it.accountId }) { account ->
                        AccountItem(
                            account = account,
                            onEditClick = {
                                navController.navigateToEditAccount(account.accountId)
                            },
                            onDeleteClick = {
                                showDeleteDialog = account
                            }
                        )
                    }
                }
            }
            if (uiState.errorMessage != null && !uiState.saveSuccess) { // Don't show generic errors if a save just happened
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(uiState.errorMessage) {
                    snackbarHostState.showSnackbar(
                        message = uiState.errorMessage!!,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Short
                    )
                    accountViewModel.clearErrorMessage()
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete '${showDeleteDialog!!.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        accountViewModel.deleteAccount(showDeleteDialog!!.accountId)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AccountItem(
    account: Account,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Type: ${account.type}", style = MaterialTheme.typography.bodySmall)
                // --- MODIFIED TO SHOW ACCOUNT NUMBER ---
                account.accountNumber?.let { accNum ->
                    if (accNum.isNotBlank()) {
                        Text(text = "Number: $accNum", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(text = "Balance: ${"%.2f".format(account.currentBalance)}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Account")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Account", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}