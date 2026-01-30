package com.example.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

data class UserStats(
    val learnGames: Int = 0,
    val learnPoints: Int = 0,
    val playGames: Int = 0,
    val playPoints: Int = 0
)

class GameViewModel : ViewModel() {
    var gameState by mutableStateOf<GameState?>(null)
        private set

    var showHelp by mutableStateOf(false)
    var showPauseMenu by mutableStateOf(false)
    var hasClosedHelpOnce by mutableStateOf(false)
    
    // Track which highlights have been explained
    val explainedHighlights = mutableStateMapOf<String, Boolean>()
    var explainedDubliStrategy by mutableStateOf(false)
    var showDubliOverlay by mutableStateOf(false)

    // Selection Dialog for Discards
    var showDiscardSelection by mutableStateOf(false)
    var discardCandidates = mutableStateListOf<Card>()

    var userStats by mutableStateOf(UserStats())
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        fetchUserStats()
    }

    private fun fetchUserStats() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val document = db.collection("users").document(userId).get().await()
                if (document.exists()) {
                    userStats = UserStats(
                        learnGames = (document.getLong("learnGames") ?: 0).toInt(),
                        learnPoints = (document.getLong("learnPoints") ?: 0).toInt(),
                        playGames = (document.getLong("playGames") ?: 0).toInt(),
                        playPoints = (document.getLong("playPoints") ?: 0).toInt()
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateStats(isLearnMode: Boolean, pointsGained: Int) {
        val userId = auth.currentUser?.uid ?: return
        val currentStats = userStats
        val newStats = if (isLearnMode) {
            currentStats.copy(
                learnGames = currentStats.learnGames + 1,
                learnPoints = currentStats.learnPoints + pointsGained
            )
        } else {
            currentStats.copy(
                playGames = currentStats.playGames + 1,
                playPoints = currentStats.playPoints + pointsGained
            )
        }

        viewModelScope.launch {
            try {
                db.collection("users").document(userId).set(newStats, SetOptions.merge()).await()
                userStats = newStats
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private var pendingPlayerCount: Int? = null

    fun initGame(playerCount: Int, showHints: Boolean) {
        if (gameState == null) {
            val newState = GameState(viewModelScope, showHints)
            gameState = newState
            explainedHighlights.clear()
            explainedDubliStrategy = false
            showDubliOverlay = false
            showDiscardSelection = false
            discardCandidates.clear()
            
            if (showHints) {
                showHelp = true
                hasClosedHelpOnce = false
                pendingPlayerCount = playerCount
            } else {
                newState.setupGame(playerCount)
                showHelp = false
                hasClosedHelpOnce = true
            }
        }
    }

    fun toggleHelp(show: Boolean) {
        showHelp = show
        if (!show) {
            hasClosedHelpOnce = true
            pendingPlayerCount?.let {
                gameState?.setupGame(it)
                pendingPlayerCount = null
            }
        }
    }

    fun togglePauseMenu(show: Boolean) {
        showPauseMenu = show
    }

    fun markHighlightExplained(colorKey: String) {
        explainedHighlights[colorKey] = true
    }

    fun markDubliStrategyExplained() {
        explainedDubliStrategy = true
    }

    fun openDiscardSelection(cards: List<Card>) {
        discardCandidates.clear()
        discardCandidates.addAll(cards)
        showDiscardSelection = true
    }

    fun closeDiscardSelection() {
        showDiscardSelection = false
        discardCandidates.clear()
    }
}
