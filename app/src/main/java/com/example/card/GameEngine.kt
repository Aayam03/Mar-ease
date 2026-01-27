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
    val playerResults: List<PlayerScoreResult>,
    val explanation: String
)

object GameEngine {

    /**
     * Identifies if a card is a "Maal" (point card/wildcard type).
     * In the standard 21-card variant, there are 12 types of derived point cards:
     * Tiplu (Maal rank in 4 suits), Poplu (+1 rank in 4 suits), Jhiplu (-1 rank in 4 suits).
     */
    fun isMaal(card: Card, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        val m = maalCard ?: return false
        
        val v1 = card.rank.value
        val v2 = m.rank.value
        
        // Rule: Tiplu (Same rank), Poplu (Rank + 1), Jhiplu (Rank - 1) in ALL 4 suits are point/wild cards.
        val isTiplu = v1 == v2
        val isPoplu = v1 == v2 + 1 || (m.rank == Rank.KING && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.TWO)
        val isJhiplu = v1 == v2 - 1 || (m.rank == Rank.TWO && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.KING)
        
        return isTiplu || isPoplu || isJhiplu
    }

    fun getMaalPoints(card: Card, maalCard: Card?): Int {
        val m = maalCard ?: return 0
        if (card.rank == Rank.JOKER) return 2 // Standard Joker value
        
        val v1 = card.rank.value
        val v2 = m.rank.value
        
        val isTiplu = v1 == v2
        val isPoplu = v1 == v2 + 1 || (m.rank == Rank.KING && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.TWO)
        val isJhiplu = v1 == v2 - 1 || (m.rank == Rank.TWO && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.KING)
        
        val sameColor = (m.suit == Suit.HEARTS || m.suit == Suit.DIAMONDS) == 
                        (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS)

        return when {
            // Tiplu (Main Maal)
            isTiplu && card.suit == m.suit -> 3
            // Alter Tiplu (Same Rank, Same Color)
            isTiplu && sameColor -> 2
            // Poplu/Jhiplu (Same Suit)
            (isPoplu || isJhiplu) && card.suit == m.suit -> 2
            // Alter Poplu/Jhiplu (Same Color)
            (isPoplu || isJhiplu) && sameColor -> 1
            else -> 0
        }
    }

    fun isJoker(card: Card, player: Int, hasShown: Map<Int, Boolean>, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        if (hasShown[player] != true || maalCard == null) return false
        return isMaal(card, maalCard)
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
                    2 -> basePoints * 3 // Bonus for double
                    3 -> basePoints * 5 // Large bonus for triple
                    else -> basePoints * count * 2
                }
                
                val label = when (count) {
                    1 -> "Single"
                    2 -> "Double (x3)"
                    3 -> "Triple (x5)"
                    else -> "Multiple"
                }
                
                val reason = when {
                    card.rank == Rank.JOKER -> "Joker ($label)"
                    card.rank == m.rank -> "Tiplu ($label)"
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
                val othersMaal = maals.drop(1)
                val totalOthersMaal = othersMaal.sum()
                val baseMaalDiff = totalOthersMaal - (playerCount - 1) * humanMaal
                
                explanationBuilder.append("--- Final Calculation for You ---\n")
                explanationBuilder.append("Maal Difference: $totalOthersMaal (Others) - (${playerCount - 1} × $humanMaal) (Yours) = $baseMaalDiff\n")
                
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
