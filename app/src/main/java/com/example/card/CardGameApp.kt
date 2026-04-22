package com.example.card

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.card.screens.*

@Composable
fun CardGameApp() {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel()
    
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("startup") {
            StartupScreen(navController = navController, viewModel = viewModel)
        }
        composable("player_selection") {
            PlayerSelectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            ) { playerCount, difficulty, showHints ->
                navController.navigate("game_board/$playerCount/$difficulty/$showHints") {
                    popUpTo("startup")
                }
            }
        }
        composable("game_board/{playerCount}/{difficulty}/{showHints}") { backStackEntry ->
            val playerCount = backStackEntry.arguments?.getString("playerCount")?.toIntOrNull() ?: 4
            val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: "HARD"
            val difficulty = try { Difficulty.valueOf(difficultyStr) } catch (_: Exception) { Difficulty.HARD }
            val showHints = backStackEntry.arguments?.getString("showHints")?.toBoolean() ?: false
            GameBoardScreen(playerCount = playerCount, difficulty = difficulty, showHints = showHints, navController = navController, viewModel = viewModel)
        }
        composable("history") {
            GameHistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
