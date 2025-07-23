package com.corestate.androidApp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : NavigationDestination(
        route = "dashboard",
        title = "Dashboard", 
        icon = Icons.Default.Dashboard
    )
    
    object Backup : NavigationDestination(
        route = "backup",
        title = "Backup",
        icon = Icons.Default.Backup
    )
    
    object Files : NavigationDestination(
        route = "files",
        title = "Files",
        icon = Icons.Default.Folder
    )
    
    object Settings : NavigationDestination(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )
}

object CoreStateNavigation {
    val destinations = listOf(
        NavigationDestination.Dashboard,
        NavigationDestination.Backup,
        NavigationDestination.Files,
        NavigationDestination.Settings
    )
}