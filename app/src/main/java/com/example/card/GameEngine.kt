package com.example.card

import kotlin.math.abs

data class MaalBreakdown(
    val card: Card,
    val points: Int,
    val reason: String
)

object GameEngine {

    fun isJoker(card: Card, player: Int, hasShown: Map<Int, Boolean>, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        if (hasShown[player] != true || maalCard == null) return false
        
        // Rule 1: Same rank as Maal Card
        if (card.rank == maalCard.rank) return true
        
        // Rule 2: Same suit (symbol), rank above or below Maal Card
        if (card.suit == maalCard.suit) {
            if (abs(card.rank.value - maalCard.rank.value) == 1) return true
        }
        
        return false
    }

    fun isPotentialJoker(card: Card, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        if (maalCard == null) return false
        
        if (card.rank == maalCard.rank) return true
        if (card.suit == maalCard.suit && abs(card.rank.value - maalCard.rank.value) == 1) return true
        
        return false
    }

    fun getDetailedMaalBreakdown(player: Int, playerHands: Map<Int, List<Card>>, shownCards: Map<Int, List<Card>>, hasShown: Map<Int, Boolean>, maalCard: Card?): List<MaalBreakdown> {
        if (hasShown[player] != true) return emptyList()
        val hand = (playerHands[player] ?: emptyList()) + (shownCards[player] ?: emptyList())
        val m = maalCard ?: return emptyList()
        
        val breakdown = mutableListOf<MaalBreakdown>()
        
        // Group by card (rank and suit) to handle multipliers for same cards
        val counts = hand.groupingBy { it }.eachCount()
        
        counts.forEach { (card, count) ->
            val basePoints = when {
                card.rank == Rank.JOKER -> 5
                card == m -> 3
                card.rank == m.rank -> {
                    val sameColor = (m.suit == Suit.HEARTS || m.suit == Suit.DIAMONDS) == 
                                    (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS)
                    if (sameColor) 5 else 0
                }
                card.suit == m.suit && abs(card.rank.value - m.rank.value) == 1 -> 2
                else -> 0
            }
            
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

    fun calculateMaal(player: Int, playerHands: Map<Int, List<Card>>, shownCards: Map<Int, List<Card>>, hasShown: Map<Int, Boolean>, maalCard: Card?): Int {
        return getDetailedMaalBreakdown(player, playerHands, shownCards, hasShown, maalCard).sumOf { it.points }
    }

    fun getFinalScoreDifference(winner: Int, playerCount: Int, playerHands: Map<Int, List<Card>>, shownCards: Map<Int, List<Card>>, hasShown: Map<Int, Boolean>, maalCard: Card?): Int {
        val maals = (1..playerCount).map { calculateMaal(it, playerHands, shownCards, hasShown, maalCard) }
        val totalMaal = maals.sum()
        val humanMaal = maals[0]
        
        // 1. Base Maal Difference calculation
        val baseDiff = totalMaal - (playerCount * humanMaal)
        
        // 2. Winner Adjustment
        val winnerAdjustment = if (winner == 1) {
            var humanCollects = 0
            for (p in 2..playerCount) {
                humanCollects += if (hasShown[p] == true) 3 else 10
            }
            -humanCollects 
        } else {
            if (hasShown[1] == true) 3 else 10
        }

        return baseDiff + winnerAdjustment
    }

    fun getFinalScoreReason(winner: Int, playerCount: Int, playerHands: Map<Int, List<Card>>, shownCards: Map<Int, List<Card>>, hasShown: Map<Int, Boolean>, maalCard: Card?): String {
        val finalDiff = getFinalScoreDifference(winner, playerCount, playerHands, shownCards, hasShown, maalCard)
        val maals = (1..playerCount).map { calculateMaal(it, playerHands, shownCards, hasShown, maalCard) }
        val status = if (finalDiff > 0) "Lose" else "Gain"
        val maalsText = (1..playerCount).joinToString(", ") { "P$it: ${maals[it-1]}" }

        return "Winner: Player $winner. Maal Points: $maalsText.\nFinal Adjustment: $finalDiff. You $status ${abs(finalDiff)} points."
    }
}
