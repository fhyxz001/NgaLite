package com.ngalite.app.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val LOGIN_WEB = "login_web"
    const val FORUM_SELECT = "forum_select"
    const val DETAIL = "detail/{tid}"
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
     * 安全获取指定路由的 backStackEntry。
     * 在进程恢复、配置变化等边界场景下 [androidx.navigation.NavController.getBackStackEntry]
     * 可能抛 IllegalArgumentException，这里用 runCatching 兜底，失败时返回 null。
     */
    fun safeListEntry() = runCatching { nav.getBackStackEntry(Routes.LIST) }.getOrNull()

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) { backStackEntry ->
            val vm: ListViewModel = viewModel(backStackEntry)
            ListScreen(
                vm = vm,
                onTopicClick = { tid ->
                    navSafe { nav.navigate(Routes.detail(tid)) }
                },
                onSettingsClick = { navSafe { nav.navigate(Routes.SETTINGS) } },
                onForumSelectClick = { navSafe { nav.navigate(Routes.FORUM_SELECT) } }
            )
        }
        composable(Routes.FORUM_SELECT) {
            // 若拿不到 LIST entry（边界场景，如进程恢复），用 LaunchedEffect 安全返回上一页
            val listEntry = safeListEntry()
            if (listEntry == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                val vm: ListViewModel = viewModel(listEntry)
                // 用 collectAsState 响应式订阅当前板块，避免读到瞬态旧值
                val currentForum by vm.currentForum.collectAsState()
                ForumSelectScreen(
                    currentForum = currentForum,
                    onBack = { navSafe { nav.popBackStack() } },
                    onForumSelected = { forum ->
                        vm.switchForum(forum)
                        navSafe { nav.popBackStack() }
                    }
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navSafe { nav.popBackStack() } },
                onLoginClick = { navSafe { nav.navigate(Routes.LOGIN_WEB) } }
            )
        }
        composable(Routes.LOGIN_WEB) {
            LoginWebScreen(onBack = { navSafe { nav.popBackStack() } })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("tid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tid = backStackEntry.arguments?.getString("tid").orEmpty()
            // 从 ListViewModel 获取当前板块名称，避免通过路由传递（消除 URL 编码问题）
            val listEntry = safeListEntry()
            val forumName = if (listEntry != null) {
                viewModel<ListViewModel>(listEntry).currentForum.value.name
            } else ""
            DetailScreen(tid = tid, forumName = forumName, onBack = { navSafe { nav.popBackStack() } })
        }
    }
}
