package com.example.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

fun MutableList<Card>.removeByReference(cardsToRemove: List<Card>) {
    cardsToRemove.forEach { cardToRemove ->
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() === cardToRemove) {
                iterator.remove()
                break
            }
        }
    }
}

enum class TurnPhase {
    INITIAL_CHECK,
    DRAW,
    PLAY_OR_DISCARD,
    SHOW_OR_END,
    ENDED
}

enum class AnimationType { DRAW, DISCARD }
enum class AnimationSource { STOCK, DISCARD, PLAYER }

data class AnimationState(
    val card: Card,
    val player: Int,
    val type: AnimationType,
    val source: AnimationSource,
    val isFaceUp: Boolean
)

data class Hint(
    val title: String,
    val message: String,
    val cards: List<Card> = emptyList()
)

class GameState(private val viewModelScope: CoroutineScope, val showHints: Boolean) {

    val playerHands = mutableStateMapOf<Int, SnapshotStateList<Card>>()
    val shownCards = mutableStateMapOf<Int, SnapshotStateList<Card>>()
    val stockPile = mutableStateListOf<Card>()
    val discardPile = mutableStateListOf<Card>()

    var playerCount by mutableIntStateOf(0)
    var currentPlayer by mutableIntStateOf(1)
    var currentTurnPhase by mutableStateOf(TurnPhase.DRAW)
    var animationState by mutableStateOf<AnimationState?>(null)
    var hint by mutableStateOf<Hint?>(null)
    
    val meldedCards = mutableStateListOf<Card>()
    val dubliCards = mutableStateListOf<Card>()
    
    var winner by mutableStateOf<Int?>(null)
    var isInitializing by mutableStateOf(true)
    var gameMessage by mutableStateOf<String?>(null)

    var maalCard by mutableStateOf<Card?>(null)
    val hasShown = mutableStateMapOf<Int, Boolean>()
    val isDubliShow = mutableStateMapOf<Int, Boolean>()
    
    // To track starting bonuses (Tunnelas)
    val startingBonuses = mutableStateMapOf<Int, Int>()

    val selectedCards = mutableStateListOf<Card>()
    val playerIcons = mutableStateMapOf<Int, String>()
    private val availableIcons = listOf("🤖", "👽", "👾", "👺", "👺", "👻")

    var isFirstTurn = mutableStateMapOf<Int, Boolean>()
        private set

    var lastDrawnCard by mutableStateOf<Card?>(null)
        private set

    // AI Difficulty
    var difficulty by mutableStateOf(Difficulty.HARD)

    // To prevent AI loops
    val lastDiscardedCardIdByPlayer = mutableStateMapOf<Int, String?>()

    // Callback to open selection dialog in UI
    var onShowDiscardSelection: ((List<Card>) -> Unit)? = null

    // Asynchronous state for UI buttons to avoid ANR
    var canWinWithSelected by mutableStateOf(false)

    fun setupGame(count: Int, difficulty: Difficulty = Difficulty.HARD) {
        viewModelScope.launch {
            try {
                isInitializing = true
                withContext(Dispatchers.Default) {
                    this@GameState.playerCount = count
                    this@GameState.difficulty = difficulty
                    val numberOfDecks = when (playerCount) {
                        in 2..4 -> 3
                        5 -> 4
                        else -> 5
                    }
                    val cardPool = createCardPool(numberOfDecks, numberOfDecks)
                    
                    withContext(Dispatchers.Main) {
                        dealCards(playerCount, cardPool)
                        assignPlayerIcons(count)
                        
                        hasShown.clear()
                        isDubliShow.clear()
                        shownCards.clear()
                        startingBonuses.clear()
                        meldedCards.clear()
                        dubliCards.clear()
                        selectedCards.clear()
                        isFirstTurn.clear()
                        lastDiscardedCardIdByPlayer.clear()
                        canWinWithSelected = false
                        
                        for (i in 1..playerCount) {
                            hasShown[i] = false
                            isDubliShow[i] = false
                            shownCards[i] = mutableStateListOf()
                            isFirstTurn[i] = true
                            lastDiscardedCardIdByPlayer[i] = null
                            
                            val hand = playerHands[i]?.toList() ?: emptyList()
                            val tunnelas = hand.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
                            var bonus = 0
                            tunnelas.keys.forEach { (rank, _) -> 
                                bonus += if (rank == Rank.JOKER) 30 else 5 
                            }
                            startingBonuses[i] = bonus
                        }
                        
                        maalCard = null
                        winner = null
                        gameMessage = null
                        currentPlayer = Random.nextInt(1, count + 1) 
                        isInitializing = false
                        lastDrawnCard = null

                        // Enter Initial Check phase for everyone on turn 1
                        currentTurnPhase = TurnPhase.INITIAL_CHECK
                        processInitialCheck()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isInitializing = false
            }
        }
    }

    private fun processInitialCheck() {
        if (currentPlayer == 1) {
            updateHint() // Human will see tunnel hints if any
        } else {
            // AI automatically reveals tunnels if any
            val aiHand = playerHands[currentPlayer] ?: return
            val currentShown = shownCards[currentPlayer] ?: return
            
            // 1. Check for Dubli Show in starting hand
            val aiHandList = aiHand.toList()
            val dublis = AiPlayer.findDublis(aiHandList)
            if (dublis.size >= 7) {
                val meldedCards = dublis.take(7).flatten()
                aiHand.removeByReference(meldedCards)
                currentShown.addAll(meldedCards)
                hasShown[currentPlayer] = true
                isDubliShow[currentPlayer] = true
                showGameMessage("Player $currentPlayer has shown Dubli!")
                if (aiHand.size == 7) pickMaalCard()
            } else {
                // 2. Check for 3 Melds in starting hand
                val initialMelds = AiPlayer.findAllInitialMelds(aiHandList)
                if (initialMelds.size >= 3) {
                    val meldedCards = initialMelds.take(3).flatten()
                    aiHand.removeByReference(meldedCards)
                    currentShown.addAll(meldedCards)
                    hasShown[currentPlayer] = true
                    showGameMessage("Player $currentPlayer has shown!")
                    if (aiHand.size == 12) pickMaalCard()
                } else {
                    // 3. Check for Tunnels (bonus points)
                    var tunnelsFound = 0
                    while (tunnelsFound < 3) {
                        val currentList = aiHand.toList()
                        val jokers = currentList.filter { it.rank == Rank.JOKER }
                        val identical = currentList.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
                        
                        val tunnelCards = when {
                            jokers.size >= 3 -> jokers.take(3)
                            identical.isNotEmpty() -> identical.values.first().take(3)
                            else -> null
                        }
                        
                        if (tunnelCards != null) {
                            aiHand.removeByReference(tunnelCards)
                            currentShown.addAll(tunnelCards)
                            tunnelsFound++
                        } else break
                    }
                    
                    if (currentShown.size == 9) {
                        hasShown[currentPlayer] = true
                        pickMaalCard()
                    }
                }
            }
            
            // Move to DRAW phase for AI
            viewModelScope.launch {
                try {
                    delay(1000)
                    currentTurnPhase = TurnPhase.DRAW
                    processAiTurn()
                } catch (e: Exception) {
                    e.printStackTrace()
                    advanceTurn()
                }
            }
        }
    }

    private fun assignPlayerIcons(playerCount: Int) {
        playerIcons.clear()
        playerIcons[1] = "🧑"
        val aiIcons = availableIcons.shuffled().iterator()
        (2..playerCount).forEach { player ->
            playerIcons[player] = if (aiIcons.hasNext()) aiIcons.next() else "🤖"
        }
    }

    fun isJoker(card: Card, player: Int): Boolean {
        if (card.rank == Rank.JOKER) return true
        if (isDubliShow[player] == true) return false
        val playerHasShown = hasShown[player] == true
        if (!playerHasShown) return false
        val m = maalCard ?: return false
        return GameEngine.isMaal(card, m)
    }

    private fun checkWin(player: Int) {
        val hand = playerHands[player]?.toList() ?: return
        
        viewModelScope.launch {
            try {
                // Check if the hand can finish using available Jokers to bridge gaps
                val isWin = withContext(Dispatchers.Default) { 
                    AiPlayer.canFinish(hand, this@GameState, player) 
                }
                
                if (isWin) {
                    withContext(Dispatchers.Main) {
                        winner = player
                        explainFinalScores()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun explainFinalScores() {
        val w = winner ?: return
        val pHands = playerHands.mapValues { it.value.toList() }
        val sCards = shownCards.mapValues { it.value.toList() }
        val hShown = hasShown.toMap()
        val isDubli = isDubliShow.toMap()
        val sBonuses = startingBonuses.toMap()
        
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.Default) {
                    GameEngine.getFinalScoreReason(w, playerCount, pHands, sCards, hShown, maalCard, isDubli, sBonuses)
                }
                withContext(Dispatchers.Main) {
                    hint = Hint(title = "Game Over!", message = message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun moveCard(card: Card, player: Int, type: AnimationType, source: AnimationSource, onLogicUpdate: () -> Unit) {
        val isFaceUp = if (type == AnimationType.DRAW) player == 1 else true
        viewModelScope.launch {
            try {
                animationState = AnimationState(card, player, type, source, isFaceUp)
                delay(500)
                onLogicUpdate()
                animationState = null
                if (type == AnimationType.DRAW) {
                    if (player == 1) lastDrawnCard = card
                    isFirstTurn[player] = false 
                    currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                    if (player == 1) updateHint()
                } else {
                    if (player == 1) lastDrawnCard = null
                    lastDiscardedCardIdByPlayer[player] = card.id
                    handlePostDiscard(player)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                animationState = null
            }
        }
    }

    private fun handlePostDiscard(player: Int) {
        val hand = playerHands[player] ?: return
        if (player == 1) {
            selectedCards.clear()
            canWinWithSelected = false
            
            // Check win immediately after discarding
            checkWin(1)
            
            if (winner != null) return

            viewModelScope.launch {
                try {
                    val handList = hand.toList()
                    val alreadyShownCount = shownCards[1]?.size ?: 0
                    val reqMelds = 3 - (alreadyShownCount / 3)
                    
                    val canShowRegular = if (hasShown[1] == false) {
                        withContext(Dispatchers.Default) { 
                            AiPlayer.findAllInitialMelds(handList).size >= reqMelds 
                        }
                    } else false
                    
                    val canShowDubli = alreadyShownCount == 0 && hasShown[1] == false && 
                        withContext(Dispatchers.Default) { AiPlayer.findDublis(handList).size >= 7 }

                    withContext(Dispatchers.Main) {
                        if (hasShown[1] == false && (canShowRegular || canShowDubli)) {
                            currentTurnPhase = TurnPhase.SHOW_OR_END
                            updateHint()
                        } else {
                            currentTurnPhase = TurnPhase.ENDED
                            advanceTurn()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    advanceTurn()
                }
            }
        } else {
            // AI logic: check win first
            checkWin(player)
            if (winner != null) return

            if (hasShown[player] == false) {
                val handList = hand.toList()
                viewModelScope.launch {
                    try {
                        val dublis = withContext(Dispatchers.Default) { AiPlayer.findDublis(handList) }
                        val alreadyShownCount = shownCards[player]?.size ?: 0
                        
                        if (alreadyShownCount == 0 && dublis.size >= 7) {
                            withContext(Dispatchers.Main) {
                                val meldedCards = dublis.take(7).flatten()
                                hand.removeByReference(meldedCards)
                                shownCards[player]?.addAll(meldedCards)
                                hasShown[player] = true
                                isDubliShow[player] = true
                                showGameMessage("Player $player has shown Dubli!")
                                if (hand.size == 7) pickMaalCard()
                                
                                // AI always ends turn after showing
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            }
                        } else {
                            val reqMelds = 3 - (alreadyShownCount / 3)
                            val initialMelds = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(handList) }
                            withContext(Dispatchers.Main) {
                                if (initialMelds.size >= reqMelds) {
                                    val meldedCards = initialMelds.take(reqMelds).flatten()
                                    hand.removeByReference(meldedCards)
                                    shownCards[player]?.addAll(meldedCards)
                                    hasShown[player] = true
                                    showGameMessage("Player $player has shown!")
                                    if (hand.size == 12) pickMaalCard()
                                }
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        advanceTurn()
                    }
                }
            } else {
                currentTurnPhase = TurnPhase.ENDED
                advanceTurn()
            }
        }
    }

    private fun pickMaalCard() {
        if (maalCard != null || stockPile.isEmpty()) return
        var index = 0
        while (index < stockPile.size && stockPile[index].rank == Rank.JOKER) index++
        maalCard = if (index < stockPile.size) stockPile.removeAt(index) else stockPile.removeAt(0)
    }

    fun showGameMessage(msg: String, duration: Long = 2500) {
        viewModelScope.launch {
            try {
                gameMessage = msg
                delay(duration) 
                gameMessage = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun humanDrawsFromStock() {
        if (isInitializing || currentPlayer != 1 || (currentTurnPhase != TurnPhase.DRAW && currentTurnPhase != TurnPhase.INITIAL_CHECK) || winner != null || stockPile.isEmpty()) return
        val card = stockPile.first()
        moveCard(card, 1, AnimationType.DRAW, AnimationSource.STOCK) {
            stockPile.removeAt(0)
            playerHands[1]?.add(card)
        }
    }

    fun humanDrawsFromDiscard() {
        if (isInitializing || currentPlayer != 1 || (currentTurnPhase != TurnPhase.DRAW && currentTurnPhase != TurnPhase.INITIAL_CHECK) || winner != null || discardPile.isEmpty()) return
        val card = discardPile.last()
        moveCard(card, 1, AnimationType.DRAW, AnimationSource.DISCARD) {
            discardPile.removeAt(discardPile.lastIndex)
            playerHands[1]?.add(card)
        }
    }

    fun humanDiscardsCard(card: Card) {
        if (isInitializing || currentPlayer != 1 || currentTurnPhase != TurnPhase.PLAY_OR_DISCARD || winner != null) return
        val hand = playerHands[1] ?: return
        if (isJoker(card, 1)) {
            showGameMessage("Don't throw away a Joker!")
            return
        }
        if (hand.contains(card)) {
            moveCard(card, 1, AnimationType.DISCARD, AnimationSource.PLAYER) {
                val idx = hand.indexOfFirst { it === card }
                if (idx != -1) hand.removeAt(idx)
                discardPile.add(card)
                if (maalCard == null) {
                    val targetSize = if (isDubliShow[1] == true) 7 else if (hasShown[1] == true) 12 else -1
                    if (targetSize != -1 && hand.size == targetSize) {
                        pickMaalCard()
                        showGameMessage("Maal Revealed!")
                    }
                }
            }
        }
    }

    fun humanWinsGame(card: Card) {
        if (isInitializing || currentPlayer != 1 || currentTurnPhase != TurnPhase.PLAY_OR_DISCARD || winner != null) return
        val hand = playerHands[1] ?: return
        if (hand.contains(card)) {
            moveCard(card, 1, AnimationType.DISCARD, AnimationSource.PLAYER) {
                val idx = hand.indexOfFirst { it === card }
                if (idx != -1) hand.removeAt(idx)
                discardPile.add(card)
            }
        }
    }

    fun humanShows() {
        if (isInitializing || currentPlayer != 1) return
        val hand = playerHands[1] ?: return
        val alreadyShownCount = shownCards[1]?.size ?: 0
        val isFirstTurnCheck = isFirstTurn[1] == true && currentTurnPhase == TurnPhase.INITIAL_CHECK

        if (hasShown[1] == false && alreadyShownCount == 0 && selectedCards.size == 14) {
            val selectedList = selectedCards.toList()
            viewModelScope.launch {
                try {
                    val dublis = withContext(Dispatchers.Default) { AiPlayer.findDublis(selectedList) }
                    withContext(Dispatchers.Main) {
                        if (dublis.size == 7) {
                            val meldedCards = dublis.flatten()
                            hand.removeByReference(meldedCards)
                            shownCards[1]?.addAll(meldedCards)
                            hasShown[1] = true
                            isDubliShow[1] = true
                            showGameMessage("Dubli Show Success!")

                            if (hand.size == 7 && isFirstTurnCheck) pickMaalCard()

                            selectedCards.clear()
                            canWinWithSelected = false

                            if (currentTurnPhase == TurnPhase.SHOW_OR_END) {
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            } else {
                                currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                            }
                            updateHint()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        if (hasShown[1] == false && alreadyShownCount < 9 && selectedCards.size == 3 && isFirstTurnCheck) { 
            val jokers = selectedCards.filter { it.rank == Rank.JOKER }
            val identical = selectedCards.all { it.rank == selectedCards[0].rank && it.suit == selectedCards[0].suit }
            if (jokers.size == 3 || identical) {
                val cardsToShow = selectedCards.toList()
                hand.removeByReference(cardsToShow)
                shownCards[1]?.addAll(cardsToShow)
                
                showGameMessage("Tunnel Revealed!")
                
                if (shownCards[1]?.size == 9) {
                    hasShown[1] = true
                    pickMaalCard()
                }
                
                selectedCards.clear()
                canWinWithSelected = false
                updateHint()
                return 
            }
        }

        val requiredMeldCount = 3 - (alreadyShownCount / 3)
        val requiredCardCount = requiredMeldCount * 3
        if (hasShown[1] == false && selectedCards.size == requiredCardCount && requiredMeldCount > 0) {
            val selectedList = selectedCards.toList()
            viewModelScope.launch {
                try {
                    val melds = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(selectedList) }
                    withContext(Dispatchers.Main) {
                        if (melds.size >= requiredMeldCount) {
                            val meldedCards = melds.take(requiredMeldCount).flatten()
                            hand.removeByReference(meldedCards)
                            shownCards[1]?.addAll(meldedCards)
                            hasShown[1] = true 
                            
                            if (hand.size == 12 && isFirstTurnCheck) pickMaalCard()

                            showGameMessage("Success!")
                            
                            if (currentTurnPhase == TurnPhase.SHOW_OR_END) {
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            } else {
                                currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                            }
                            
                            selectedCards.clear()
                            canWinWithSelected = false
                            updateHint()
                        } else showGameMessage("Invalid Melds!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun humanEndsTurnWithoutShowing() {
        if (currentPlayer == 1 && currentTurnPhase == TurnPhase.SHOW_OR_END) {
            currentTurnPhase = TurnPhase.ENDED
            advanceTurn()
        }
    }

    fun toggleCardSelection(card: Card) {
        if (isInitializing || currentPlayer != 1 || winner != null) return
        val isAlreadySelected = selectedCards.any { it === card }
        if (isAlreadySelected) selectedCards.removeAll { it === card }
        else {
            val maxAllowed = if (hasShown[1] == true) 1 else 14
            if (selectedCards.size < maxAllowed) selectedCards.add(card)
            else if (maxAllowed == 1) {
                selectedCards.clear()
                selectedCards.add(card)
            }
        }
        updateHint()
    }

    private fun advanceTurn() {
        if (winner != null) return
        currentTurnPhase = TurnPhase.ENDED
        hint = null
        viewModelScope.launch {
            try {
                delay(500)
                currentPlayer = if (currentPlayer == playerCount) 1 else currentPlayer + 1
                if (isFirstTurn[currentPlayer] == true) {
                    currentTurnPhase = TurnPhase.INITIAL_CHECK
                    processInitialCheck()
                } else {
                    currentTurnPhase = TurnPhase.DRAW
                    if (currentPlayer != 1) processAiTurn() else updateHint()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processAiTurn() {
        val myPlayerId = currentPlayer
        viewModelScope.launch {
            val aiHand = playerHands[myPlayerId] ?: return@launch
            try {
                delay(500)
                if (currentPlayer != myPlayerId || currentTurnPhase != TurnPhase.DRAW) return@launch
                val cardFromDiscard = discardPile.lastOrNull()
                val aiHandList = aiHand.toList()
                if (cardFromDiscard != null) {
                    val decision = withContext(Dispatchers.Default) { AiPlayer.shouldPickFromDiscard(cardFromDiscard, aiHandList, this@GameState, myPlayerId) }
                    val wasLastDiscarded = lastDiscardedCardIdByPlayer[myPlayerId] == cardFromDiscard.id
                    if (decision.shouldPick && !wasLastDiscarded) {
                        val card = discardPile.last()
                        moveCard(card, myPlayerId, AnimationType.DRAW, AnimationSource.DISCARD) {
                            discardPile.removeAt(discardPile.lastIndex)
                            aiHand.add(card)
                        }
                    } else if (stockPile.isNotEmpty()) {
                        val card = stockPile.first()
                        moveCard(card, myPlayerId, AnimationType.DRAW, AnimationSource.STOCK) {
                            stockPile.removeAt(0)
                            aiHand.add(card)
                        }
                    } else { advanceTurn(); return@launch }
                } else if (stockPile.isNotEmpty()) {
                    val card = stockPile.first()
                    moveCard(card, myPlayerId, AnimationType.DRAW, AnimationSource.STOCK) {
                        stockPile.removeAt(0)
                        aiHand.add(card)
                    }
                } else { advanceTurn(); return@launch }
                delay(1000) 
                if (winner != null || currentPlayer != myPlayerId) return@launch
                if (currentTurnPhase == TurnPhase.PLAY_OR_DISCARD) {
                    val aiHandAfterDraw = aiHand.toList()
                    val decision = withContext(Dispatchers.Default) { AiPlayer.findCardToDiscard(aiHandAfterDraw, this@GameState, myPlayerId) }
                    val cardToDiscard = decision.card
                    moveCard(cardToDiscard, myPlayerId, AnimationType.DISCARD, AnimationSource.PLAYER) {
                        val idx = aiHand.indexOfFirst { it === cardToDiscard }
                        if (idx != -1) aiHand.removeAt(idx)
                        discardPile.add(cardToDiscard)
                        if (maalCard == null) {
                            val targetSize = if (isDubliShow[myPlayerId] == true) 7 else if (hasShown[myPlayerId] == true) 12 else -1
                            if (targetSize != -1 && aiHand.size == targetSize) pickMaalCard()
                        }
                    }
                }
            } catch (_: Exception) { if (winner == null && currentPlayer == myPlayerId) advanceTurn() }
        }
    }

    private fun updateHint() {
        if (isInitializing || currentPlayer != 1 || winner != null) {
            if (!showHints) {
                hint = null
                meldedCards.clear()
                dubliCards.clear()
            }
            if (currentTurnPhase == TurnPhase.PLAY_OR_DISCARD || currentTurnPhase == TurnPhase.INITIAL_CHECK) {
                updateCanWinStatus()
            }
            return
        }
        
        val humanHand = playerHands[1]?.toList() ?: return
        val alreadyShownCount = shownCards[1]?.size ?: 0
        viewModelScope.launch {
            try {
                val dublis = withContext(Dispatchers.Default) { AiPlayer.findDublis(humanHand) }
                val melds = withContext(Dispatchers.Default) { 
                    val initialShow = hasShown[1] == false
                    val m = mutableListOf<List<Card>>()
                    val used = java.util.BitSet(humanHand.size)
                    while (true) {
                        val indices = findMeldsGreedyIndices(humanHand, used, initialShow) ?: break
                        m.add(indices.map { humanHand[it] })
                        indices.forEach { used.set(it) }
                    }
                    m
                }
                
                withContext(Dispatchers.Main) {
                    if (showHints) {
                        meldedCards.clear()
                        meldedCards.addAll(melds.flatten())
                        dubliCards.clear()
                        if (dublis.size >= 4 && hasShown[1] == false && alreadyShownCount == 0) {
                            dubliCards.addAll(dublis.flatten())
                        }
                    }

                    when (currentTurnPhase) {
                        TurnPhase.INITIAL_CHECK -> {
                            val jokers = humanHand.filter { it.rank == Rank.JOKER }
                            val identical = humanHand.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
                            
                            val reqMelds = 3 - (alreadyShownCount / 3)
                            val possibleInitial = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
                            
                            if (jokers.size >= 3 && alreadyShownCount == 0) {
                                if (showHints) hint = Hint(title = "Tunnel!", message = "You have a Joker Tunnel! Select 3 Jokers and SHOW to get points. Even after this, you'll need to show 3 sequences to reveal Maal.", cards = jokers.take(3))
                            } else if (identical.isNotEmpty() && alreadyShownCount == 0) {
                                if (showHints) hint = Hint(title = "Tunnel!", message = "You have an Identical Tunnel! Select 3 identical cards and SHOW to get points. Even after this, you'll need to show 3 sequences to reveal Maal.", cards = identical.values.first().take(3))
                            } else if (dublis.size >= 7 && alreadyShownCount == 0 && hasShown[1] == false) {
                                if (showHints) hint = Hint(title = "Dubli Show", message = "You have 7 pairs! Select 14 cards and SHOW to reveal Maal.", cards = dublis.take(7).flatten())
                            } else if (possibleInitial.size >= reqMelds && reqMelds > 0) {
                                if (showHints) hint = Hint(title = "Ready to Show", message = "You already have the required pure melds! Select ${reqMelds * 3} cards and SHOW to reveal Maal.", cards = possibleInitial.take(reqMelds).flatten())
                            } else {
                                updateDrawHint(humanHand)
                            }
                            updateCanWinStatus()
                        }
                        TurnPhase.DRAW -> updateDrawHint(humanHand)
                        TurnPhase.PLAY_OR_DISCARD -> {
                            val winCard = withContext(Dispatchers.Default) {
                                humanHand.find { card -> AiPlayer.canFinish(humanHand.filter { it !== card }, this@GameState, 1) }
                            }
                            
                            if (winCard != null) {
                                if (showHints) hint = Hint(title = "Victory!", message = "Discard ${winCard.rank.symbol} to WIN!", cards = listOf(winCard))
                            } else if (hasShown[1] == false) {
                                if (alreadyShownCount == 0 && dublis.size >= 7) {
                                    if (showHints) hint = Hint(title = "Dubli Show", message = "Select 14 cards and SHOW.", cards = dublis.take(7).flatten())
                                } else {
                                    val reqMelds = 3 - (alreadyShownCount / 3)
                                    val possibleInitial = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
                                    if (possibleInitial.size >= reqMelds) {
                                        if (showHints) hint = Hint(title = "Ready to Show", message = "Select ${reqMelds * 3} cards and SHOW.", cards = possibleInitial.take(reqMelds).flatten())
                                    } else {
                                        provideDiscardHint(humanHand)
                                    }
                                }
                            } else {
                                provideDiscardHint(humanHand)
                            }
                            updateCanWinStatus()
                        }
                        TurnPhase.SHOW_OR_END -> {
                            if (showHints) {
                                hint = if (hasShown[1] == true) {
                                    Hint(title = "End Turn", message = "Press END TURN.")
                                } else {
                                    val reqMelds = 3 - (alreadyShownCount / 3)
                                    val possibleInitial = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
                                    if (possibleInitial.size >= reqMelds) {
                                        Hint(title = "Show Now?", message = "Select cards and SHOW.", cards = possibleInitial.take(reqMelds).flatten())
                                    } else {
                                        Hint(title = "Strategy", message = "End your turn.")
                                    }
                                }
                            }
                        }
                        else -> hint = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateCanWinStatus() {
        val selected = selectedCards.toList()
        if (selected.size != 1) {
            canWinWithSelected = false
            return
        }
        val hand = playerHands[1]?.toList() ?: return
        val cardToDiscard = selected.first()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    AiPlayer.canFinish(hand.filter { it !== cardToDiscard }, this@GameState, 1)
                }
                withContext(Dispatchers.Main) {
                    canWinWithSelected = result
                }
            } catch (e: Exception) {
                e.printStackTrace()
                canWinWithSelected = false
            }
        }
    }

    private fun provideDiscardHint(humanHand: List<Card>) {
        viewModelScope.launch {
            try {
                val suggested = withContext(Dispatchers.Default) { GameEngine.getSuggestedDiscards(humanHand, this@GameState, 1, difficulty) }
                withContext(Dispatchers.Main) {
                    if (suggested.size > 1) {
                        if (showHints) hint = Hint(title = "Discard Options", message = "Multiple cards are equally worthless. Tap the DISCARD button to choose.", cards = suggested)
                    } else if (suggested.isNotEmpty()) {
                        val decision = withContext(Dispatchers.Default) { AiPlayer.findCardToDiscard(humanHand, this@GameState, 1) }
                        if (showHints) hint = Hint(title = "Discard", message = decision.reason, cards = listOf(decision.card))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestDiscardSelection() {
        val hand = playerHands[1]?.toList() ?: return
        viewModelScope.launch {
            try {
                val suggested = withContext(Dispatchers.Default) { GameEngine.getSuggestedDiscards(hand, this@GameState, 1, difficulty) }
                withContext(Dispatchers.Main) {
                    if (suggested.size > 1) {
                        onShowDiscardSelection?.invoke(suggested)
                    } else if (suggested.isNotEmpty()) {
                        humanDiscardsCard(suggested.first())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun updateDrawHint(humanHand: List<Card>) {
        val alreadyShownCount = shownCards[1]?.size ?: 0
        if (hasShown[1] == false) {
            val dublis = AiPlayer.findDublis(humanHand)
            if (alreadyShownCount == 0 && dublis.size >= 4) {
                val cardFromDiscard = discardPile.lastOrNull()
                val discardDecision = if (cardFromDiscard != null) withContext(Dispatchers.Default) { AiPlayer.shouldPickFromDiscard(cardFromDiscard, humanHand, this@GameState, 1) } else null
                if (showHints) hint = Hint(title = "Dubli", message = discardDecision?.reason ?: "Pick from Stock pile.", cards = if (discardDecision?.shouldPick == true) listOf(discardPile.last()) else emptyList())
                return
            }
            val reqMelds = 3 - (alreadyShownCount / 3)
            val possibleInitial = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
            if (possibleInitial.size >= reqMelds) {
                if (showHints) hint = Hint(title = "Show Before Drawing", message = "Select cards and SHOW.", cards = possibleInitial.take(reqMelds).flatten())
                return
            }
        }
        val cardFromDiscard = discardPile.lastOrNull()
        val decision = if (cardFromDiscard != null) withContext(Dispatchers.Default) { AiPlayer.shouldPickFromDiscard(cardFromDiscard, humanHand, this@GameState, 1) } else null
        if (showHints) {
            hint = if (decision != null && decision.shouldPick) Hint(title = "Draw Discard", message = decision.reason, cards = listOf(discardPile.last()))
            else Hint(title = "Draw Stock", message = "Pick from Stock pile.")
        }
    }

    private fun findMeldsGreedyIndices(cards: List<Card>, used: java.util.BitSet, initialShow: Boolean): List<Int>? {
        val indices = mutableListOf<Int>()
        for (i in cards.indices) if (!used.get(i)) indices.add(i)
        if (indices.size < 3) return null
        for (i in indices.indices) {
            for (j in i + 1 until indices.size) {
                for (k in j + 1 until indices.size) {
                    val isValid = if (initialShow) AiPlayer.isValidInitialMeld(cards[indices[i]], cards[indices[j]], cards[indices[k]])
                                 else AiPlayer.isValidGeneralMeld(cards[indices[i]], cards[indices[j]], cards[indices[k]], this, 1)
                    if (isValid) {
                        return listOf(indices[i], indices[j], indices[k])
                    }
                }
            }
        }
        return null
    }

    private fun createCardPool(deckCount: Int, jokerCount: Int): MutableList<Card> {
        val pool = mutableListOf<Card>()
        repeat(deckCount) {
            val standardRanks = Rank.entries.filter { it != Rank.JOKER }
            val standardSuits = Suit.entries.filter { it != Suit.NONE }
            pool.addAll(standardSuits.flatMap { suit -> standardRanks.map { rank -> Card(suit, rank) } })
        }
        repeat(jokerCount) { pool.add(Card(Suit.NONE, Rank.JOKER)) }
        pool.shuffle(Random)
        return pool
    }

    private fun dealCards(playerCount: Int, cardPool: MutableList<Card>) {
        playerHands.clear()
        stockPile.clear()
        discardPile.clear()
        for (i in 1..playerCount) playerHands[i] = mutableStateListOf() 
        repeat(21) {
            for (player in 1..playerCount) if (cardPool.isNotEmpty()) playerHands[player]?.add(cardPool.removeAt(0))
        }
        stockPile.addAll(cardPool)
    }
}
