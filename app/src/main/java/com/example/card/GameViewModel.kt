package com.example.card

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

data class UserStats(
    val learnGames: Int = 0,
    val learnPoints: Int = 0,
    val easyGames: Int = 0,
    val easyPoints: Int = 0,
    val mediumGames: Int = 0,
    val mediumPoints: Int = 0,
    val hardGames: Int = 0,
    val hardPoints: Int = 0
)

data class GameRecord(
    val mode: String = "",
    val points: Int = 0,
    val timestamp: Long = 0L
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
    val discardCandidates = mutableStateListOf<Card>()

    var userStats by mutableStateOf(UserStats())
        private set

    var gameHistory = mutableStateListOf<GameRecord>()
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val authListener = FirebaseAuth.AuthStateListener {
        if (it.currentUser != null) {
            refreshData()
        } else {
            userStats = UserStats()
            gameHistory.clear()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        refreshData()
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    fun refreshData() {
        fetchUserStats()
        fetchGameHistory()
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
                        easyGames = (document.getLong("easyGames") ?: 0).toInt(),
                        easyPoints = (document.getLong("easyPoints") ?: 0).toInt(),
                        mediumGames = (document.getLong("mediumGames") ?: 0).toInt(),
                        mediumPoints = (document.getLong("mediumPoints") ?: 0).toInt(),
                        hardGames = (document.getLong("hardGames") ?: 0).toInt(),
                        hardPoints = (document.getLong("hardPoints") ?: 0).toInt()
                    )
                }
            } catch (e: Exception) {
                Log.e("GameViewModel", "Error fetching user stats", e)
            }
        }
    }

    private fun fetchGameHistory() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(userId).collection("history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()
                
                gameHistory.clear()
                val records = snapshot.toObjects(GameRecord::class.java)
                gameHistory.addAll(records)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Error fetching game history", e)
            }
        }
    }

    fun updateStats(isLearnMode: Boolean, difficulty: Difficulty, pointsGained: Int) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w("GameViewModel", "No user logged in, stats not saved.")
            return
        }
        val currentStats = userStats
        val modeStr = if (isLearnMode) "LEARN" else difficulty.name
        
        val newStats = if (isLearnMode) {
            currentStats.copy(
                learnGames = currentStats.learnGames + 1,
                learnPoints = currentStats.learnPoints + pointsGained
            )
        } else {
            when (difficulty) {
                Difficulty.EASY -> currentStats.copy(
                    easyGames = currentStats.easyGames + 1,
                    easyPoints = currentStats.easyPoints + pointsGained
                )
                Difficulty.MEDIUM -> currentStats.copy(
                    mediumGames = currentStats.mediumGames + 1,
                    mediumPoints = currentStats.mediumPoints + pointsGained
                )
                Difficulty.HARD -> currentStats.copy(
                    hardGames = currentStats.hardGames + 1,
                    hardPoints = currentStats.hardPoints + pointsGained
                )
            }
        }

        val newRecord = GameRecord(
            mode = modeStr,
            points = pointsGained,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                // Update totals
                db.collection("users").document(userId).set(newStats, SetOptions.merge()).await()

                // Add to history
                db.collection("users").document(userId).collection("history").add(newRecord).await()

                userStats = newStats
                gameHistory.add(0, newRecord)
                Log.d("GameViewModel", "Successfully updated Firebase with new game record.")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Error updating stats in Firebase", e)
            }
        }
    }

    private var pendingPlayerCount: Int? = null
    private var pendingDifficulty: Difficulty? = null

    fun initGame(playerCount: Int, showHints: Boolean, difficulty: Difficulty = Difficulty.MEDIUM) {
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
                pendingDifficulty = difficulty
            } else {
                newState.setupGame(playerCount, difficulty)
                showHelp = false
                hasClosedHelpOnce = true
            }
        }
    }

    fun toggleHelp(show: Boolean) {
        showHelp = show
        if (!show) {
            hasClosedHelpOnce = true
            pendingPlayerCount?.let { count ->
                gameState?.setupGame(count, pendingDifficulty ?: Difficulty.MEDIUM)
                pendingPlayerCount = null
                pendingDifficulty = null
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
