package com.corestate.androidApp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.corestate.androidApp.ui.screens.backup.BackupScreen
import com.corestate.androidApp.ui.screens.dashboard.DashboardScreen
import com.corestate.androidApp.ui.screens.files.FilesScreen
import com.corestate.androidApp.ui.screens.settings.SettingsScreen
import com.corestate.androidApp.ui.navigation.CoreStateNavigation
import com.corestate.androidApp.ui.navigation.NavigationDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreStateApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                CoreStateNavigation.destinations.forEach { destination ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = { Text(destination.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationDestination.Dashboard.route) {
                DashboardScreen(
                    viewModel = hiltViewModel(),
                    onNavigateToBackup = {
                        navController.navigate(NavigationDestination.Backup.route)
                    },
                    onNavigateToFiles = {
                        navController.navigate(NavigationDestination.Files.route)
                    }
                )
            }
            
            composable(NavigationDestination.Backup.route) {
                BackupScreen(
                    viewModel = hiltViewModel()
                )
            }
            
            composable(NavigationDestination.Files.route) {
                FilesScreen(
                    viewModel = hiltViewModel()
                )
            }
            
            composable(NavigationDestination.Settings.route) {
                SettingsScreen(
                    viewModel = hiltViewModel()
                )
            }
        }
    }
}