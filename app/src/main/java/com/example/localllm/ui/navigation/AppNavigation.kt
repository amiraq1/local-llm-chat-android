package com.example.localllm.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.localllm.ui.benchmark.BenchmarkScreen
import com.example.localllm.ui.chat.ChatScreen
import com.example.localllm.ui.history.HistoryScreen
import com.example.localllm.ui.models.ModelsScreen
import com.example.localllm.ui.settings.SettingsScreen
import com.example.localllm.ui.tasks.TasksScreen

sealed class Screen(val route: String) {
    object Chat      : Screen("chat")
    object Models    : Screen("models")
    object History   : Screen("history")
    object Benchmark : Screen("benchmark")
    object Settings  : Screen("settings")
    object Tasks     : Screen("tasks")
}

data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Chat,      "الدردشة",    Icons.Filled.Chat,          Icons.Outlined.Chat),
    NavItem(Screen.Tasks,     "المهام",     Icons.Filled.Build,          Icons.Outlined.Build),
    NavItem(Screen.Models,    "النماذج",    Icons.Filled.Memory,         Icons.Outlined.Memory),
    NavItem(Screen.History,   "السجل",      Icons.Filled.History,        Icons.Outlined.History),
    NavItem(Screen.Settings,  "الإعدادات",  Icons.Filled.Settings,       Icons.Outlined.Settings)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDest = currentBackStack?.destination

    val rootRoutes = navItems.map { it.screen.route }.toSet()
    val currentRoute = currentDest?.route
    val showBottomBar = currentRoute in rootRoutes || currentRoute == null

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit  = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    tonalElevation = NavigationBarDefaults.Elevation
                ) {
                    navItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState   = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController     = navController,
            startDestination  = Screen.Chat.route,
            modifier          = Modifier.padding(innerPadding),
            enterTransition   = { fadeIn(animationSpec = tween(200)) },
            exitTransition    = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition  = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(onOpenConversation = { id ->
                    navController.navigate("chat/$id")
                })
            }
            composable("chat/{conversationId}") { back ->
                val id = back.arguments?.getString("conversationId")?.toLongOrNull() ?: -1L
                ChatScreen(conversationId = id, onOpenConversation = {})
            }
            composable(Screen.Tasks.route)     { TasksScreen() }
            composable(Screen.Models.route)    { ModelsScreen() }
            composable(Screen.History.route)   { HistoryScreen(onOpenConversation = { id -> navController.navigate("chat/$id") }) }
            composable(Screen.Benchmark.route) { BenchmarkScreen() }
            composable(Screen.Settings.route)  { SettingsScreen() }
        }
    }
}
