package com.example.expendium.ui.screen

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.expendium.data.DefaultDataProvider
import com.example.expendium.data.model.Account
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.viewmodel.TransactionViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

// Helper function for currency formatting
fun formatCurrencyInput(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    return format.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel = hiltViewModel(),
    transactionId: Long? // Null for new, otherwise ID of transaction to edit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val categoriesFromDb by viewModel.categories.collectAsStateWithLifecycle()
    val accountsFromDb by viewModel.accounts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transactionToEdit by viewModel.transactionToEdit.collectAsStateWithLifecycle()

    var amount by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.amount?.toString() ?: "") }
    var merchantOrPayee by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.merchantOrPayee ?: "") }
    var notes by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.notes ?: "") }
    var selectedDateMillis by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.transactionDate ?: System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var transactionType by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.type ?: TransactionType.EXPENSE) }

    val paymentModes = remember { DefaultDataProvider.getDefaultPaymentModes() }
    var selectedPaymentMode by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.paymentMode ?: paymentModes.firstOrNull() ?: "") }
    var selectedAccountId by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.accountId) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var paymentModeDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val screenTitle = if (transactionId != null && uiState.isEditing) "Edit Transaction" else "Add Transaction"
    val snackbarHostState = remember { SnackbarHostState() }

    // Load transaction for editing or prepare for new
    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            viewModel.loadTransactionForEditing(transactionId)
        } else {
            viewModel.prepareForNewTransaction()
        }
    }

    // Populate fields when transactionToEdit is loaded or reset for new
    LaunchedEffect(transactionToEdit, categoriesFromDb, accountsFromDb, uiState.isEditing) {
        if (transactionToEdit != null && uiState.isEditing) {
            val loadedTransaction = transactionToEdit!!
            amount = loadedTransaction.amount.toString()
            merchantOrPayee = loadedTransaction.merchantOrPayee
            notes = loadedTransaction.notes ?: ""
            selectedDateMillis = loadedTransaction.transactionDate
            transactionType = loadedTransaction.type
            selectedPaymentMode = loadedTransaction.paymentMode
            selectedAccountId = loadedTransaction.accountId

            if (categoriesFromDb.isNotEmpty()) {
                selectedCategory = categoriesFromDb.find { it.categoryId == loadedTransaction.categoryId }
            }
        } else if (!uiState.isEditing && transactionToEdit == null) {
            amount = ""
            merchantOrPayee = ""
            notes = ""
            selectedDateMillis = System.currentTimeMillis()
            selectedCategory = null
            transactionType = TransactionType.EXPENSE
            selectedPaymentMode = paymentModes.firstOrNull() ?: ""
            selectedAccountId = null
        }
    }

    LaunchedEffect(transactionToEdit?.categoryId, categoriesFromDb) {
        if (transactionToEdit != null && categoriesFromDb.isNotEmpty() && selectedCategory == null) {
            selectedCategory = categoriesFromDb.find { it.categoryId == transactionToEdit?.categoryId }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        screenTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transaction Type Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                        text = "Transaction Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TransactionTypeChip(
                            label = "Expense",
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            isSelected = transactionType == TransactionType.EXPENSE,
                            color = Color(0xFFFF1744),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (transactionType != TransactionType.EXPENSE) {
                                transactionType = TransactionType.EXPENSE
                                if (selectedCategory?.type != TransactionType.EXPENSE) selectedCategory = null
                            }
                        }

                        TransactionTypeChip(
                            label = "Income",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isSelected = transactionType == TransactionType.INCOME,
                            color = Color(0xFF00C853),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (transactionType != TransactionType.INCOME) {
                                transactionType = TransactionType.INCOME
                                if (selectedCategory?.type != TransactionType.INCOME) selectedCategory = null
                            }
                        }
                    }
                }
            }

            // Amount Preview Card
            AnimatedVisibility(
                visible = amount.isNotEmpty() && amount.toDoubleOrNull() != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (transactionType == TransactionType.INCOME)
                            Color(0xFF00C853).copy(alpha = 0.1f)
                        else
                            Color(0xFFFF1744).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (transactionType == TransactionType.INCOME)
                                Icons.AutoMirrored.Filled.TrendingUp
                            else
                                Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (transactionType == TransactionType.INCOME)
                                Color(0xFF00C853)
                            else
                                Color(0xFFFF1744),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatCurrencyInput(amount.toDoubleOrNull() ?: 0.0),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (transactionType == TransactionType.INCOME)
                                Color(0xFF00C853)
                            else
                                Color(0xFFFF1744)
                        )
                    }
                }
            }

            // Form Fields Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Amount Field
                    ModernTextField(
                        value = amount,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                amount = newValue
                            }
                        },
                        label = "Amount",
                        leadingIcon = {
                            Text(
                                "₹",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.errorMessage?.contains("Amount", ignoreCase = true) == true
                    )

                    // Merchant/Payee Field
                    ModernTextField(
                        value = merchantOrPayee,
                        onValueChange = { merchantOrPayee = it },
                        label = "Merchant / Payee",
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Store,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        isError = uiState.errorMessage?.contains("Merchant/Payee", ignoreCase = true) == true
                    )

                    // Date Field
                    ModernTextField(
                        value = dateFormatter.format(Date(selectedDateMillis)),
                        onValueChange = {},
                        label = "Transaction Date",
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Select Date"
                                )
                            }
                        }
                    )

                    // Category Dropdown
                    ModernDropdownField(
                        value = selectedCategory?.name ?: "Select Category",
                        label = "Category",
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = it },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        isError = uiState.errorMessage?.contains("Category", ignoreCase = true) == true && selectedCategory == null
                    ) {
                        val filteredCategories = categoriesFromDb.filter { it.type == transactionType }
                        if (filteredCategories.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (transactionType == TransactionType.INCOME)
                                            "No income categories"
                                        else
                                            "No expense categories"
                                    )
                                },
                                onClick = { categoryDropdownExpanded = false },
                                enabled = false
                            )
                        } else {
                            filteredCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Payment Mode Dropdown
                    ModernDropdownField(
                        value = selectedPaymentMode.ifEmpty { "Select Payment Mode" },
                        label = "Payment Mode",
                        expanded = paymentModeDropdownExpanded,
                        onExpandedChange = { paymentModeDropdownExpanded = it },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Payment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        paymentModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    selectedPaymentMode = mode
                                    paymentModeDropdownExpanded = false
                                }
                            )
                        }
                    }

                    // Account Dropdown
                    ModernDropdownField(
                        value = accountsFromDb.find { it.accountId == selectedAccountId }?.name ?: "Select Account",
                        label = "Account",
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = it },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        isError = uiState.errorMessage?.contains("Account", ignoreCase = true) == true && selectedAccountId == null
                    ) {
                        if (accountsFromDb.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No accounts available. Add one first.") },
                                onClick = { accountDropdownExpanded = false },
                                enabled = false
                            )
                        } else {
                            accountsFromDb.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        selectedAccountId = account.accountId
                                        accountDropdownExpanded = false
                                        if (uiState.errorMessage?.contains("Account", ignoreCase = true) == true) {
                                            viewModel.clearError()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Notes Field
                    ModernTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Notes (Optional)",
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Notes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    keyboardController?.hide()
                    val amountDouble = amount.toDoubleOrNull()

                    val success = viewModel.saveTransaction(
                        transactionIdToUpdate = transactionId,
                        amount = amountDouble ?: 0.0,
                        merchantOrPayee = merchantOrPayee,
                        categoryId = selectedCategory?.categoryId,
                        paymentMode = selectedPaymentMode,
                        notes = notes.takeIf { it.isNotBlank() },
                        transactionDate = selectedDateMillis,
                        type = transactionType,
                        accountId = selectedAccountId
                    )
                    if (success) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (transactionId != null && uiState.isEditing)
                            "Update Transaction"
                        else
                            "Save Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year, month, dayOfMonth)
                selectedDateMillis = calendar.timeInMillis
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showDatePicker = false }
            show()
        }
    }
}

@Composable
fun TransactionTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    isError: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDropdownField(
    value: String,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier.fillMaxWidth()
    ) {
        ModernTextField(
            value = value,
            onValueChange = {},
            label = label,
            readOnly = true,
            leadingIcon = leadingIcon,
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = isError,
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp)
            )
        ) {
            content()
        }
    }
}