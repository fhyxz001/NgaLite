package com.ngalite.app.ui

import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
fun NavGraph() {
    val nav = rememberNavController()
    var lastNavTime by remember { mutableStateOf(0L) }
    val minNavInterval = 500L

    /**
     * 统一的导航防抖包装：对 navigate / popBackStack 等所有导航操作统一约束，
     * 避免快速连续点击导致导航转场动画未完成就发起下一次导航，造成白屏卡死。
     */
    fun navSafe(block: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastNavTime < minNavInterval) return
        lastNavTime = now
        runCatching { block() }.onFailure { e ->
            Log.e("NavGraph", "导航操作失败", e)
        }
    }

    /**
     * 安全获取 ForumThreads 路由的 backStackEntry，用于 Detail 页读取当前板块名称。
     */
    fun safeForumThreadsEntry(navController: NavController) = runCatching {
        navController.getBackStackEntry(Routes.FORUM_THREADS)
    }.getOrNull()

    val currentBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.COMMUNITY, Routes.SETTINGS)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(56.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
                                modifier = Modifier.padding(top = 4.dp),
                                imageVector = Icons.Default.Forum,
                                contentDescription = "社区"
                            )
                        }
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
                                modifier = Modifier.padding(top = 4.dp),
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置"
                            )
                        }
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
            composable(Routes.COMMUNITY) {
                CommunityScreen(
                    onForumClick = { fid ->
                        navSafe { nav.navigate(Routes.forumThreads(fid)) }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = null,
                    onLoginClick = { navSafe { nav.navigate(Routes.LOGIN_WEB) } }
                )
            }
            composable(
                route = Routes.LOGIN_WEB
            ) {
                LoginWebScreen(onBack = { navSafe { nav.popBackStack() } })
            }
            composable(
                route = Routes.FORUM_THREADS,
                arguments = listOf(
                    navArgument("fid") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val fid = backStackEntry.arguments?.getString("fid").orEmpty()
                ForumThreadsScreen(
                    fid = fid,
                    onTopicClick = { tid ->
                        navSafe { nav.navigate(Routes.detail(tid)) }
                    }
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType }
                )
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
