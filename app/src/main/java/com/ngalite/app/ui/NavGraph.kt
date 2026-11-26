package com.ngalite.app.ui

import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val LOGIN_WEB = "login_web"
    const val FORUM_THREADS = "forum_threads/{fid}"
    const val DETAIL = "detail/{tid}?forumName={forumName}"

    fun forumThreads(fid: String) = "forum_threads/$fid"
    fun detail(tid: String, forumName: String = ""): String {
        val encoded = Uri.encode(forumName)
        return "detail/$tid?forumName=$encoded"
    }
}

private data class BottomTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomTabs = listOf(
    BottomTabItem(
        route = Routes.COMMUNITY,
        label = "社区",
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    ),
    BottomTabItem(
        route = Routes.RECORDS,
        label = "足迹",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks
    ),
    BottomTabItem(
        route = Routes.SETTINGS,
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@Composable
fun NavGraph(initialFid: String? = null) {
    val nav = rememberNavController()

    fun navSafe(block: () -> Unit) {
        runCatching { block() }.onFailure { e ->
            Log.e("NavGraph", "导航操作失败", e)
        }
    }

    LaunchedEffect(initialFid) {
        if (!initialFid.isNullOrBlank()) {
            navSafe { nav.navigate(Routes.forumThreads(initialFid)) }
        }
    }

    fun safeForumThreadsEntry(navController: NavController) = runCatching {
        navController.getBackStackEntry(Routes.FORUM_THREADS)
    }.getOrNull()

    fun AnimatedContentTransitionScope<*>.tabEnter(): EnterTransition =
        fadeIn(tween(210, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(210, easing = FastOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.tabExit(): ExitTransition =
        fadeOut(tween(150))

    fun AnimatedContentTransitionScope<*>.pageEnter(): EnterTransition =
        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
            fadeIn(tween(240))

    fun AnimatedContentTransitionScope<*>.pageExit(): ExitTransition =
        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
            fadeOut(tween(200))

    fun AnimatedContentTransitionScope<*>.pagePopEnter(): EnterTransition =
        slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
            fadeIn(tween(220))

    fun AnimatedContentTransitionScope<*>.pagePopExit(): ExitTransition =
        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
            fadeOut(tween(220))

    val currentBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.COMMUNITY, Routes.RECORDS, Routes.SETTINGS)

    Scaffold(
        containerColor = ForumColors.Page,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(ForumColors.Surface)
                ) {
                    HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = ForumColors.Surface,
                        contentColor = ForumColors.TextPrimary,
                        tonalElevation = 0.dp
                    ) {
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navSafe {
                                        nav.navigate(
                                            tab.route,
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
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ForumColors.Accent,
                                    selectedTextColor = ForumColors.Accent,
                                    indicatorColor = ForumColors.AccentSoft,
                                    unselectedIconColor = ForumColors.Meta,
                                    unselectedTextColor = ForumColors.Meta
                                )
                            )
                        }
                    }
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
                Routes.RECORDS,
                enterTransition = { tabEnter() },
                exitTransition = { tabExit() },
                popEnterTransition = { tabEnter() },
                popExitTransition = { tabExit() }
            ) {
                RecordsScreen(
                    onTopicClick = { tid, forumName ->
                        navSafe { nav.navigate(Routes.detail(tid, forumName)) }
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
                    onTopicClick = { tid, forumName ->
                        navSafe { nav.navigate(Routes.detail(tid, forumName)) }
                    }
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType },
                    navArgument("forumName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { pageEnter() },
                exitTransition = { pageExit() },
                popEnterTransition = { pagePopEnter() },
                popExitTransition = { pagePopExit() }
            ) { backStackEntry ->
                val tid = backStackEntry.arguments?.getString("tid").orEmpty()
                val argForumName = backStackEntry.arguments?.getString("forumName").orEmpty()
                val forumName = argForumName.ifBlank {
                    safeForumThreadsEntry(nav)?.let { entry ->
                        viewModel<ListViewModel>(entry).currentForum.value.name
                    }.orEmpty()
                }
                DetailScreen(
                    tid = tid,
                    forumName = forumName,
                    onBack = { navSafe { nav.popBackStack() } }
                )
            }
        }
    }
}
