// ui/screen/AccountListScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.data.model.Account
import com.example.expendium.ui.navigation.navigateToAddAccount
import com.example.expendium.ui.navigation.navigateToEditAccount
import com.example.expendium.ui.viewmodel.AccountViewModel
import java.text.NumberFormat
import java.util.*

// Helper function for currency formatting
fun formatAccountBalance(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    return format.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    navController: NavController,
    accountViewModel: AccountViewModel = hiltViewModel()
) {
    val accounts by accountViewModel.allAccounts.collectAsState()
    val uiState by accountViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSummary by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf<Account?>(null) }

    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            if (!uiState.saveSuccess) { // Don't show generic errors if a save just happened
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                accountViewModel.clearErrorMessage()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Accounts",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    // Summary toggle button
                    IconButton(
                        onClick = { showSummary = !showSummary }
                    ) {
                        Icon(
                            imageVector = if (showSummary) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showSummary) "Hide Summary" else "Show Summary",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    accountViewModel.prepareNewAccount()
                    navController.navigateToAddAccount()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Account",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Account",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Section
            item {
                AnimatedVisibility(
                    visible = showSummary && accounts.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AccountSummaryCard(accounts)
                }
            }

            // Content Section
            if (uiState.isLoading && accounts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (accounts.isEmpty()) {
                item {
                    EmptyAccountState()
                }
            } else {
                // Account list
                items(accounts, key = { it.accountId }) { account ->
                    ModernAccountItem(
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

            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog != null) {
        DeleteAccountDialog(
            account = showDeleteDialog!!,
            onConfirm = {
                accountViewModel.deleteAccount(showDeleteDialog!!.accountId)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

@Composable
fun AccountSummaryCard(accounts: List<Account>) {
    val totalBalance = accounts.sumOf { it.currentBalance }
    val positiveAccounts = accounts.filter { it.currentBalance >= 0 }
    val negativeAccounts = accounts.filter { it.currentBalance < 0 }
    val positiveTotal = positiveAccounts.sumOf { it.currentBalance }
    val negativeTotal = negativeAccounts.sumOf { it.currentBalance }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Account Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccountSummaryMetric(
                    label = "Total Accounts",
                    value = accounts.size.toString(),
                    icon = Icons.Filled.AccountBalance,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                AccountSummaryMetric(
                    label = "Positive Balance",
                    value = formatAccountBalance(positiveTotal),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    color = Color(0xFF00C853)
                )
            }

            if (negativeTotal < 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccountSummaryMetric(
                        label = "Negative Balance",
                        value = formatAccountBalance(negativeTotal),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        color = Color(0xFFFF1744)
                    )

                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net Worth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatAccountBalance(totalBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (totalBalance >= 0) Color(0xFF00C853) else Color(0xFFFF1744)
                )
            }
        }
    }
}

@Composable
fun AccountSummaryMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
fun ModernAccountItem(
    account: Account,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isPositiveBalance = account.currentBalance >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (account.type.lowercase()) {
                        "bank", "checking", "savings" -> Icons.Filled.AccountBalance
                        "cash" -> Icons.Filled.Money
                        "credit", "credit card" -> Icons.Filled.CreditCard
                        "investment" -> Icons.AutoMirrored.Filled.TrendingUp
                        else -> Icons.Filled.AccountBalanceWallet
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Account Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = account.type,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Account number if available
                account.accountNumber?.let { accNum ->
                    if (accNum.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "••• ${accNum.takeLast(4)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Balance and Actions
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatAccountBalance(account.currentBalance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositiveBalance) Color(0xFF00C853) else Color(0xFFFF1744)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit Account",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Account",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAccountState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No accounts yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add your first account to start tracking your finances",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DeleteAccountDialog(
    account: Account,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                "Delete Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                "Are you sure you want to delete '${account.name}'? This action cannot be undone and will remove all associated transactions.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}