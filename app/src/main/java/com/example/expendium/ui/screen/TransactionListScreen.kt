// ui/screen/TransactionListScreen.kt
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
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.model.TransactionWithCategory
import com.example.expendium.ui.navigation.navigateToTransactionDetail
import com.example.expendium.ui.viewmodel.TransactionViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Data classes for grouping
data class TransactionGroup(
    val date: String,
    val timestamp: Long,
    val transactions: List<TransactionWithCategory>,
    val totalAmount: Double
)

// Filter enum
enum class TransactionFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
}

// Helper functions
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    return format.format(amount)
}

fun formatDateShort(timestamp: Long, format: String = "MMM dd"): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateYear(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateFull(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun isToday(timestamp: Long): Boolean {
    return isSameDay(timestamp, System.currentTimeMillis())
}

fun isYesterday(timestamp: Long): Boolean {
    val yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    return isSameDay(timestamp, yesterday)
}

fun getDateDisplayText(timestamp: Long): String {
    return when {
        isToday(timestamp) -> "Today"
        isYesterday(timestamp) -> "Yesterday"
        else -> formatDateShort(timestamp, "MMM dd, yyyy")
    }
}

fun filterTransactions(
    transactions: List<TransactionWithCategory>,
    filter: TransactionFilter
): List<TransactionWithCategory> {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()

    return when (filter) {
        TransactionFilter.ALL -> transactions
        TransactionFilter.TODAY -> {
            transactions.filter { isToday(it.transaction.transactionDate) }
        }
        TransactionFilter.THIS_WEEK -> {
            calendar.timeInMillis = now
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val weekStart = calendar.timeInMillis

            transactions.filter { it.transaction.transactionDate >= weekStart }
        }
        TransactionFilter.THIS_MONTH -> {
            calendar.timeInMillis = now
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.timeInMillis

            transactions.filter { it.transaction.transactionDate >= monthStart }
        }
        TransactionFilter.THIS_YEAR -> {
            calendar.timeInMillis = now
            calendar.set(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val yearStart = calendar.timeInMillis

            transactions.filter { it.transaction.transactionDate >= yearStart }
        }
    }
}

fun groupTransactionsByDate(transactions: List<TransactionWithCategory>): List<TransactionGroup> {
    return transactions
        .groupBy { transaction ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = transaction.transaction.transactionDate
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        .map { (timestamp, transactionList) ->
            TransactionGroup(
                date = getDateDisplayText(timestamp),
                timestamp = timestamp,
                transactions = transactionList.sortedByDescending { it.transaction.transactionDate },
                totalAmount = transactionList.sumOf {
                    if (it.transaction.type == TransactionType.INCOME)
                        it.transaction.amount
                    else
                        -it.transaction.amount
                }
            )
        }
        .sortedByDescending { it.timestamp }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavController,
    viewModel: TransactionViewModel,
    onNavigateToSettings: () -> Unit
) {
    val transactionsWithCategories by viewModel.transactionsWithCategoryNames.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSummary by remember { mutableStateOf(true) }
    var currentFilter by remember { mutableStateOf(TransactionFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Process transactions based on search and filter
    val processedTransactions = remember(transactionsWithCategories, uiState.searchQuery, currentFilter) {
        val searchFiltered = if (uiState.searchQuery.isNotEmpty()) {
            transactionsWithCategories.filter { transactionWithCategory ->
                transactionWithCategory.transaction.merchantOrPayee.contains(uiState.searchQuery, ignoreCase = true) ||
                        transactionWithCategory.categoryName?.contains(uiState.searchQuery, ignoreCase = true) == true ||
                        transactionWithCategory.transaction.notes?.contains(uiState.searchQuery, ignoreCase = true) == true
            }
        } else {
            transactionsWithCategories
        }

        val timeFiltered = filterTransactions(searchFiltered, currentFilter)
        groupTransactionsByDate(timeFiltered)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    // Filter button
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Filter transactions",
                                tint = if (currentFilter != TransactionFilter.ALL)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            TransactionFilter.values().forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when(filter) {
                                                TransactionFilter.ALL -> "All Transactions"
                                                TransactionFilter.TODAY -> "Today"
                                                TransactionFilter.THIS_WEEK -> "This Week"
                                                TransactionFilter.THIS_MONTH -> "This Month"
                                                TransactionFilter.THIS_YEAR -> "This Year"
                                            }
                                        )
                                    },
                                    onClick = {
                                        currentFilter = filter
                                        showFilterMenu = false
                                    },
                                    leadingIcon = {
                                        if (currentFilter == filter) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

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
                    visible = showSummary && transactionsWithCategories.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val summaryTransactions = processedTransactions.flatMap { it.transactions.map { it.transaction } }
                    if (summaryTransactions.isNotEmpty()) {
                        TransactionSummaryCard(summaryTransactions, currentFilter)
                    }
                }
            }

            // Search Section
            item {
                SearchBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.searchTransactions(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Content Section
            if (uiState.isLoading && transactionsWithCategories.isEmpty()) {
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
            } else if (processedTransactions.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No transactions found",
                        subtitle = "Try adjusting your search terms or filters"
                    )
                }
            } else if (processedTransactions.isEmpty() && currentFilter != TransactionFilter.ALL) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FilterList,
                        title = "No transactions found",
                        subtitle = "No transactions match the selected filter"
                    )
                }
            } else if (transactionsWithCategories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Receipt,
                        title = "No transactions yet",
                        subtitle = "Start by adding your first transaction"
                    )
                }
            } else {
                // Group transactions by date
                items(processedTransactions) { group ->
                    TransactionDateGroup(
                        group = group,
                        onItemClick = { transactionId ->
                            navController.navigateToTransactionDetail(transactionId)
                        }
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun TransactionDateGroup(
    group: TransactionGroup,
    onItemClick: (Long) -> Unit
) {
    Column {
        // Date Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = formatCurrency(group.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (group.totalAmount >= 0)
                        Color(0xFF00C853)
                    else
                        Color(0xFFFF1744)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transactions for this date
        group.transactions.forEach { transactionWithCategory ->
            ModernTransactionItem(
                transaction = transactionWithCategory.transaction,
                categoryName = transactionWithCategory.categoryName,
                onItemClick = onItemClick
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TransactionSummaryCard(transactions: List<Transaction>, filter: TransactionFilter) {
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netFlow = totalIncome - totalExpenses

    val filterTitle = when(filter) {
        TransactionFilter.ALL -> "Overall Summary"
        TransactionFilter.TODAY -> "Today's Summary"
        TransactionFilter.THIS_WEEK -> "This Week's Summary"
        TransactionFilter.THIS_MONTH -> "This Month's Summary"
        TransactionFilter.THIS_YEAR -> "This Year's Summary"
    }

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
                text = filterTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetric(
                    label = "Income",
                    amount = totalIncome,
                    color = Color(0xFF00C853),
                    icon = Icons.Filled.TrendingUp
                )
                SummaryMetric(
                    label = "Expenses",
                    amount = totalExpenses,
                    color = Color(0xFFFF1744),
                    icon = Icons.Filled.TrendingDown
                )
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
                    text = "Net Flow",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatCurrency(netFlow),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (netFlow >= 0) Color(0xFF00C853) else Color(0xFFFF1744)
                )
            }
        }
    }
}

@Composable
fun SummaryMetric(
    label: String,
    amount: Double,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                text = formatCurrency(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text("Search transactions") },
        placeholder = { Text("Merchant, category, or notes...") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun ModernTransactionItem(
    transaction: Transaction,
    categoryName: String?,
    onItemClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(transaction.transactionId) },
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
            // Transaction Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (transaction.type == TransactionType.INCOME)
                            Color(0xFF00C853).copy(alpha = 0.1f)
                        else
                            Color(0xFFFF1744).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.type == TransactionType.INCOME)
                        Icons.Filled.TrendingUp
                    else
                        Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (transaction.type == TransactionType.INCOME)
                        Color(0xFF00C853)
                    else
                        Color(0xFFFF1744),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Transaction Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantOrPayee,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = categoryName ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.transactionDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatCurrency(transaction.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.INCOME)
                        Color(0xFF00C853)
                    else
                        Color(0xFFFF1744)
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}