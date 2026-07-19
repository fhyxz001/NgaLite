package com.ngalite.app.ui

import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
fun NavGraph() {
    val nav = rememberNavController()

    /** 閬垮厤鍗曟瀵艰埅寮傚父瀵艰嚧鐣岄潰宕╂簝锛屼笉璺ㄦ搷浣滃悶鎺夋甯稿鑸€?*/
    fun navSafe(block: () -> Unit) {
        runCatching { block() }.onFailure { e ->
            Log.e("NavGraph", "导航操作失败", e)
        }
    }

    /**
     * 安全获取 ForumThreads 璺敱鐨?backStackEntry锛岀敤浜?Detail 椤佃鍙栧綋鍓嶆澘鍧楀悕绉般€?
     */
    fun safeForumThreadsEntry(navController: NavController) = runCatching {
        navController.getBackStackEntry(Routes.FORUM_THREADS)
    }.getOrNull()

    val tabTransitionMs = 180
    val pageEnterMs = 280
    val pageExitMs = 220

    fun AnimatedContentTransitionScope<*>.tabEnter(): EnterTransition =
        fadeIn(animationSpec = tween(tabTransitionMs, easing = LinearOutSlowInEasing)) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(tabTransitionMs, easing = LinearOutSlowInEasing)
            )

    fun AnimatedContentTransitionScope<*>.tabExit(): ExitTransition =
        fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing)) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(120, easing = FastOutLinearInEasing)
            )

    fun AnimatedContentTransitionScope<*>.pageEnter(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(pageEnterMs, easing = LinearOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.pageExit(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -it / 12 },
            animationSpec = tween(pageExitMs, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(pageExitMs, easing = FastOutSlowInEasing)
            )

    fun AnimatedContentTransitionScope<*>.pagePopEnter(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { -it / 12 },
            animationSpec = tween(pageEnterMs, easing = LinearOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing))

    fun AnimatedContentTransitionScope<*>.pagePopExit(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it / 4 },
            animationSpec = tween(pageExitMs, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(pageExitMs, easing = FastOutSlowInEasing)
            )

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
                // 浠?ForumThreads 鐨?ListViewModel 鑾峰彇褰撳墠鏉垮潡鍚嶇О锛岄伩鍏嶉€氳繃璺敱浼犻€?
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
