// ui/screen/AddTransactionScreen.kt
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.expendium.data.DefaultDataProvider
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf("") }
    var merchantOrPayee by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Date selection
    val calendar = Calendar.getInstance()
    var selectedDateMillis by remember { mutableStateOf(calendar.timeInMillis) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Category selection
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Payment Mode selection
    val paymentModes = remember { DefaultDataProvider.getDefaultPaymentModes() }
    var selectedPaymentMode by remember { mutableStateOf(paymentModes.firstOrNull() ?: "") }
    var paymentModeDropdownExpanded by remember { mutableStateOf(false) }

    // Transaction Type selection (default to Expense)
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }


    // Handle error messages from ViewModel
    uiState.errorMessage?.let { message ->
        // TODO: Show a Snack bar or Toast for the error message
        // For now, we'll just log it and clear
        LaunchedEffect(message) {
            println("Error: $message")
            viewModel.clearError() // Ensure you have a clearError method in ViewModel
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
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
                .verticalScroll(rememberScrollState()), // Make content scrollable
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    // Allow only numbers and a single decimal point
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                        amount = newValue
                    }
                },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("₹") } // Currency symbol
            )

            // Merchant/Payee
            OutlinedTextField(
                value = merchantOrPayee,
                onValueChange = { merchantOrPayee = it },
                label = { Text("Merchant / Payee") },
                modifier = Modifier.fillMaxWidth()
            )

            // Date Picker
            OutlinedTextField(
                value = dateFormatter.format(selectedDateMillis),
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
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                calendar.timeInMillis = selectedDateMillis // Ensure dialog opens with currently selected date

                DatePickerDialog(
                    context,
                    { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                        calendar.set(selectedYear, selectedMonth, selectedDayOfMonth)
                        selectedDateMillis = calendar.timeInMillis
                        showDatePicker = false
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    // Optionally set min/max dates
                    // datePicker.maxDate = System.currentTimeMillis()
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
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    // Filter categories based on selected transaction type
                    val filteredCategories = categories.filter { it.type == transactionType }
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
                        transactionType = TransactionType.EXPENSE
                        // Reset category if current selection is not for expense
                        if (selectedCategory?.type != TransactionType.EXPENSE) {
                            selectedCategory = null
                        }
                    },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = transactionType == TransactionType.INCOME,
                    onClick = {
                        transactionType = TransactionType.INCOME
                        // Reset category if current selection is not for income
                        if (selectedCategory?.type != TransactionType.INCOME) {
                            selectedCategory = null
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
                minLines = 3
            )

            Spacer(modifier = Modifier.weight(1f)) // Pushes button to bottom if content is short

            // Save Button
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (amountDouble == null || amountDouble <= 0) {
                        // TODO: Show error for invalid amount
                        viewModel.setError("Invalid amount entered.") // Example error
                        return@Button
                    }
                    if (merchantOrPayee.isBlank()) {
                        // TODO: Show error for blank merchant
                        viewModel.setError("Merchant/Payee cannot be empty.")
                        return@Button
                    }
                    if (selectedCategory == null) {
                        // TODO: Show error for no category selected
                        viewModel.setError("Please select a category.")
                        return@Button
                    }

                    viewModel.addTransaction(
                        amount = amountDouble,
                        merchantOrPayee = merchantOrPayee,
                        categoryId = selectedCategory!!.categoryId, // Safe due to check above
                        paymentMode = selectedPaymentMode,
                        notes = notes.takeIf { it.isNotBlank() },
                        transactionDate = selectedDateMillis,
                        type = transactionType // Pass the selected type
                    )
                    navController.popBackStack() // Go back after saving
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading // Disable button while loading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Transaction")
                }
            }
        }
    }
}