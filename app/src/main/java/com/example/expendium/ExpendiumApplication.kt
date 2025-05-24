// ExpendiumApplication.kt
package com.example.expendium

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpendiumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}