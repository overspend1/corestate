/*
 * CoreState v2.0 - Android-Managed Enterprise Backup System
 *
 * Author: Wiktor (overspend1)
 * GitHub: https://github.com/overspend1
 * Repository: https://github.com/overspend1/corestate
 *
 * Copyright (c) 2025 Wiktor
 * Licensed under MIT License
 *
 * Main Application Entry Point
 */

package com.corestate.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CoreStateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize application components
    }
}
