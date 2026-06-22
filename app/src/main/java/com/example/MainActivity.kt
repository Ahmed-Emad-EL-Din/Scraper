package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.BrowserScreen
import com.example.ui.DashboardScreen
import com.example.ui.DetailsScreen
import com.example.ui.TrackerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Clear any old/lingering notifications on start to prevent stale resource pointers
        try {
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancelAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Prompt user for POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val trackerViewModel: TrackerViewModel = viewModel(
                    factory = TrackerViewModel.Factory(application)
                )

                // Listen for deep link rule_id notification click events
                LaunchedEffect(intent) {
                    val ruleId = intent?.getIntExtra("rule_id", -1) ?: -1
                    if (ruleId != -1) {
                        navController.navigate("details/$ruleId") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = trackerViewModel,
                                onNavigateToBrowser = {
                                    navController.navigate("browser")
                                },
                                onNavigateToDetails = { rule ->
                                    navController.navigate("details/${rule.id}")
                                }
                            )
                        }
                        composable(
                            route = "browser?url={url}",
                            arguments = listOf(navArgument("url") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            })
                        ) { backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url")
                            BrowserScreen(
                                viewModel = trackerViewModel,
                                onBackToDashboard = {
                                    navController.popBackStack()
                                },
                                initialUrl = url
                            )
                        }
                        composable(
                            route = "details/{ruleId}",
                            arguments = listOf(navArgument("ruleId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getInt("ruleId") ?: -1
                            DetailsScreen(
                                ruleId = ruleId,
                                viewModel = trackerViewModel,
                                onBack = { navController.popBackStack() },
                                onViewLive = { url ->
                                    navController.navigate("browser?url=$url")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
