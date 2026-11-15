package com.samuelcruz.ghidiem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samuelcruz.ghidiem.GhiDiemApp
import com.samuelcruz.ghidiem.core.Sport

val Context.app get() = applicationContext as GhiDiemApp

@Composable
fun app(): GhiDiemApp = LocalContext.current.app

/** Routes (like routes.rb): home, new/{sport}, match/{id}, stats/{id}, board/{id}. */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNew = { nav.navigate("new/${it.name}") },
                onOpen = { nav.navigate("match/$it") },
            )
        }
        composable("new/{sport}") { e ->
            NewMatchScreen(
                sport = Sport.of(e.arguments?.getString("sport") ?: ""),
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("match/$id") { popUpTo("home") } },
            )
        }
        composable("match/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            MatchScreen(
                id = e.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("match/$id") { popUpTo("home") } },
                onStats = { nav.navigate("stats/$it") },
                onBoard = { nav.navigate("board/$it") },
            )
        }
        composable("stats/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            StatsScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("board/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            BoardScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
    }
}
