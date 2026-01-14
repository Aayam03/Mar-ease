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
import kotlin.math.abs
import kotlin.random.Random

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
    val action: String,
    val reason: String,
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
    var winner by mutableStateOf<Int?>(null)
    var isInitializing by mutableStateOf(true)
    var gameMessage by mutableStateOf<String?>(null)

    var maalCard by mutableStateOf<Card?>(null)
    val hasShown = mutableStateMapOf<Int, Boolean>()
    
    // To track "Special Shows" from the first turn
    val bonusMaalPoints = mutableStateMapOf<Int, Int>()

    val selectedCards = mutableStateListOf<Card>()
    val playerIcons = mutableStateMapOf<Int, String>()
    private val availableIcons = listOf("🤖", "👽", "👾", "👺", "🤡", "👻")

    var isFirstTurn by mutableStateOf(true)
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
                // Corrected Joker count: 2 Jokers per deck
                val cardPool = createCardPool(numberOfDecks, numberOfDecks * 2)
                
                withContext(Dispatchers.Main) {
                    dealCards(playerCount, cardPool)
                    assignPlayerIcons(count)
                    
                    hasShown.clear()
                    shownCards.clear()
                    bonusMaalPoints.clear()
                    for (i in 1..playerCount) {
                        hasShown[i] = false
                        shownCards[i] = mutableStateListOf()
                        bonusMaalPoints[i] = 0
                    }
                    maalCard = null
                    winner = null
                    gameMessage = null
                    currentTurnPhase = TurnPhase.DRAW
                    isFirstTurn = true
                    isInitializing = false
                    updateHint()
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
        return GameEngine.isJoker(card, player, hasShown, maalCard)
    }

    fun calculateMaal(player: Int): Int {
        // Only include bonus if the player has actually fully shown later
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
        val breakdown = StringBuilder()
        val maals = (1..playerCount).map { p -> 
            val score = calculateMaal(p)
            breakdown.append("P$p Maal: $score. ")
            score
        }
        
        var winnerBonus = 0
        winner?.let { w ->
            for (p in 1..playerCount) {
                if (p == w) continue
                winnerBonus += if (hasShown[p] == true) 5 else 10
            }
        }

        val individualTotals = maals.mapIndexed { index, maal ->
            if (index + 1 == winner) maal + winnerBonus else maal
        }

        val totalSum = individualTotals.sum()
        val myIndTotal = individualTotals[0]
        val diff = totalSum - (myIndTotal * playerCount)
        val status = if (diff > 0) "Lose" else "Gain"

        hint = Hint(
            "Game Over!", 
            "Winner: Player $winner. $breakdown\n" +
            "Scoring Values: Joker (5), Maal Card (3), Same Rank Color (5), Neighbors (2).\n" +
            "Final Calc: $totalSum - ($myIndTotal * $playerCount) = $diff. You $status ${abs(diff)} points."
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
                isFirstTurn = false // Action taken, first turn window closed
                if (player == 1) {
                    currentTurnPhase = TurnPhase.PLAY_OR_DISCARD
                    updateHint()
                }
            } else { // DISCARD
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

            // Check if player can show now (they have 21 cards)
            if (hasShown[1] == false && AiPlayer.findAllInitialMelds(hand).isNotEmpty()) {
                currentTurnPhase = TurnPhase.SHOW_OR_END
                updateHint()
            } else {
                currentTurnPhase = TurnPhase.ENDED
                advanceTurn()
            }
        } else {
            // AI Logic
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
        // Skip jokers for the Maal card
        var index = 0
        while (index < stockPile.size && stockPile[index].rank == Rank.JOKER) {
            index++
        }
        maalCard = if (index < stockPile.size) {
            stockPile.removeAt(index)
        } else {
            // All cards in stock are jokers (impossible with standard decks)
            stockPile.removeAt(0)
        }
    }

    private fun showGameMessage(msg: String) {
        viewModelScope.launch {
            gameMessage = msg
            delay(2500) // Slightly longer message display
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
        
        // Explain why not to throw away Jokers
        if (isJoker(card, 1)) {
            showGameMessage("Don't throw away a Joker! It can replace any card in a meld, making it the most valuable card in your hand.")
            return
        }

        if (hand.contains(card)) {
            moveCard(card, 1, AnimationType.DISCARD, AnimationSource.PLAYER) {
                val idx = hand.indexOfFirst { it === card }
                if (idx != -1) hand.removeAt(idx)
                discardPile.add(card)
            }
        }
    }

    fun humanShows() {
        if (isInitializing || currentPlayer != 1 || (currentTurnPhase != TurnPhase.DRAW && currentTurnPhase != TurnPhase.PLAY_OR_DISCARD && currentTurnPhase != TurnPhase.SHOW_OR_END)) return
        val hand = playerHands[1] ?: return

        // Case 1: Special first turn show (3 jokers or 3 identical cards)
        // Does NOT mark hasShown[1] = true, only records potential bonus and reveals Maal.
        if (isFirstTurn && currentTurnPhase == TurnPhase.DRAW && hasShown[1] == false) {
            if (selectedCards.size == 3) {
                val jokers = selectedCards.filter { it.rank == Rank.JOKER }
                val identical = selectedCards.all { it.rank == selectedCards[0].rank && it.suit == selectedCards[0].suit }
                
                if (jokers.size == 3 || identical) {
                    val bonus = if (jokers.size == 3) 25 else 10
                    val cardsToShow = selectedCards.toList()
                    hand.removeByReference(cardsToShow)
                    shownCards[1]?.addAll(cardsToShow)
                    bonusMaalPoints[1] = bonus
                    // Note: hasShown remains false until a standard 3-meld show occurs
                    isFirstTurn = false
                    showGameMessage("Special Maal Reveal! +$bonus Bonus pending.")
                    
                    pickMaalCard()
                    humanDrawsFromStock()
                    return
                }
            }
        }

        // Case 2: Standard show (9 cards / 3 melds)
        if (hand.size == 21 && selectedCards.size == 9 && hasShown[1] == false) {
            val melds = AiPlayer.findAllInitialMelds(selectedCards.toList())
            if (melds.size >= 3) {
                val meldedCards = melds.flatten()
                hand.removeByReference(meldedCards)
                shownCards[1]?.addAll(meldedCards)
                hasShown[1] = true
                showGameMessage("You have shown!")
                
                pickMaalCard()
                
                currentTurnPhase = TurnPhase.ENDED
                advanceTurn()
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
        
        // Use reference equality (===) to select individual cards even if they have the same rank/suit
        val isAlreadySelected = selectedCards.any { it === card }

        // Allowed to select cards in DRAW phase ONLY for special first turn show
        if (currentTurnPhase == TurnPhase.DRAW && isFirstTurn) {
            if (isAlreadySelected) {
                selectedCards.removeAll { it === card }
            } else {
                if (selectedCards.size < 3) selectedCards.add(card)
            }
            return
        }

        if (currentTurnPhase == TurnPhase.SHOW_OR_END || (currentTurnPhase == TurnPhase.PLAY_OR_DISCARD && hasShown[1] != true)) {
            if (isAlreadySelected) {
                selectedCards.removeAll { it === card }
            } else {
                selectedCards.add(card)
            }
        } else {
            if (isAlreadySelected) {
                selectedCards.removeAll { it === card }
            } else {
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
        viewModelScope.launch {
            val aiHand = playerHands[currentPlayer] ?: return@launch
            try {
                // AI Special Show Check
                if (isFirstTurn) {
                    val jokers = aiHand.filter { it.rank == Rank.JOKER }
                    val identical = aiHand.groupBy { it }.filter { it.value.size >= 3 }
                    
                    if (jokers.size >= 3) {
                        val cards = jokers.take(3)
                        aiHand.removeByReference(cards)
                        shownCards[currentPlayer]?.addAll(cards)
                        bonusMaalPoints[currentPlayer] = 25
                        pickMaalCard()
                    } else if (identical.isNotEmpty()) {
                        val cards = identical.values.first().take(3)
                        aiHand.removeByReference(cards)
                        shownCards[currentPlayer]?.addAll(cards)
                        bonusMaalPoints[currentPlayer] = 10
                        pickMaalCard()
                    }
                }

                delay(500)
                val cardFromDiscard = discardPile.lastOrNull()
                
                if (cardFromDiscard != null && AiPlayer.shouldTakeCard(aiHand, cardFromDiscard, this@GameState, currentPlayer)) {
                    val card = discardPile.last()
                    moveCard(card, currentPlayer, AnimationType.DRAW, AnimationSource.DISCARD) {
                        discardPile.removeAt(discardPile.lastIndex)
                        aiHand.add(card)
                    }
                } else if (stockPile.isNotEmpty()) {
                    val card = stockPile.first()
                    moveCard(card, currentPlayer, AnimationType.DRAW, AnimationSource.STOCK) {
                        stockPile.removeAt(0)
                        aiHand.add(card)
                    }
                } else {
                    advanceTurn()
                    return@launch
                }
                
                delay(1000) 
                if (winner != null) return@launch

                // Play Phase
                val cardToDiscard = AiPlayer.findCardToDiscard(aiHand, this@GameState, currentPlayer)
                moveCard(cardToDiscard, currentPlayer, AnimationType.DISCARD, AnimationSource.PLAYER) {
                    val idx = aiHand.indexOfFirst { it === cardToDiscard }
                    if (idx != -1) aiHand.removeAt(idx)
                    discardPile.add(cardToDiscard)
                }

            } catch (ignore: Exception) {
                if (winner == null) advanceTurn()
            }
        }
    }

    private fun updateHint() {
        if (!showHints || isInitializing || currentPlayer != 1 || winner != null) {
            hint = null
            return
        }
        val humanHand = playerHands[1] ?: return
        
        when (currentTurnPhase) {
            TurnPhase.DRAW -> {
                if (isFirstTurn) {
                    val jokers = humanHand.filter { it.rank == Rank.JOKER }
                    val identical = humanHand.groupBy { it }.filter { it.value.size >= 3 }
                    if (jokers.size >= 3) {
                        hint = Hint("Special Reveal!", "You have 3 Jokers! Select them and press SHOW to reveal Maal and secure a bonus if you show later.", jokers.take(3))
                        return
                    } else if (identical.isNotEmpty()) {
                        hint = Hint("Special Reveal!", "You have 3 identical cards! Select them and press SHOW to reveal Maal and secure a bonus.", identical.values.first().take(3))
                        return
                    }
                }

                val cardFromDiscard = discardPile.lastOrNull()
                hint = if (cardFromDiscard != null && AiPlayer.shouldTakeCard(humanHand, cardFromDiscard, this, 1)) {
                    Hint("Draw Discard", "You should take the ${cardFromDiscard.rank.symbol}${cardFromDiscard.suit.symbol} from the discard pile. It forms a meld or a strong connection.", listOf(cardFromDiscard))
                } else {
                    Hint("Draw from Stock", "The discard pile is useless. You should draw from the stock pile.")
                }
            }
            TurnPhase.PLAY_OR_DISCARD -> {
                if (hasShown[1] == false) {
                    val melds = AiPlayer.findAllInitialMelds(humanHand)
                    if (melds.size >= 3 && humanHand.size == 21) {
                        hint = Hint("Choose Melds to Show", "You should select the 9 cards forming your 3 melds, then press SHOW. Jokers cannot be used in runs for the initial show.", melds.flatten())
                    } else if (melds.size >= 3 && humanHand.size == 22) {
                        val options = AiPlayer.findBestDiscardOptions(humanHand, this, 1)
                        hint = Hint("Discard to Show", "You have the required 3 melds! However, you must first discard one card to have exactly 21 before you can perform a 'Show'.", options)
                    } else {
                        val cardToDiscard = AiPlayer.findCardToDiscard(humanHand, this, 1)
                        val hasGapRuns = humanHand.any { AiPlayer.isPartOfGapRun(it, humanHand, this, 1) }
                        val reason = when {
                            isJoker(cardToDiscard, 1) -> "Internal Error: Never discard Jokers!"
                            hasGapRuns && AiPlayer.isPartOfPair(cardToDiscard, humanHand, this, 1) -> 
                                "Discarding the ${cardToDiscard.rank} because a Gap Run has a higher probability of completion than a Pair."
                            else -> "This card has the least connection to your other cards."
                        }
                        hint = Hint("Discard", reason, listOf(cardToDiscard))
                    }
                } else {
                    if (AiPlayer.canFinish(humanHand, this, 1)) {
                        hint = Hint("Finish!", "You have 7 valid melds. You should discard any 13th card to win!")
                    } else {
                        val cardToDiscard = AiPlayer.findCardToDiscard(humanHand, this, 1)
                        val hasGapRuns = humanHand.any { AiPlayer.isPartOfGapRun(it, humanHand, this, 1) }
                        val reason = when {
                            isJoker(cardToDiscard, 1) -> "Internal Error: Never discard Jokers!"
                            hasGapRuns && AiPlayer.isPartOfPair(cardToDiscard, humanHand, this, 1) -> 
                                "Discarding the ${cardToDiscard.rank} because a Gap Run has a higher probability of completion than a Pair."
                            else -> "This card has the least connection to your other cards."
                        }
                        hint = Hint("Discard", reason, listOf(cardToDiscard))
                    }
                }
            }
            TurnPhase.SHOW_OR_END -> {
                val melds = AiPlayer.findAllInitialMelds(humanHand)
                hint = if (melds.isNotEmpty()) {
                    Hint("Show Now?", "You should select your 9 melded cards and press SHOW, or End Turn if you want to wait.", melds.flatten())
                } else {
                    Hint("Strategy", "You should end your turn if no further melds can be formed.")
                }
            }
            TurnPhase.ENDED -> hint = null
        }
    }

    private fun MutableList<Card>.removeByReference(toRemove: List<Card>) {
        toRemove.forEach { card ->
            val idx = indexOfFirst { it === card }
            if (idx != -1) removeAt(idx)
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
        playerHands.clear(); stockPile.clear()
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
