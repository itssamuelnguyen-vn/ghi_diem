package com.samuelcruz.ghidiem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
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

private val idArg = listOf(navArgument("id") { type = NavType.LongType })

/**
 * Routes (like routes.rb): home, new/{sport}, match/{id}, stats/{id}, board/{id}, tournaments, tournament/new,
 * tournament/{id}, players, settings. [openMatch] is a match the widget asked to open (0 = none).
 */
@Composable
fun AppNav(openMatch: MutableLongState) {
    val nav = rememberNavController()
    LaunchedEffect(openMatch.longValue) {
        val id = openMatch.longValue
        if (id > 0) {
            nav.navigate("match/$id") { popUpTo("home") }
            openMatch.longValue = 0
        }
    }
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNew = { nav.navigate("new/${it.name}") },
                onOpen = { nav.navigate("match/$it") },
                onTournaments = { nav.navigate("tournaments") },
                onPlayers = { nav.navigate("players") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("new/{sport}") { e ->
            NewMatchScreen(
                sport = Sport.of(e.arguments?.getString("sport") ?: ""),
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("match/$id") { popUpTo("home") } },
            )
        }
        composable("match/{id}", arguments = idArg) { e ->
            MatchScreen(
                id = e.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("match/$id") { popUpTo("home") } },
                onStats = { nav.navigate("stats/$it") },
                onBoard = { nav.navigate("board/$it") },
            )
        }
        composable("stats/{id}", arguments = idArg) { e ->
            StatsScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("board/{id}", arguments = idArg) { e ->
            BoardScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("tournaments") {
            TournamentsScreen(
                onBack = { nav.popBackStack() },
                onNew = { nav.navigate("tournament/new") },
                onOpen = { nav.navigate("tournament/$it") },
            )
        }
        composable("tournament/new") {
            NewTournamentScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("tournament/$id") { popUpTo("tournaments") } },
            )
        }
        composable("tournament/{id}", arguments = idArg) { e ->
            TournamentScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onMatch = { nav.navigate("match/$it") })
        }
        composable("players") { PlayersScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
