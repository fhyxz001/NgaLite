package com.ngalite.app.ui

import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions

object Routes {
    const val COMMUNITY = "community"
    const val SETTINGS = "settings"
    const val LOGIN_WEB = "login_web"
    const val FORUM_THREADS = "forum_threads/{fid}"
    const val DETAIL = "detail/{tid}"

    fun forumThreads(fid: String) = "forum_threads/$fid"
    fun detail(tid: String) = "detail/$tid"
}

@Composable
fun NavGraph(initialFid: String? = null) {
    val nav = rememberNavController()

    /** 安全导航包装：捕获导航异常防止界面崩溃，不跨操作吞掉异常 */
    fun navSafe(block: () -> Unit) {
        runCatching { block() }.onFailure { e ->
            Log.e("NavGraph", "导航操作失败", e)
        }
    }

    // 从桌面快捷方式启动时，直接导航到帖子列表页
    LaunchedEffect(initialFid) {
        if (!initialFid.isNullOrBlank()) {
            navSafe { nav.navigate(Routes.forumThreads(initialFid)) }
        }
    }

    /**
     * 安全获取 ForumThreads 路由的 backStackEntry，用于 Detail 页读取当前板块名称。
     */
    fun safeForumThreadsEntry(navController: NavController) = runCatching {
        navController.getBackStackEntry(Routes.FORUM_THREADS)
    }.getOrNull()

    val transitionMs = 150

    fun AnimatedContentTransitionScope<*>.tabEnter(): EnterTransition =
        fadeIn(animationSpec = tween(transitionMs, easing = LinearOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.tabExit(): ExitTransition =
        fadeOut(animationSpec = tween(transitionMs, easing = FastOutLinearInEasing))

    fun AnimatedContentTransitionScope<*>.pageEnter(): EnterTransition =
        fadeIn(animationSpec = tween(transitionMs, easing = LinearOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.pageExit(): ExitTransition =
        fadeOut(animationSpec = tween(transitionMs, easing = FastOutLinearInEasing))

    fun AnimatedContentTransitionScope<*>.pagePopEnter(): EnterTransition =
        fadeIn(animationSpec = tween(transitionMs, easing = LinearOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.pagePopExit(): ExitTransition =
        fadeOut(animationSpec = tween(transitionMs, easing = FastOutLinearInEasing))

    val currentBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.COMMUNITY, Routes.SETTINGS)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = Color.Black.copy(alpha = 0.18f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.52f),
                            shape = RoundedCornerShape(28.dp),
                        ),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.COMMUNITY,
                        onClick = {
                            navSafe {
                                nav.navigate(
                                    Routes.COMMUNITY,
                                    navOptions {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                )
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "社区"
                            )
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navSafe {
                                nav.navigate(
                                    Routes.SETTINGS,
                                    navOptions {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                )
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置"
                            )
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.COMMUNITY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                Routes.COMMUNITY,
                enterTransition = { tabEnter() },
                exitTransition = { tabExit() },
                popEnterTransition = { tabEnter() },
                popExitTransition = { tabExit() }
            ) {
                CommunityScreen(
                    onForumClick = { fid ->
                        navSafe { nav.navigate(Routes.forumThreads(fid)) }
                    }
                )
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { tabEnter() },
                exitTransition = { tabExit() },
                popEnterTransition = { tabEnter() },
                popExitTransition = { tabExit() }
            ) {
                SettingsScreen(
                    onBack = null,
                    onLoginClick = { navSafe { nav.navigate(Routes.LOGIN_WEB) } }
                )
            }
            composable(
                route = Routes.LOGIN_WEB,
                enterTransition = { pageEnter() },
                exitTransition = { pageExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() }
            ) {
                LoginWebScreen(onBack = { navSafe { nav.popBackStack() } })
            }
            composable(
                route = Routes.FORUM_THREADS,
                arguments = listOf(
                    navArgument("fid") { type = NavType.StringType }
                ),
                enterTransition = { pageEnter() },
                exitTransition = { pageExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() }
            ) { backStackEntry ->
                val fid = backStackEntry.arguments?.getString("fid").orEmpty()
                ForumThreadsScreen(
                    fid = fid,
                    onBack = { navSafe { nav.popBackStack() } },
                    onTopicClick = { tid ->
                        navSafe { nav.navigate(Routes.detail(tid)) }
                    }
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType }
                ),
                enterTransition = { pageEnter() },
                exitTransition = { pageExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() }
            ) { backStackEntry ->
                val tid = backStackEntry.arguments?.getString("tid").orEmpty()
                // 从 ForumThreads 的 ListViewModel 获取当前板块名称，避免通过路由传递
                val forumName = safeForumThreadsEntry(nav)?.let { entry ->
                    viewModel<ListViewModel>(entry).currentForum.value.name
                } ?: ""
                DetailScreen(
                    tid = tid,
                    forumName = forumName,
                    onBack = { navSafe { nav.popBackStack() } }
                )
            }
        }
    }
}
