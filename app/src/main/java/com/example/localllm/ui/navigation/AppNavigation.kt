package com.example.localllm.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.localllm.ui.benchmark.BenchmarkScreen
import com.example.localllm.ui.chat.ChatScreen
import com.example.localllm.ui.history.HistoryScreen
import com.example.localllm.ui.models.ModelsScreen
import com.example.localllm.ui.settings.SettingsScreen

sealed interface AppGraph {
    val route: String

    data object Root : AppGraph {
        override val route: String = "root"
    }

    data object Main : AppGraph {
        override val route: String = "main"
    }

    data object Chat : AppGraph {
        override val route: String = "chat_graph"
    }
}

sealed interface AppDestination {
    val route: String

    data object ChatHome : AppDestination {
        override val route: String = "chat"
    }

    data object ChatConversation : AppDestination {
        override val route: String = "chat/{conversationId}"
        const val ARG_CONVERSATION_ID = "conversationId"

        fun createRoute(conversationId: Long): String = "chat/$conversationId"

        val arguments = listOf(
            navArgument(ARG_CONVERSATION_ID) {
                type = NavType.LongType
            }
        )

        val deepLinks: List<NavDeepLink> = listOf(
            navDeepLink {
                uriPattern = "localllm://chat/{$ARG_CONVERSATION_ID}"
            }
        )
    }

    data object Models : AppDestination {
        override val route: String = "models"
    }

    data object History : AppDestination {
        override val route: String = "history"
    }

    data object Benchmark : AppDestination {
        override val route: String = "benchmark"
    }

    data object Settings : AppDestination {
        override val route: String = "settings"
    }
}

data class BottomNavItem(
    val destination: AppDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        destination = AppDestination.ChatHome,
        label = "الدردشة",
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat
    ),
    BottomNavItem(
        destination = AppDestination.Models,
        label = "النماذج",
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory
    ),
    BottomNavItem(
        destination = AppDestination.History,
        label = "السجل",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    ),
    BottomNavItem(
        destination = AppDestination.Benchmark,
        label = "الأداء",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
    ),
    BottomNavItem(
        destination = AppDestination.Settings,
        label = "الإعدادات",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val rootRoutes = remember {
        bottomNavItems.map { it.destination.route }.toSet()
    }

    val showBottomBar = shouldShowBottomBar(
        currentDestination = currentDestination,
        rootRoutes = rootRoutes
    )

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(220)
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220)
                ) + fadeOut(animationSpec = tween(180))
            ) {
                AppBottomBar(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppGraph.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            addMainGraph(navController)
            addChatGraph(navController)
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.addMainGraph(
    navController: NavHostController
) {
    navigation(
        route = AppGraph.Main.route,
        startDestination = AppDestination.ChatHome.route
    ) {
        composable(AppDestination.ChatHome.route) {
            ChatScreen(
                onOpenConversation = { conversationId ->
                    navController.navigate(
                        AppDestination.ChatConversation.createRoute(conversationId)
                    )
                }
            )
        }

        composable(AppDestination.Models.route) {
            ModelsScreen()
        }

        composable(AppDestination.History.route) {
            HistoryScreen(
                onOpenConversation = { conversationId ->
                    navController.navigate(
                        AppDestination.ChatConversation.createRoute(conversationId)
                    )
                }
            )
        }

        composable(AppDestination.Benchmark.route) {
            BenchmarkScreen()
        }

        composable(AppDestination.Settings.route) {
            SettingsScreen()
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.addChatGraph(
    navController: NavHostController
) {
    navigation(
        route = AppGraph.Chat.route,
        startDestination = AppDestination.ChatConversation.route
    ) {
        composable(
            route = AppDestination.ChatConversation.route,
            arguments = AppDestination.ChatConversation.arguments,
            deepLinks = AppDestination.ChatConversation.deepLinks
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments
                ?.getLong(AppDestination.ChatConversation.ARG_CONVERSATION_ID)
                ?: -1L

            ChatScreen(
                conversationId = conversationId,
                onOpenConversation = { nextConversationId ->
                    navController.navigate(
                        AppDestination.ChatConversation.createRoute(nextConversationId)
                    )
                }
            )
        }
    }
}

@Composable
private fun AppBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?
) {
    NavigationBar(
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == item.destination.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(item.label)
                },
                alwaysShowLabel = true,
                modifier = Modifier.semantics {
                    contentDescription = item.label
                    stateDescription = if (selected) {
                        "التبويب الحالي"
                    } else {
                        "غير محدد"
                    }
                }
            )
        }
    }
}

private fun shouldShowBottomBar(
    currentDestination: NavDestination?,
    rootRoutes: Set<String>
): Boolean {
    val currentRoute = currentDestination?.route
    return currentRoute == null || currentRoute in rootRoutes
}
