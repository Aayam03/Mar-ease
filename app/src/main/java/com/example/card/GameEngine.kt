package com.example.card

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
    val playerResults: List<PlayerScoreResult>,
    val explanation: String
)

object GameEngine {

    /**
     * Identifies if a card is a "Maal" (point card/wildcard type).
     * Rule: All Tiplus (same rank) and point cards (Neighbors/Jokers) act as Jokers.
     */
    fun isMaal(card: Card, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        val m = maalCard ?: return false
        
        // Acts as Joker if it's a Tiplu (same rank) OR has points (Neighbor)
        return card.rank == m.rank || getMaalPoints(card, m) > 0
    }

    fun getMaalPoints(card: Card, maalCard: Card?): Int {
        val m = maalCard ?: return 0
        if (card.rank == Rank.JOKER) return 5 // Updated Standard Joker: 5 Points
        
        val v1 = card.rank.value
        val v2 = m.rank.value
        
        val isTiplu = card.rank == m.rank
        val isNeighbor = card.suit == m.suit && (
            v1 == v2 + 1 || v1 == v2 - 1 || 
            (m.rank == Rank.KING && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.TWO) ||
            (m.rank == Rank.TWO && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.KING)
        )
        
        val sameColor = (m.suit == Suit.HEARTS || m.suit == Suit.DIAMONDS) == 
                        (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS)

        return when {
            // Tiplu (Same suit): 2 Point
            isTiplu && card.suit == m.suit -> 2
            // Tiplu (Same color): 3 Points
            isTiplu && sameColor -> 3
            // Tiplu (Other color): 0 Point
            isTiplu -> 0
            // Poplu/Jhiplu (Same suit): 2 Points
            isNeighbor -> 2
            else -> 0
        }
    }

    /**
     * Calculates the detailed breakdown of Maal points for a player.
     * Includes Marriage sets, standard Maal points, and starting bonuses (Tunnelas, etc.).
     */
    fun getDetailedMaalBreakdown(
        player: Int, 
        playerHands: Map<Int, List<Card>>, 
        shownCards: Map<Int, List<Card>>, 
        hasShown: Map<Int, Boolean>, 
        maalCard: Card?,
        startingBonus: Int = 0
    ): List<MaalBreakdown> {
        if (hasShown[player] != true) return emptyList()
        val hand = (playerHands[player] ?: emptyList()) + (shownCards[player] ?: emptyList())
        val m = maalCard ?: return emptyList()
        
        val breakdown = mutableListOf<MaalBreakdown>()
        
        // 1. Starting Bonus (Tunnelas, Special Show, etc.)
        if (startingBonus > 0) {
            breakdown.add(MaalBreakdown(null, startingBonus, "Starting Bonus (Tunnelas)"))
        }

        val usedCards = mutableListOf<Card>()
        
        // 2. Marriage Detection (Tiplu, Poplu, Jhiplu of same suit)
        val suitMatch = hand.filter { it.suit == m.suit }
        val tiplus = suitMatch.filter { it.rank == m.rank }
        val poplus = suitMatch.filter { 
            it.rank.value == m.rank.value + 1 || (m.rank == Rank.KING && it.rank == Rank.ACE) || (m.rank == Rank.ACE && it.rank == Rank.TWO)
        }
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
        
        // 3. Standard Breakdown for remaining cards
        val counts = remainingHand.groupingBy { it.rank to it.suit }.eachCount()
        
        counts.forEach { (rankSuit, count) ->
            val card = remainingHand.first { it.rank == rankSuit.first && it.suit == rankSuit.second }
            val basePoints = getMaalPoints(card, m)
            
            if (basePoints > 0) {
                // Multiplier logic for multiple identical maal cards
                val totalPoints = when (count) {
                    1 -> basePoints
                    2 -> basePoints * 1.5.toInt() // Bonus for double
                    3 -> basePoints * 2 // Large bonus for triple
                    else -> basePoints * count * 2
                }
                
                val label = when (count) {
                    1 -> "Single"
                    2 -> "Double (x1.5)"
                    3 -> "Triple (x2)"
                    else -> "Multiple"
                }
                
                val reason = when (val rank = card.rank) {
                    Rank.JOKER -> "Joker ($label)"
                    m.rank -> "Tiplu ($label)"
                    else -> "Poplu/Jhiplu ($label)"
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
        startingBonus: Int = 0
    ): Int {
        if (hasShown[player] != true) return 0
        return getDetailedMaalBreakdown(player, playerHands, shownCards, hasShown, maalCard, startingBonus).sumOf { it.points }
    }

    fun getGameResult(
        winner: Int, 
        playerCount: Int, 
        playerHands: Map<Int, List<Card>>, 
        shownCards: Map<Int, List<Card>>, 
        hasShown: Map<Int, Boolean>, 
        maalCard: Card?,
        isDubliShow: Map<Int, Boolean> = emptyMap(),
        startingBonuses: Map<Int, Int> = emptyMap()
    ): GameResult {
        val maals = (1..playerCount).map { 
            calculateMaal(it, playerHands, shownCards, hasShown, maalCard, startingBonuses[it] ?: 0) 
        }
        
        val explanationBuilder = StringBuilder()
        explanationBuilder.append("Winner: Player $winner\n\n")
        
        val playerResults = (1..playerCount).map { player ->
            val idx = player - 1
            val humanMaal = maals[0]
            val playerMaal = maals[idx]
            
            var adjustment = 0
            if (player == 1) {
                val totalOthersMaal = maals.drop(1).sum()
                val baseMaalDiff = totalOthersMaal - (playerCount ) * humanMaal
                
                explanationBuilder.append("--- Final Calculation for You ---\n")
                explanationBuilder.append("Maal Difference: $totalOthersMaal (Others) - (${playerCount } × $humanMaal) (Yours) = $baseMaalDiff\n")
                
                val bonusExplanation = StringBuilder()
                val winnerAdjustment = if (winner == 1) {
                    var collect = 0
                    for (p in 2..playerCount) {
                        val isWinnerDubli = isDubliShow[1] == true
                        val winBase = if (isWinnerDubli) 5 else 3
                        val loseNoShowBase = if (isWinnerDubli) 15 else 10
                        
                        val points = if (hasShown[p] == true) winBase else loseNoShowBase
                        val reason = if (hasShown[p] == true) "showed" else "didn't show"
                        bonusExplanation.append("Player $p gave $points because they $reason.\n")
                        collect += points
                    }
                    -collect
                } else {
                    val isWinnerDubli = isDubliShow[winner] == true
                    if (hasShown[1] == true) {
                        val points = if (isDubliShow[1] == true) 0 else 3
                        bonusExplanation.append("You gave $points to Player $winner because you showed.\n")
                        points
                    } else {
                        val points = if (isWinnerDubli) 15 else 10
                        bonusExplanation.append("You gave $points to Player $winner because you didn't show.\n")
                        points
                    }
                }
                
                explanationBuilder.append("Win/Loss Bonus:\n$bonusExplanation")
                adjustment = baseMaalDiff + winnerAdjustment
                explanationBuilder.append("Total Adjustment: $baseMaalDiff + ($winnerAdjustment) = $adjustment")
            }

            PlayerScoreResult(
                player = player,
                totalMaal = playerMaal,
                adjustment = adjustment,
                breakdown = getDetailedMaalBreakdown(player, playerHands, shownCards, hasShown, maalCard, startingBonuses[player] ?: 0),
                hasShown = hasShown[player] == true,
                isDubli = isDubliShow[player] == true
            )
        }

        return GameResult(winner, playerResults, explanationBuilder.toString())
    }

    fun getFinalScoreDifference(
        winner: Int, 
        playerCount: Int, 
        playerHands: Map<Int, List<Card>>, 
        shownCards: Map<Int, List<Card>>, 
        hasShown: Map<Int, Boolean>, 
        maalCard: Card?,
        isDubliShow: Map<Int, Boolean> = emptyMap(),
        startingBonuses: Map<Int, Int> = emptyMap()
    ): Int {
        return getGameResult(winner, playerCount, playerHands, shownCards, hasShown, maalCard, isDubliShow, startingBonuses).playerResults[0].adjustment
    }

    fun getFinalScoreReason(
        winner: Int, 
        playerCount: Int, 
        playerHands: Map<Int, List<Card>>, 
        shownCards: Map<Int, List<Card>>, 
        hasShown: Map<Int, Boolean>, 
        maalCard: Card?,
        isDubliShow: Map<Int, Boolean> = emptyMap(),
        startingBonuses: Map<Int, Int> = emptyMap()
    ): String {
        return getGameResult(winner, playerCount, playerHands, shownCards, hasShown, maalCard, isDubliShow, startingBonuses).explanation
    }
}
