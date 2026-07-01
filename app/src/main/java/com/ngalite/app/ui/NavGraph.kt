package com.ngalite.app.ui

import androidx.compose.runtime.Composable
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
    const val DETAIL = "detail/{tid}/{forumName}"
    fun detail(tid: String, forumName: String) = "detail/$tid/$forumName"
}

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) { backStackEntry ->
            val vm: ListViewModel = viewModel(backStackEntry)
            ListScreen(
                vm = vm,
                onTopicClick = { tid -> nav.navigate(Routes.detail(tid, vm.currentForum.value.name)) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onForumSelectClick = { nav.navigate(Routes.FORUM_SELECT) }
            )
        }
        composable(Routes.FORUM_SELECT) { backStackEntry ->
            val listEntry = nav.getBackStackEntry(Routes.LIST)
            val vm: ListViewModel = viewModel(listEntry)
            ForumSelectScreen(
                currentForum = vm.currentForum.value,
                onBack = { nav.popBackStack() },
                onForumSelected = { forum ->
                    vm.switchForum(forum)
                    nav.popBackStack()
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLoginClick = { nav.navigate(Routes.LOGIN_WEB) }
            )
        }
        composable(Routes.LOGIN_WEB) {
            LoginWebScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("tid") { type = NavType.StringType },
                navArgument("forumName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tid = backStackEntry.arguments?.getString("tid").orEmpty()
            val forumName = backStackEntry.arguments?.getString("forumName").orEmpty()
            DetailScreen(tid = tid, forumName = forumName, onBack = { nav.popBackStack() })
        }
    }
}
