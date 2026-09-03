package com.lockin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lockin.app.data.VerificationMethod
import com.lockin.app.ui.TodoViewModel
import com.lockin.app.ui.screens.AddTaskScreen
import com.lockin.app.ui.screens.AppPickerScreen
import com.lockin.app.ui.screens.HistoryScreen
import com.lockin.app.ui.screens.LockedAppsScreen
import com.lockin.app.ui.screens.SetupScreen
import com.lockin.app.ui.screens.TodoScreen
import com.lockin.app.ui.screens.TokenScreen
import com.lockin.app.ui.theme.LockInTheme
import com.lockin.app.ui.theme.Violet
import com.lockin.app.ui.verify.CameraVerifyScreen
import com.lockin.app.ui.verify.LocationVerifyScreen
import com.lockin.app.ui.verify.TimerVerifyScreen
import androidx.compose.foundation.layout.padding

class MainActivity : ComponentActivity() {
    private val viewModel: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LockInTheme { LockInApp(viewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Rolls the day over and generates today's recurring tasks.
        viewModel.refreshDay()
    }
}

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Todo : Screen("todo", "Today", Icons.Default.CheckCircle)
    data object Apps : Screen("apps", "Apps", Icons.Default.Lock)
    data object History : Screen("history", "Streaks", Icons.Default.Insights)
    data object Tokens : Screen("tokens", "Tokens", Icons.Default.Bolt)
    data object Setup : Screen("setup", "Setup", Icons.Default.Settings)
}

@Composable
fun LockInApp(viewModel: TodoViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(Screen.Todo, Screen.Apps, Screen.History, Screen.Tokens, Screen.Setup)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Violet,
                                selectedTextColor = Violet,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Todo.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Todo.route) {
                TodoScreen(
                    viewModel = viewModel,
                    onAddTask = { navController.navigate("edit_task/-1") },
                    onEditTask = { navController.navigate("edit_task/${it.id}") },
                    onVerify = { task ->
                        when (task.verificationMethod) {
                            VerificationMethod.CAMERA_SCAN -> navController.navigate("verify_camera/${task.id}")
                            VerificationMethod.TIMER -> navController.navigate("verify_timer/${task.id}")
                            VerificationMethod.LOCATION -> navController.navigate("verify_location/${task.id}")
                            VerificationMethod.MANUAL -> viewModel.markComplete(task)
                        }
                    }
                )
            }

            composable(
                route = "edit_task/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { entry ->
                val raw = entry.arguments?.getLong("taskId") ?: -1L
                AddTaskScreen(
                    viewModel = viewModel,
                    editTaskId = if (raw >= 0) raw else null,
                    onDone = { navController.popBackStack() }
                )
            }

            composable(Screen.Apps.route) {
                LockedAppsScreen(viewModel, onAddApps = { navController.navigate("app_picker") })
            }

            composable("app_picker") {
                val locked by viewModel.lockedApps.collectAsState()
                AppPickerScreen(
                    alreadyLocked = locked.map { it.packageName }.toSet(),
                    onCancel = { navController.popBackStack() },
                    onConfirm = { picked, minutes ->
                        picked.forEach { viewModel.addLockedApp(it.packageName, it.label, minutes) }
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "verify_camera/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                val tasks by viewModel.tasksToday.collectAsState()
                val target = tasks.firstOrNull { it.id == taskId }
                CameraVerifyScreen(
                    taskTitle = target?.title.orEmpty(),
                    expectedLabels = target?.expectedLabels
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    onCancel = { navController.popBackStack() },
                    onVerified = {
                        viewModel.markCompleteById(taskId)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "verify_timer/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                val tasks by viewModel.tasksToday.collectAsState()
                val target = tasks.firstOrNull { it.id == taskId }
                TimerVerifyScreen(
                    taskTitle = target?.title.orEmpty(),
                    totalMinutes = target?.timerMinutes ?: 10,
                    onCancel = { navController.popBackStack() },
                    onVerified = {
                        viewModel.markCompleteById(taskId)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "verify_location/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                val tasks by viewModel.tasksToday.collectAsState()
                val target = tasks.firstOrNull { it.id == taskId }
                LocationVerifyScreen(
                    taskTitle = target?.title.orEmpty(),
                    placeLabel = target?.placeLabel.orEmpty(),
                    targetLat = target?.latitude,
                    targetLng = target?.longitude,
                    radiusMeters = target?.radiusMeters ?: 150,
                    onCancel = { navController.popBackStack() },
                    onVerified = {
                        viewModel.markCompleteById(taskId)
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.History.route) { HistoryScreen(viewModel) }
            composable(Screen.Tokens.route) { TokenScreen(viewModel) }
            composable(Screen.Setup.route) { SetupScreen(viewModel) }
        }
    }
}
