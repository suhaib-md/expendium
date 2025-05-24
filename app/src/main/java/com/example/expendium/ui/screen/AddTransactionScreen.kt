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
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel = hiltViewModel(),
    transactionId: Long // -1L for new, otherwise ID of transaction to edit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val categoriesFromDb by viewModel.categories.collectAsStateWithLifecycle() // All categories
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transactionToEdit by viewModel.transactionToEdit.collectAsStateWithLifecycle()

    // Form state - use derivedStateOf to react to transactionToEdit or keep local if not editing
    var amount by remember { mutableStateOf("") }
    var merchantOrPayee by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) } // Default

    val paymentModes = remember { DefaultDataProvider.getDefaultPaymentModes() }
    var selectedPaymentMode by remember { mutableStateOf(paymentModes.firstOrNull() ?: "") }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var paymentModeDropdownExpanded by remember { mutableStateOf(false) }

    val screenTitle = if (transactionId != -1L && uiState.isEditing) "Edit Transaction" else "Add Transaction"

    // Load transaction for editing or prepare for new
    LaunchedEffect(transactionId) {
        if (transactionId != -1L) {
            viewModel.loadTransactionForEditing(transactionId)
        } else {
            viewModel.prepareForNewTransaction() // Clears previous edit state and sets up for new
        }
    }

    // Populate fields when transactionToEdit is loaded
    LaunchedEffect(transactionToEdit, categoriesFromDb) {
        transactionToEdit?.let { loadedTransaction ->
            amount = loadedTransaction.amount.toString()
            merchantOrPayee = loadedTransaction.merchantOrPayee
            notes = loadedTransaction.notes ?: ""
            selectedDateMillis = loadedTransaction.transactionDate
            transactionType = loadedTransaction.type
            selectedPaymentMode = loadedTransaction.paymentMode

            // Find and set the category
            // Ensure categoriesFromDb is not empty and has finished loading
            if (categoriesFromDb.isNotEmpty()) {
                selectedCategory = categoriesFromDb.find { it.categoryId == loadedTransaction.categoryId }
            }
        } ?: run {
            // If transactionToEdit is null (either new or cleared after save/delete) reset fields
            if (!uiState.isEditing) { // Only reset if not in the process of loading an edit
                amount = ""
                merchantOrPayee = ""
                notes = ""
                selectedDateMillis = System.currentTimeMillis()
                selectedCategory = null
                transactionType = TransactionType.EXPENSE // Default
                selectedPaymentMode = paymentModes.firstOrNull() ?: ""
            }
        }
    }

    // Update selectedCategory if categoriesFromDb changes and we have a transactionToEdit (e.g. after initial load)
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
            verticalArrangement = Arrangement.spacedBy(12.dp) // Slightly reduced spacing
        ) {
            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}\$"))) { // Allow up to 2 decimal places
                        amount = newValue
                    }
                },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("₹") }, // Example currency
                singleLine = true
            )

            // Merchant/Payee
            OutlinedTextField(
                value = merchantOrPayee,
                onValueChange = { merchantOrPayee = it },
                label = { Text("Merchant / Payee") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Date Picker
            OutlinedTextField(
                value = dateFormatter.format(Date(selectedDateMillis)), // Use Date object
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
                    // datePicker.maxDate = System.currentTimeMillis() // Optional: prevent future dates
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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
                    value = selectedPaymentMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentModeDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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

            // Transaction Type (Income/Expense)
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

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.weight(1f, fill = false)) // Pushes button to bottom

            // Save Button
            Button(
                onClick = {
                    keyboardController?.hide() // Hide keyboard before processing
                    val amountDouble = amount.toDoubleOrNull()

                    val success = viewModel.saveTransaction(
                        transactionIdToUpdate = if (transactionId != -1L) transactionId else null,
                        amount = amountDouble ?: 0.0, // Let ViewModel handle amount validation
                        merchantOrPayee = merchantOrPayee,
                        categoryId = selectedCategory?.categoryId,
                        paymentMode = selectedPaymentMode,
                        notes = notes.takeIf { it.isNotBlank() },
                        transactionDate = selectedDateMillis,
                        type = transactionType
                    )
                    if (success) {
                        navController.popBackStack()
                    }
                    // Error message will be shown by the LaunchedEffect observing uiState.errorMessage
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (transactionId != -1L && uiState.isEditing) "Update Transaction" else "Save Transaction")
                }
            }
        }
    }
}