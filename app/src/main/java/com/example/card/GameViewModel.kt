package com.example.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class GameViewModel : ViewModel() {
    var gameState by mutableStateOf<GameState?>(null)
        private set

    var showHelp by mutableStateOf(false)
    var showPauseMenu by mutableStateOf(false)
    var hasClosedHelpOnce by mutableStateOf(false)

    fun initGame(playerCount: Int, showHints: Boolean) {
        if (gameState == null) {
            val newState = GameState(viewModelScope, showHints)
            // setupGame internally calls dealCards and sets isInitializing = false
            newState.setupGame(playerCount)
            gameState = newState
            showHelp = showHints
            hasClosedHelpOnce = !showHints
        }
    }

    fun toggleHelp(show: Boolean) {
        showHelp = show
        if (!show) hasClosedHelpOnce = true
    }

    fun togglePauseMenu(show: Boolean) {
        showPauseMenu = show
    }
}
