package com.example.card

import kotlin.math.abs

data class MaalBreakdown(
    val card: Card?,
    val points: Int,
    val reason: String
)

data class PlayerScoreResult(
    val player: Int,
    val totalMaal: Int,
    val adjustment: Int,
    val breakdown: List<MaalBreakdown>,
    val hasShown: Boolean,
    val isDubli: Boolean = false
)

data class GameResult(
    val winner: Int,
    val playerResults: List<PlayerScoreResult>
)

object GameEngine {

    fun isMaal(card: Card, maalCard: Card?): Boolean {
        val m = maalCard ?: return false
        if (card.rank == Rank.JOKER) return true
        if (card.rank == m.rank) return true
        if (card.suit == m.suit && (abs(card.rank.value - m.rank.value) == 1 ||
            (m.rank == Rank.ACE && card.rank == Rank.TWO) ||
            (m.rank == Rank.TWO && card.rank == Rank.ACE))) return true
        return false
    }

    fun getMaalPoints(card: Card, maalCard: Card?): Int {
        val m = maalCard ?: return 0
        return when {
            card.rank == Rank.JOKER -> 5
            card.rank == m.rank && card.suit == m.suit -> 3
            card.rank == m.rank -> {
                val sameColor = (m.suit == Suit.HEARTS || m.suit == Suit.DIAMONDS) ==
                                (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS)
                if (sameColor) 5 else 0
            }
            card.suit == m.suit && (abs(card.rank.value - m.rank.value) == 1 ||
                                   (m.rank == Rank.ACE && card.rank == Rank.TWO) ||
                                   (m.rank == Rank.TWO && card.rank == Rank.ACE)) -> 2
            else -> 0
        }
    }

    fun isJoker(card: Card, player: Int, hasShown: Map<Int, Boolean>, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        if (hasShown[player] != true || maalCard == null) return false
        
        // Rule 1: Same rank as Maal Card
        if (card.rank == maalCard.rank) return true
        
        // Rule 2: Same suit (symbol), rank above or below Maal Card
        if (card.suit == maalCard.suit) {
            val v1 = card.rank.value
            val v2 = maalCard.rank.value
            // Standard neighbor
            if (abs(v1 - v2) == 1) return true
            // Ace-King neighbor logic (Ace is 14, King is 13, Two is 2)
            if (maalCard.rank == Rank.ACE && card.rank == Rank.TWO) return true
            if (maalCard.rank == Rank.TWO && card.rank == Rank.ACE) return true
        }
        
        return false
    }

    /**
     * Calculates the detailed breakdown of Maal points for a player.
     * Includes Marriage sets, standard Maal points, first-turn bonuses, and initial Tunnelas.
     */
    fun getDetailedMaalBreakdown(
        player: Int,
        playerHands: Map<Int, List<Card>>,
        shownCards: Map<Int, List<Card>>,
        hasShown: Map<Int, Boolean>,
        maalCard: Card?,
        firstTurnBonus: Int = 0,
        dealtTunnelasCount: Int = 0
    ): List<MaalBreakdown> {
        if (hasShown[player] != true) return emptyList()
        val hand = (playerHands[player] ?: emptyList()) + (shownCards[player] ?: emptyList())
        val m = maalCard ?: return emptyList()
        
        val breakdown = mutableListOf<MaalBreakdown>()
        
        // 1. First Turn Bonus (e.g., 3 Jokers or 3 identical cards on very first turn)
        if (firstTurnBonus > 0) {
            breakdown.add(MaalBreakdown(null, firstTurnBonus, "First Turn Special Show Bonus"))
        }

        // 2. Initial Tunnela Bonus (Dealt at start)
        repeat(dealtTunnelasCount) {
            breakdown.add(MaalBreakdown(null, 5, "Initial Tunnela Bonus"))
        }

        val usedCards = mutableListOf<Card>()

        // 3. Marriage Detection (Tiplu, Poplu, Jhiplu of same suit)
        val suitMatch = hand.filter { it.suit == m.suit }
        val tiplus = suitMatch.filter { it.rank == m.rank }
        // Poplu logic with Ace-King boundary
        val poplus = suitMatch.filter {
            it.rank.value == m.rank.value + 1 || (m.rank == Rank.KING && it.rank == Rank.ACE) || (m.rank == Rank.ACE && it.rank == Rank.TWO)
        }
        // Jhiplu logic with Ace-King boundary
        val jhiplus = suitMatch.filter {
            it.rank.value == m.rank.value - 1 || (m.rank == Rank.TWO && it.rank == Rank.ACE) || (m.rank == Rank.ACE && it.rank == Rank.KING)
        }

        val marriageCount = minOf(tiplus.size, poplus.size, jhiplus.size)
        repeat(marriageCount) {
            val t = tiplus[it]; val p = poplus[it]; val j = jhiplus[it]
            breakdown.add(MaalBreakdown(null, 10, "Marriage Set (Tiplu-Poplu-Jhiplu)"))
            usedCards.add(t); usedCards.add(p); usedCards.add(j)
        }

        // Remaining hand after marriage
        val remainingHand = hand.toMutableList()
        usedCards.forEach { card ->
            val idx = remainingHand.indexOfFirst { it.isSameInstance(card) }
            if (idx != -1) remainingHand.removeAt(idx)
        }

        // 4. Standard Breakdown for remaining cards
        val counts = remainingHand.groupingBy { it }.eachCount()
        
        counts.forEach { (card, count) ->
            val basePoints = getMaalPoints(card, m)
            
            if (basePoints > 0) {
                val totalPoints = when (count) {
                    1 -> basePoints
                    2 -> when (basePoints) {
                        2 -> 4
                        3 -> 8
                        5 -> 15
                        else -> basePoints * 2
                    }
                    3 -> when (basePoints) {
                        2 -> 10
                        3 -> 15
                        5 -> 25
                        else -> basePoints * 3
                    }
                    else -> basePoints * count // Fallback for 4+ decks
                }
                
                val label = when (count) {
                    1 -> "Standard"
                    2 -> "Double (Same Card)"
                    3 -> "Triple (Same Card)"
                    else -> "Multiple"
                }
                
                val reason = when {
                    card.rank == Rank.JOKER -> "Joker ($label)"
                    card == m -> "Maal Card ($label)"
                    card.rank == m.rank -> "Same Rank ($label)"
                    else -> "Maal Neighbor ($label)"
                }
                
                breakdown.add(MaalBreakdown(card, totalPoints, reason))
            }
        }
        
        return breakdown
    }

    fun calculateMaal(
        player: Int,
        playerHands: Map<Int, List<Card>>,
        shownCards: Map<Int, List<Card>>,
        hasShown: Map<Int, Boolean>,
        maalCard: Card?,
        firstTurnBonus: Int = 0,
        dealtTunnelasCount: Int = 0
    ): Int {
        if (hasShown[player] != true) return 0
        return getDetailedMaalBreakdown(player, playerHands, shownCards, hasShown, maalCard, firstTurnBonus, dealtTunnelasCount).sumOf { it.points }
    }

    fun getGameResult(
        winner: Int,
        playerCount: Int,
        playerHands: Map<Int, List<Card>>,
        shownCards: Map<Int, List<Card>>,
        hasShown: Map<Int, Boolean>,
        maalCard: Card?,
        isDubliShow: Map<Int, Boolean> = emptyMap(),
        firstTurnBonuses: Map<Int, Int> = emptyMap(),
        dealtTunnelas: Map<Int, Int> = emptyMap()
    ): GameResult {
        val maals = (1..playerCount).map {
            calculateMaal(it, playerHands, shownCards, hasShown, maalCard, firstTurnBonuses[it] ?: 0, dealtTunnelas[it] ?: 0)
        }

        val playerResults = (1..playerCount).map { player ->
            val idx = player - 1
            val humanMaal = maals[0]
            val playerMaal = maals[idx]

            var adjustment = 0
            if (player == 1) {
                val totalOthersMaal = maals.drop(1).sum()
                val baseMaalDiff = totalOthersMaal - (playerCount - 1) * humanMaal

                val winnerAdjustment = if (winner == 1) {
                    var collect = 0
                    for (p in 2..playerCount) {
                        // If winner is Dubli, they get +5 more points (5+13 instead of 3+10)
                        val isDubliWinner = isDubliShow[1] == true
                        val winBase = if (isDubliWinner) 5 else 3
                        val loseNoShowBase = if (isDubliWinner) 15 else 10 // Adjusted for the user's "5 more" request

                        collect += if (hasShown[p] == true) winBase else loseNoShowBase
                    }
                    -collect
                } else {
                    // Loss adjustment
                    if (hasShown[1] == true) {
                        // If they have shown, they usually pay 3 points.
                        // BUT if they shown Dubli, they don't pay anything!
                        if (isDubliShow[1] == true) 0 else 3
                    } else {
                        // If winner is Dubli, the loser (no show) pays more.
                        val isDubliWinner = isDubliShow[winner] == true
                        if (isDubliWinner) 15 else 10
                    }
                }
                adjustment = baseMaalDiff + winnerAdjustment
            }

            PlayerScoreResult(
                player = player,
                totalMaal = playerMaal,
                adjustment = adjustment,
                breakdown = getDetailedMaalBreakdown(player, playerHands, shownCards, hasShown, maalCard, firstTurnBonuses[player] ?: 0, dealtTunnelas[player] ?: 0),
                hasShown = hasShown[player] == true,
                isDubli = isDubliShow[player] == true
            )
        }

        return GameResult(winner, playerResults)
    }

    fun getFinalScoreReason(
        winner: Int,
        playerCount: Int,
        playerHands: Map<Int, List<Card>>,
        shownCards: Map<Int, List<Card>>,
        hasShown: Map<Int, Boolean>,
        maalCard: Card?,
        isDubliShow: Map<Int, Boolean> = emptyMap(),
        firstTurnBonuses: Map<Int, Int> = emptyMap(),
        dealtTunnelas: Map<Int, Int> = emptyMap()
    ): String {
        val result = getGameResult(winner, playerCount, playerHands, shownCards, hasShown, maalCard, isDubliShow, firstTurnBonuses, dealtTunnelas)
        val finalDiff = result.playerResults[0].adjustment

        val status = if (finalDiff > 0) "Lose" else "Gain"
        val maalsText = result.playerResults.joinToString(", ") {
            "P${it.player}: ${it.totalMaal}${if (it.isDubli) "(D)" else ""}"
        }

        return "Winner: Player $winner${if (isDubliShow[winner] == true) " (Dubli)" else ""}. Maal Points: $maalsText.\nFinal Adjustment: $finalDiff. You $status ${abs(finalDiff)} points."
    }
} // End of GameEngine object