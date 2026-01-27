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

    fun setupGame(count: Int) {
        viewModelScope.launch {
            isInitializing = true
            withContext(Dispatchers.Default) {
                this@GameState.playerCount = count
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
                    selectedCards.clear()
                    isFirstTurn.clear()
                    
                    for (i in 1..playerCount) {
                        hasShown[i] = false
                        isDubliShow[i] = false
                        shownCards[i] = mutableStateListOf()
                        isFirstTurn[i] = true
                        
                        val hand = playerHands[i] ?: emptyList()
                        val tunnelas = hand.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
                        var bonus = 0
                        tunnelas.forEach { (key, list) -> bonus += if (key.first == Rank.JOKER) 30 else 5 }
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
        }
    }

    private fun processInitialCheck() {
        if (currentPlayer == 1) {
            updateHint() // Human will see tunnel hints if any
        } else {
            // AI automatically reveals tunnels if any
            val aiHand = playerHands[currentPlayer] ?: return
            val jokers = aiHand.filter { it.rank == Rank.JOKER }
            val identical = aiHand.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
            
            if (jokers.size >= 3) {
                val cards = jokers.take(3)
                aiHand.removeByReference(cards)
                shownCards[currentPlayer]?.addAll(cards)
                if (aiHand.size == 18) pickMaalCard()
            } else if (identical.isNotEmpty()) {
                val cards = identical.values.first().take(3)
                aiHand.removeByReference(cards)
                shownCards[currentPlayer]?.addAll(cards)
                if (aiHand.size == 18) pickMaalCard()
            }
            
            // Move to DRAW phase for AI
            viewModelScope.launch {
                delay(1000)
                currentTurnPhase = TurnPhase.DRAW
                processAiTurn()
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
    
    fun calculateMaal(player: Int): Int {
        val bonus = startingBonuses[player] ?: 0
        return GameEngine.calculateMaal(player, playerHands.toMap(), shownCards.toMap(), hasShown.toMap(), maalCard, bonus)
    }

    private fun checkWin(player: Int) {
        val hand = playerHands[player]?.toList() ?: return
        viewModelScope.launch {
            val isWin = withContext(Dispatchers.Default) { AiPlayer.canFinish(hand, this@GameState, player) }
            if (isWin) {
                withContext(Dispatchers.Main) {
                    winner = player
                    explainFinalScores()
                }
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
            val message = withContext(Dispatchers.Default) {
                GameEngine.getFinalScoreReason(w, playerCount, pHands, sCards, hShown, maalCard, isDubli, sBonuses)
            }
            withContext(Dispatchers.Main) {
                hint = Hint(title = "Game Over!", message = message)
            }
        }
    }

    private fun moveCard(card: Card, player: Int, type: AnimationType, source: AnimationSource, onLogicUpdate: () -> Unit) {
        val isFaceUp = if (type == AnimationType.DRAW) player == 1 else true
        viewModelScope.launch {
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
                handlePostDiscard(player)
            }
        }
    }

    private fun handlePostDiscard(player: Int) {
        val hand = playerHands[player] ?: return
        if (player == 1) {
            selectedCards.clear()
            checkWin(1)
            if (winner != null) return
            viewModelScope.launch {
                val handList = hand.toList()
                val alreadyShownCount = shownCards[1]?.size ?: 0
                val reqMelds = 3 - (alreadyShownCount / 3)
                val canShowRegular = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(handList).size >= reqMelds }
                val canShowDubli = alreadyShownCount == 0 && withContext(Dispatchers.Default) { AiPlayer.findDublis(handList).size >= 7 }
                withContext(Dispatchers.Main) {
                    if (hasShown[1] == false && (canShowRegular || canShowDubli)) {
                        currentTurnPhase = TurnPhase.SHOW_OR_END
                        updateHint()
                    } else {
                        currentTurnPhase = TurnPhase.ENDED
                        advanceTurn()
                    }
                }
            }
        } else {
            if (hasShown[player] == false) {
                val handList = hand.toList()
                viewModelScope.launch {
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
                            checkWin(player)
                            if (winner == null) {
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            }
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
                            checkWin(player)
                            if (winner == null) {
                                currentTurnPhase = TurnPhase.ENDED
                                advanceTurn()
                            }
                        }
                    }
                }
            } else {
                checkWin(player)
                if (winner == null) {
                    currentTurnPhase = TurnPhase.ENDED
                    advanceTurn()
                }
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
            gameMessage = msg
            delay(duration) 
            gameMessage = null
        }
    }

    fun humanDrawsFromStock() {
        if (isInitializing || currentPlayer != 1 || currentTurnPhase != TurnPhase.DRAW || winner != null || stockPile.isEmpty()) return
        val card = stockPile.first()
        moveCard(card, 1, AnimationType.DRAW, AnimationSource.STOCK) {
            stockPile.removeAt(0)
            playerHands[1]?.add(card)
        }
    }

    fun humanDrawsFromDiscard() {
        if (isInitializing || currentPlayer != 1 || currentTurnPhase != TurnPhase.DRAW || winner != null || discardPile.isEmpty()) return
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
                    val targetSize = if (isDubliShow[1] == true) 7 else if (hasShown[1] == true) 12 else if (shownCards[1]?.size == 3) 18 else -1
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
                val dublis = withContext(Dispatchers.Default) { AiPlayer.findDublis(selectedList) }
                withContext(Dispatchers.Main) {
                    if (dublis.size == 7) {
                        val meldedCards = dublis.flatten()
                        hand.removeByReference(meldedCards)
                        shownCards[1]?.addAll(meldedCards)
                        hasShown[1] = true
                        isDubliShow[1] = true
                        showGameMessage("Dubli Show Success!")
                        if (hand.size == 7) pickMaalCard()
                        selectedCards.clear()
                        updateHint()
                        currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                    }
                }
            }
            return
        }

        if (hasShown[1] == false && alreadyShownCount == 0 && selectedCards.size == 3 && isFirstTurnCheck) { 
            val jokers = selectedCards.filter { it.rank == Rank.JOKER }
            val identical = selectedCards.size == 3 && selectedCards.all { it.rank == selectedCards[0].rank && it.suit == selectedCards[0].suit }
            if (jokers.size == 3 || identical) {
                val cardsToShow = selectedCards.toList()
                hand.removeByReference(cardsToShow)
                shownCards[1]?.addAll(cardsToShow)
                if (hand.size == 18) pickMaalCard()
                showGameMessage("Tunnel Revealed!")
                selectedCards.clear()
                updateHint()
                return 
            }
        }

        val requiredMeldCount = 3 - (alreadyShownCount / 3)
        val requiredCardCount = requiredMeldCount * 3
        if (hasShown[1] == false && selectedCards.size == requiredCardCount && requiredMeldCount > 0) {
            val selectedList = selectedCards.toList()
            viewModelScope.launch {
                val melds = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(selectedList) }
                withContext(Dispatchers.Main) {
                    if (melds.size >= requiredMeldCount) {
                        val meldedCards = melds.take(requiredMeldCount).flatten()
                        hand.removeByReference(meldedCards)
                        shownCards[1]?.addAll(meldedCards)
                        hasShown[1] = true 
                        if (hand.size == 12) pickMaalCard()
                        showGameMessage("Success!")
                        if (currentTurnPhase != TurnPhase.DRAW) currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                        selectedCards.clear()
                        updateHint()
                    } else showGameMessage("Invalid Melds!")
                }
            }
        }
    }

    fun humanEndsInitialCheck() {
        if (currentPlayer == 1 && currentTurnPhase == TurnPhase.INITIAL_CHECK) {
            currentTurnPhase = TurnPhase.DRAW
            updateHint()
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
    }

    private fun advanceTurn() {
        if (winner != null) return
        currentTurnPhase = TurnPhase.ENDED
        hint = null
        viewModelScope.launch {
            delay(500)
            currentPlayer = if (currentPlayer == playerCount) 1 else currentPlayer + 1
            if (isFirstTurn[currentPlayer] == true) {
                currentTurnPhase = TurnPhase.INITIAL_CHECK
                processInitialCheck()
            } else {
                currentTurnPhase = TurnPhase.DRAW
                if (currentPlayer != 1) processAiTurn() else updateHint()
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
                    if (decision.shouldPick) {
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
                            val targetSize = if (isDubliShow[myPlayerId] == true) 7 else if (hasShown[myPlayerId] == true) 12 else if (shownCards[myPlayerId]?.size == 3) 18 else -1
                            if (targetSize != -1 && aiHand.size == targetSize) pickMaalCard()
                        }
                    }
                }
            } catch (ignore: Exception) { if (winner == null && currentPlayer == myPlayerId) advanceTurn() }
        }
    }

    private fun updateHint() {
        if (!showHints || isInitializing || currentPlayer != 1 || winner != null) {
            hint = null
            meldedCards.clear()
            return
        }
        val humanHand = playerHands[1]?.toList() ?: return
        val alreadyShownCount = shownCards[1]?.size ?: 0
        viewModelScope.launch {
            val mCards = withContext(Dispatchers.Default) { AiPlayer.findAllMeldedCards(humanHand, this@GameState, 1) }
            withContext(Dispatchers.Main) {
                meldedCards.clear()
                meldedCards.addAll(mCards)
                when (currentTurnPhase) {
                    TurnPhase.INITIAL_CHECK -> {
                        val jokers = humanHand.filter { it.rank == Rank.JOKER }
                        val identical = humanHand.groupBy { it.rank to it.suit }.filter { it.value.size >= 3 }
                        if (jokers.size >= 3) hint = Hint(title = "Tunnel!", message = "Select 3 Jokers and SHOW.", cards = jokers.take(3))
                        else if (identical.isNotEmpty()) hint = Hint(title = "Tunnel!", message = "Select 3 identical cards and SHOW.", cards = identical.values.first().take(3))
                        else hint = Hint(title = "No Tunnels", message = "Press PROCEED to draw your first card.")
                    }
                    TurnPhase.DRAW -> updateDrawHint(humanHand)
                    TurnPhase.PLAY_OR_DISCARD -> {
                        val winCard = humanHand.find { card -> AiPlayer.canFinish(humanHand.filter { it !== card }, this@GameState, 1) }
                        if (winCard != null) hint = Hint(title = "Victory!", message = "Discard ${winCard.rank.symbol} to WIN!", cards = listOf(winCard))
                        else if (hasShown[1] == false) {
                            val dublis = AiPlayer.findDublis(humanHand)
                            if (alreadyShownCount == 0 && dublis.size >= 7) hint = Hint(title = "Dubli Show", message = "Select 14 cards and SHOW.", cards = dublis.take(7).flatten())
                            else {
                                val reqMelds = 3 - (alreadyShownCount / 3)
                                val possibleInitial = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
                                if (possibleInitial.size >= reqMelds) hint = Hint(title = "Ready to Show", message = "Select ${reqMelds * 3} cards and SHOW.", cards = possibleInitial.take(reqMelds).flatten())
                                else {
                                    val decision = withContext(Dispatchers.Default) { AiPlayer.findCardToDiscard(humanHand, this@GameState, 1) }
                                    hint = Hint(title = "Discard", message = decision.reason, cards = listOf(decision.card))
                                }
                            }
                        } else {
                            val decision = withContext(Dispatchers.Default) { AiPlayer.findCardToDiscard(humanHand, this@GameState, 1) }
                            hint = Hint(title = "Discard", message = decision.reason, cards = listOf(decision.card))
                        }
                    }
                    TurnPhase.SHOW_OR_END -> {
                        if (hasShown[1] == true) hint = Hint(title = "End Turn", message = "Press END TURN.")
                        else {
                            val reqMelds = 3 - (alreadyShownCount / 3)
                            val melds = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
                            if (melds.size >= reqMelds) hint = Hint(title = "Show Now?", message = "Select cards and SHOW.", cards = melds.take(reqMelds).flatten())
                            else hint = Hint(title = "Strategy", message = "End your turn.")
                        }
                    }
                    else -> hint = null
                }
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
                hint = Hint(title = "Dubli", message = discardDecision?.reason ?: "Draw from stock.", cards = if (discardDecision?.shouldPick == true) listOf(discardPile.last()) else emptyList())
                return
            }
            val reqMelds = 3 - (alreadyShownCount / 3)
            val melds = withContext(Dispatchers.Default) { AiPlayer.findAllInitialMelds(humanHand) }
            if (melds.size >= reqMelds) {
                hint = Hint(title = "Show Before Drawing", message = "Select cards and SHOW.", cards = melds.take(reqMelds).flatten())
                return
            }
        }
        val cardFromDiscard = discardPile.lastOrNull()
        val decision = if (cardFromDiscard != null) withContext(Dispatchers.Default) { AiPlayer.shouldPickFromDiscard(cardFromDiscard, humanHand, this@GameState, 1) } else null
        hint = if (decision != null && decision.shouldPick) Hint(title = "Draw Discard", message = decision.reason, cards = listOf(discardPile.last()))
        else Hint(title = "Draw Stock", message = "Draw from stock pile.")
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
