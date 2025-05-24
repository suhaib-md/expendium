// ui/screen/TransactionDetailScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use auto-mirrored for LTR/RTL
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.navigation.AppDestinations
import com.example.expendium.ui.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.*

// Re-use or move to a common utils file
fun formatDateFull(timestamp: Long, format: String = "EEE, dd MMM yyyy, hh:mm a"): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(Date(timestamp))
}
// formatCurrency is defined in TransactionListScreen.kt or should be in a common utils file

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    navController: NavController,
    transactionId: Long,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transactionDetail by viewModel.selectedTransactionDetail.collectAsStateWithLifecycle()
    val categoryName by viewModel.selectedTransactionCategoryName.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transactionId) {
        if (transactionId != -1L) { // Ensure we have a valid ID before loading
            viewModel.loadTransactionDetails(transactionId)
        }
    }

    // Clear details when the screen is left or transactionId changes to prevent showing stale data
    DisposableEffect(transactionId) { // Re-run if transactionId changes (e.g. deep link)
        onDispose {
            // Only clear if we are truly navigating away from a specific detail view.
            // If transactionId changes, LaunchedEffect will reload.
            // This clear is more for when the composable is removed from composition.
            viewModel.clearSelectedTransactionDetails()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    transactionDetail?.let {
                        IconButton(onClick = {
                            navController.navigate("${AppDestinations.ADD_TRANSACTION_BASE_ROUTE}?transactionId=${it.transactionId}")
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Transaction")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Transaction", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (uiState.isLoading && transactionDetail == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (transactionDetail == null && !uiState.isLoading) { // Check if not loading and still null
                Text(
                    text = uiState.errorMessage ?: "Transaction not found or could not be loaded.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                transactionDetail?.let { transaction ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp) // Increased spacing
                    ) {
                        DetailRow("Amount:", formatCurrency(transaction.amount), valueColor = if (transaction.type == TransactionType.INCOME) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, isAmount = true)
                        DetailRow("Type:", transaction.type.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) })
                        DetailRow("Merchant/Payee:", transaction.merchantOrPayee)
                        DetailRow("Category:", categoryName ?: (if (transaction.categoryId == null) "Not Set" else "Loading..."))
                        DetailRow("Date:", formatDateFull(transaction.transactionDate))
                        DetailRow("Payment Mode:", transaction.paymentMode)
                        transaction.notes?.takeIf { it.isNotBlank() }?.let {
                            DetailRow("Notes:", it, isMultiLine = true)
                        }
                        // DetailRow("Manually Added:", if(transaction.isManual) "Yes" else "No")
                        // DetailRow("Created At:", formatDateFull(transaction.createdAt))
                        // DetailRow("Last Updated:", formatDateFull(transaction.updatedAt))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionDetail?.let {
                            viewModel.deleteTransaction(it.transactionId)
                            showDeleteDialog = false
                            // ViewModel's delete will clear selected details.
                            // Successful deletion should pop the back stack.
                            // If there's an error, the snackbar will show.
                            navController.popBackStack()
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = LocalContentColor.current,
    isAmount: Boolean = false,
    isMultiLine: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = if (isMultiLine) Alignment.Top else Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(0.35f) // Adjust weight as needed
        )
        Text(
            text = value,
            style = if (isAmount) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyLarge,
            color = valueColor,
            modifier = Modifier.weight(0.65f),
            lineHeight = if (isMultiLine) 20.sp else TextUnit.Unspecified // Improve readability for multiline
        )
    }
}