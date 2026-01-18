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
    
    val bonusMaalPoints = mutableStateMapOf<Int, Int>()

    val selectedCards = mutableStateListOf<Card>()
    val playerIcons = mutableStateMapOf<Int, String>()
    private val availableIcons = listOf("🤖", "👽", "👾", "👺", "👺", "👻")

    var isFirstTurn by mutableStateOf(true)
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
                val cardPool = createCardPool(numberOfDecks, numberOfDecks * 2)
                
                withContext(Dispatchers.Main) {
                    dealCards(playerCount, cardPool)
                    assignPlayerIcons(count)
                    
                    hasShown.clear()
                    shownCards.clear()
                    bonusMaalPoints.clear()
                    meldedCards.clear()
                    selectedCards.clear()
                    
                    for (i in 1..playerCount) {
                        hasShown[i] = false
                        shownCards[i] = mutableStateListOf()
                        bonusMaalPoints[i] = 0
                    }
                    
                    maalCard = null
                    winner = null
                    gameMessage = null
                    
                    currentPlayer = Random.nextInt(1, count + 1) 
                    
                    currentTurnPhase = TurnPhase.DRAW
                    isFirstTurn = true
                    isInitializing = false
                    lastDrawnCard = null

                    if (currentPlayer != 1) {
                        processAiTurn()
                    } else {
                        updateHint()
                    }
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
        val visibleMaal = if (hasShown[player] == true) maalCard else null
        return GameEngine.isJoker(card, player, hasShown, visibleMaal)
    }
    
    fun calculateMaal(player: Int): Int {
        val bonus = if (hasShown[player] == true) (bonusMaalPoints[player] ?: 0) else 0
        return GameEngine.calculateMaal(player, playerHands, shownCards, hasShown, maalCard) + bonus
    }

    private fun checkWin(player: Int) {
        val hand = playerHands[player] ?: return
        if (AiPlayer.canFinish(hand, this, player)) {
            winner = player
            explainFinalScores()
        }
    }

    private fun explainFinalScores() {
        val w = winner ?: return
        val message = GameEngine.getFinalScoreReason(
            w, 
            playerCount, 
            playerHands, 
            shownCards, 
            hasShown, 
            maalCard
        )

        hint = Hint(
            title = "Game Over!", 
            message = message
        )
    }

    private fun moveCard(
        card: Card,
        player: Int,
        type: AnimationType,
        source: AnimationSource,
        onLogicUpdate: () -> Unit
    ) {
        val isFaceUp = if (type == AnimationType.DRAW) {
            player == 1
        } else {
            true 
        }

        viewModelScope.launch {
            animationState = AnimationState(card, player, type, source, isFaceUp)
            delay(500)
            onLogicUpdate()
            animationState = null
            
            if (type == AnimationType.DRAW) {
                if (player == 1) {
                    lastDrawnCard = card
                }
                isFirstTurn = false 
                currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                if (player == 1) {
                    updateHint()
                }
            } else { // DISCARD
                if (player == 1) {
                    lastDrawnCard = null
                }
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

            if (hasShown[1] == false && AiPlayer.findAllInitialMelds(hand).isNotEmpty()) {
                currentTurnPhase = TurnPhase.SHOW_OR_END
                updateHint()
            } else {
                currentTurnPhase = TurnPhase.ENDED
                advanceTurn()
            }
        } else {
            if (hasShown[player] == false) {
                val initialMelds = AiPlayer.findAllInitialMelds(hand)
                if (initialMelds.size >= 3) {
                    val meldedCards = initialMelds.flatten()
                    hand.removeByReference(meldedCards)
                    shownCards[player]?.addAll(meldedCards)
                    hasShown[player] = true
                    showGameMessage("Player $player has shown!")
                    pickMaalCard()
                }
            }
            checkWin(player)
            if (winner == null) {
                currentTurnPhase = TurnPhase.ENDED
                advanceTurn()
            }
        }
    }

    private fun pickMaalCard() {
        if (maalCard != null || stockPile.isEmpty()) return
        var index = 0
        while (index < stockPile.size && stockPile[index].rank == Rank.JOKER) {
            index++
        }
        maalCard = if (index < stockPile.size) {
            stockPile.removeAt(index)
        } else {
            stockPile.removeAt(0)
        }
    }

    private fun showGameMessage(msg: String) {
        viewModelScope.launch {
            gameMessage = msg
            delay(2500) 
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

                if (hasShown[1] == true && maalCard == null) {
                    pickMaalCard()
                    showGameMessage("Maal Revealed!")
                }
            }
        }
    }

    fun humanShows() {
        if (isInitializing || currentPlayer != 1) return
        val hand = playerHands[1] ?: return

        if (hasShown[1] == false && 
            selectedCards.size == 3 && 
            (bonusMaalPoints[1] ?: 0) == 0 && 
            currentTurnPhase == TurnPhase.DRAW && 
            isFirstTurn) { 
            
            val jokers = selectedCards.filter { it.rank == Rank.JOKER }
            val identical = selectedCards.size == 3 && selectedCards.all { 
                it.rank == selectedCards[0].rank && it.suit == selectedCards[0].suit 
            }

            if (jokers.size == 3 || identical) {
                val bonus = if (jokers.size == 3) 25 else 10
                val cardsToShow = selectedCards.toList()
                hand.removeByReference(cardsToShow)
                shownCards[1]?.addAll(cardsToShow)
                bonusMaalPoints[1] = bonus
                pickMaalCard()
                showGameMessage("Special Show! Bonus secured.")
                selectedCards.clear()
                updateHint()
                return 
            }
        }

        val hasSpecialBonus = (bonusMaalPoints[1] ?: 0) > 0
        val requiredCardCount = if (hasSpecialBonus) 6 else 9
        val requiredMeldCount = if (hasSpecialBonus) 2 else 3

        if (hasShown[1] == false && selectedCards.size == requiredCardCount) {
            val melds = AiPlayer.findAllInitialMelds(selectedCards.toList())
            
            if (melds.size >= requiredMeldCount) {
                val meldedCards = melds.flatten()
                hand.removeByReference(meldedCards)
                shownCards[1]?.addAll(meldedCards)
                hasShown[1] = true 

                val message = if (maalCard != null) "Success! Maal is already visible." 
                             else if (currentTurnPhase == TurnPhase.DRAW) "Success! Now draw your card."
                             else "Success! Now discard to see Maal."
                
                showGameMessage(message)
                
                if (currentTurnPhase != TurnPhase.DRAW) {
                    currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                }
                
                selectedCards.clear()
                updateHint()
            } else {
                showGameMessage("Invalid Melds!")
            }
        } else if (hasShown[1] == false) {
            val msg = if (currentTurnPhase == TurnPhase.DRAW && isFirstTurn) 
                "You can perform a Special Show (3 cards) or select 9 cards for a regular show."
                else "Select $requiredCardCount cards to show."
            showGameMessage(msg)
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

        if (isAlreadySelected) {
            selectedCards.removeAll { it === card }
        } else {
            val maxAllowed = if (hasShown[1] == true) 1 else 9

            if (selectedCards.size < maxAllowed) {
                selectedCards.add(card)
            } else if (maxAllowed == 1) {
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
            currentTurnPhase = TurnPhase.DRAW
            if (currentPlayer != 1) processAiTurn() else updateHint()
        }
    }

    private fun processAiTurn() {
        val myPlayerId = currentPlayer
        viewModelScope.launch {
            val aiHand = playerHands[myPlayerId] ?: return@launch
            try {
                if (isFirstTurn && currentPlayer == myPlayerId) {
                    val jokers = aiHand.filter { it.rank == Rank.JOKER }
                    val identical = aiHand.groupBy { it }.filter { it.value.size >= 3 }
                    
                    if (jokers.size >= 3) {
                        val cards = jokers.take(3)
                        aiHand.removeByReference(cards)
                        shownCards[myPlayerId]?.addAll(cards)
                        bonusMaalPoints[myPlayerId] = 25
                        pickMaalCard()
                    } else if (identical.isNotEmpty()) {
                        val cards = identical.values.first().take(3)
                        aiHand.removeByReference(cards)
                        shownCards[myPlayerId]?.addAll(cards)
                        bonusMaalPoints[myPlayerId] = 10
                        pickMaalCard()
                    }
                }

                delay(500)
                if (currentPlayer != myPlayerId || currentTurnPhase != TurnPhase.DRAW) return@launch

                val cardFromDiscard = discardPile.lastOrNull()
                
                if (cardFromDiscard != null && AiPlayer.shouldTakeCard(aiHand, cardFromDiscard, this@GameState, myPlayerId)) {
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
                } else {
                    advanceTurn()
                    return@launch
                }
                
                delay(1000) 
                if (winner != null || currentPlayer != myPlayerId) return@launch

                if (currentTurnPhase == TurnPhase.PLAY_OR_DISCARD) {
                    val decision = AiPlayer.findCardToDiscard(aiHand, this@GameState, myPlayerId)
                    val cardToDiscard = decision.card
                    moveCard(cardToDiscard, myPlayerId, AnimationType.DISCARD, AnimationSource.PLAYER) {
                        val idx = aiHand.indexOfFirst { it === cardToDiscard }
                        if (idx != -1) aiHand.removeAt(idx)
                        discardPile.add(cardToDiscard)
                    }
                }

            } catch (ignore: Exception) {
                if (winner == null && currentPlayer == myPlayerId) advanceTurn()
            }
        }
    }

    private fun updateHint() {
        if (!showHints || isInitializing || currentPlayer != 1 || winner != null) {
            hint = null
            meldedCards.clear()
            return
        }
        val humanHand = playerHands[1] ?: return
        
        meldedCards.clear()
        meldedCards.addAll(AiPlayer.findAllMeldedCards(humanHand, this, 1))

        when (currentTurnPhase) {
            TurnPhase.DRAW -> {
                if (isFirstTurn) {
                    val jokers = humanHand.filter { it.rank == Rank.JOKER }
                    val identical = humanHand.groupBy { it }.filter { it.value.size >= 3 }
                    if (jokers.size >= 3) {
                        hint = Hint(title = "Special Reveal!", message = "You have 3 Jokers! Select them and press SHOW to reveal Maal and secure a bonus if you show later.", cards = jokers.take(3))
                        return
                    } else if (identical.isNotEmpty()) {
                        hint = Hint(title = "Special Reveal!", message = "You have 3 identical cards! Select them and press SHOW to reveal Maal and secure a bonus.", cards = identical.values.first().take(3))
                        return
                    }
                }

                if (hasShown[1] == false) {
                    val melds = AiPlayer.findAllInitialMelds(humanHand)
                    if (melds.size >= 3) {
                        hint = Hint(title = "Show Before Drawing", message = "You already have the 3 required melds! Select them and press SHOW to reveal Maal before taking a card.", cards = meldedCards.toList())
                        return
                    }
                }

                val cardFromDiscard = discardPile.lastOrNull()
                hint = if (cardFromDiscard != null && AiPlayer.shouldTakeCard(humanHand, cardFromDiscard, this, 1)) {
                    Hint(title = "Draw Discard", message = "You should take the ${cardFromDiscard.rank.symbol}${cardFromDiscard.suit.symbol} from the discard pile. It forms a meld or a strong connection.", cards = listOf(cardFromDiscard))
                } else {
                    Hint(title = "Tap to Draw from Stock", message = "The discard pile is useless. You should draw from the stock pile.")
                }
            }
            TurnPhase.PLAY_OR_DISCARD -> {
                if (hasShown[1] == false) {
                    val hand = playerHands[1] ?: emptyList()
                    val req = if ((bonusMaalPoints[1] ?: 0) > 0) 6 else 9
                    val possibleInitial = AiPlayer.findAllInitialMelds(hand)
                    val reqMelds = if ((bonusMaalPoints[1] ?: 0) > 0) 2 else 3

                    if (possibleInitial.size >= reqMelds) {
                        hint = Hint(
                            title = "Ready to Show",
                            message = "Select your $req cards for the melds and press SHOW.",
                            cards = possibleInitial.flatten().take(req)
                        )
                    } else {
                        val decision = AiPlayer.findCardToDiscard(hand, this, 1)
                        hint = Hint(title = "Suggested Discard", message = decision.reason, cards = listOf(decision.card))
                    }
                } else if (maalCard == null) {
                    val decision = AiPlayer.findCardToDiscard(humanHand, this, 1)
                    hint = Hint(title = "Final Step", message = "Discard ${decision.card.rank.symbol} to reveal the Maal and end your turn.", cards = listOf(decision.card))
                } else {
                    val decision = AiPlayer.findCardToDiscard(humanHand, this, 1)
                    val title = if (isFirstTurn) "Next Step" else "Strategic Discard"
                    val msg = if (isFirstTurn) "Now discard ${decision.card.rank.symbol} to end your turn and finalize your show." else decision.reason
                    hint = Hint(title = title, message = msg, cards = listOf(decision.card))
                }
            }
            TurnPhase.SHOW_OR_END -> {
                if (hasShown[1] == true) {
                    hint = Hint(title = "End Turn", message = "You have already shown. Press END TURN to let others play.")
                } else {
                    val melds = AiPlayer.findAllInitialMelds(humanHand)
                    hint = if (melds.isNotEmpty()) {
                        Hint(title = "Show Now?", message = "You should select your 9 melded cards and press SHOW, or End Turn if you want to wait.", cards = meldedCards.toList())
                    } else {
                        Hint(title = "Strategy", message = "You should end your turn if no further melds can be formed.")
                    }
                }
            }
            TurnPhase.ENDED -> hint = null
        }
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
        for (i in 1..playerCount) { 
            playerHands[i] = mutableStateListOf() 
        }
        repeat(21) {
            for (player in 1..playerCount) {
                if (cardPool.isNotEmpty()) playerHands[player]?.add(cardPool.removeAt(0))
            }
        }
        stockPile.addAll(cardPool)
    }
}
