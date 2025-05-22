// ui/viewmodel/AppInitializationViewModel.kt
package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.example.expendium.data.repository.DataInitializer
import javax.inject.Inject

@HiltViewModel
class AppInitializationViewModel @Inject constructor(
    private val dataInitializer: DataInitializer
) : ViewModel() {

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            try {
                dataInitializer.initializeDefaultData()
            } catch (e: Exception) {
                // Handle initialization error
                // You might want to show an error state or retry mechanism
            }
        }
    }
}