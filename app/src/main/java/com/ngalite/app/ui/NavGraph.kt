package com.ngalite.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val LOGIN_WEB = "login_web"
    const val DETAIL = "detail/{tid}"
    fun detail(tid: String) = "detail/$tid"
}

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ListScreen(
                onTopicClick = { tid -> nav.navigate(Routes.detail(tid)) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) }
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
            arguments = listOf(navArgument("tid") { type = NavType.StringType })
        ) { backStackEntry ->
            val tid = backStackEntry.arguments?.getString("tid").orEmpty()
            DetailScreen(tid = tid, onBack = { nav.popBackStack() })
        }
    }
}
