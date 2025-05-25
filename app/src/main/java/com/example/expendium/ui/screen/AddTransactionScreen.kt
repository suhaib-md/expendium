package com.example.expendium.ui.screen

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val accountsFromDb by viewModel.accounts.collectAsStateWithLifecycle() // Collect accounts
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transactionToEdit by viewModel.transactionToEdit.collectAsStateWithLifecycle()

    var amount by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.amount?.toString() ?: "") }
    var merchantOrPayee by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.merchantOrPayee ?: "") }
    var notes by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.notes ?: "") }
    var selectedDateMillis by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.transactionDate ?: System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) } // Will be populated by LaunchedEffect
    var transactionType by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.type ?: TransactionType.EXPENSE) }

    val paymentModes = remember { DefaultDataProvider.getDefaultPaymentModes() }
    var selectedPaymentMode by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.paymentMode ?: paymentModes.firstOrNull() ?: "") }

    // State for selected account ID
    var selectedAccountId by rememberSaveable(transactionToEdit) { mutableStateOf(transactionToEdit?.accountId) }


    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var paymentModeDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val screenTitle = if (transactionId != null && uiState.isEditing) "Edit Transaction" else "Add Transaction"


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
        if (transactionToEdit != null && uiState.isEditing) { // Check isEditing flag
            val loadedTransaction = transactionToEdit!! // Safe due to check
            amount = loadedTransaction.amount.toString()
            merchantOrPayee = loadedTransaction.merchantOrPayee
            notes = loadedTransaction.notes ?: ""
            selectedDateMillis = loadedTransaction.transactionDate
            transactionType = loadedTransaction.type
            selectedPaymentMode = loadedTransaction.paymentMode
            selectedAccountId = loadedTransaction.accountId // Directly set the ID

            if (categoriesFromDb.isNotEmpty()) {
                selectedCategory = categoriesFromDb.find { it.categoryId == loadedTransaction.categoryId }
            }
        } else if (!uiState.isEditing && transactionToEdit == null) { // Reset for new or after save/delete
            amount = ""
            merchantOrPayee = ""
            notes = ""
            selectedDateMillis = System.currentTimeMillis()
            selectedCategory = null
            transactionType = TransactionType.EXPENSE
            selectedPaymentMode = paymentModes.firstOrNull() ?: ""
            selectedAccountId = null // Reset selected account ID
        }
    }

    // This effect ensures category is set once categories are loaded, especially for edits
    LaunchedEffect(transactionToEdit?.categoryId, categoriesFromDb) {
        if (transactionToEdit != null && categoriesFromDb.isNotEmpty() && selectedCategory == null) {
            selectedCategory = categoriesFromDb.find { it.categoryId == transactionToEdit?.categoryId }
        }
    }


    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amount = newValue
                    }
                },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("₹") },
                singleLine = true,
                isError = uiState.errorMessage?.contains("Amount", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = merchantOrPayee,
                onValueChange = { merchantOrPayee = it },
                label = { Text("Merchant / Payee") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.errorMessage?.contains("Merchant/Payee", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = dateFormatter.format(Date(selectedDateMillis)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Transaction Date") },
                trailingIcon = {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = "Select Date",
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

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

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Select Category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    isError = uiState.errorMessage?.contains("Category", ignoreCase = true) == true && selectedCategory == null
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    val filteredCategories = categoriesFromDb.filter { it.type == transactionType }
                    if (filteredCategories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (transactionType == TransactionType.INCOME) "No income categories" else "No expense categories") },
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
            }

            // Payment Mode Dropdown
            ExposedDropdownMenuBox(
                expanded = paymentModeDropdownExpanded,
                onExpandedChange = { paymentModeDropdownExpanded = !paymentModeDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPaymentMode.ifEmpty { "Select Payment Mode" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentModeDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = paymentModeDropdownExpanded,
                    onDismissRequest = { paymentModeDropdownExpanded = false }
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
            }

            // Account Dropdown
            ExposedDropdownMenuBox(
                expanded = accountDropdownExpanded,
                onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedAccountName = accountsFromDb.find { it.accountId == selectedAccountId }?.name
                OutlinedTextField(
                    value = selectedAccountName ?: "Select Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    isError = uiState.errorMessage?.contains("Account", ignoreCase = true) == true && selectedAccountId == null
                )
                ExposedDropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false }
                ) {
                    if (accountsFromDb.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No accounts available. Add one first.") },
                            onClick = { accountDropdownExpanded = false /* Optionally navigate */ },
                            enabled = false // Consider true if you want to navigate
                        )
                    } else {
                        accountsFromDb.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccountId = account.accountId
                                    accountDropdownExpanded = false
                                    if (uiState.errorMessage?.contains("Account", ignoreCase = true) == true) {
                                        viewModel.clearError() // Clear error if user selects an account
                                    }
                                }
                            )
                        }
                    }
                }
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Type:", style = MaterialTheme.typography.titleMedium)
                FilterChip(
                    selected = transactionType == TransactionType.EXPENSE,
                    onClick = {
                        if (transactionType != TransactionType.EXPENSE) {
                            transactionType = TransactionType.EXPENSE
                            if (selectedCategory?.type != TransactionType.EXPENSE) selectedCategory = null
                        }
                    },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = transactionType == TransactionType.INCOME,
                    onClick = {
                        if (transactionType != TransactionType.INCOME) {
                            transactionType = TransactionType.INCOME
                            if (selectedCategory?.type != TransactionType.INCOME) selectedCategory = null
                        }
                    },
                    label = { Text("Income") }
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            Button(
                onClick = {
                    keyboardController?.hide()
                    val amountDouble = amount.toDoubleOrNull()

                    val success = viewModel.saveTransaction(
                        transactionIdToUpdate = transactionId, // Pass directly, VM handles null or ID
                        amount = amountDouble ?: 0.0,
                        merchantOrPayee = merchantOrPayee,
                        categoryId = selectedCategory?.categoryId,
                        paymentMode = selectedPaymentMode,
                        notes = notes.takeIf { it.isNotBlank() },
                        transactionDate = selectedDateMillis,
                        type = transactionType,
                        accountId = selectedAccountId // Pass the selected account ID (Long?)
                    )
                    if (success) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (transactionId != null && uiState.isEditing) "Update Transaction" else "Save Transaction")
                }
            }
        }
    }
}