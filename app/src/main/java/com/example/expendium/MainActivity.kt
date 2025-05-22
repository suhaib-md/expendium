// MainActivity.kt
package com.example.expendium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.example.expendium.ui.theme.ExpendiumTheme
import com.example.expendium.ui.navigation.ExpendiumNavigation
import com.example.expendium.ui.viewmodel.AppInitializationViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpendiumTheme {
                // Initialize app data
                val initViewModel: AppInitializationViewModel = hiltViewModel()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ExpendiumNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}