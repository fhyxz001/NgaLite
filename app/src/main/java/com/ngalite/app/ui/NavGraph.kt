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
            SettingsScreen(onBack = { nav.popBackStack() })
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
