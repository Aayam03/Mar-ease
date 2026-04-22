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
    fun isMaal(card: Card, maalCard: Card?): Boolean {
        if (card.rank == Rank.JOKER) return true
        val m = maalCard ?: return false
        
        // Acts as Joker if it's a Tiplu (same rank) OR has points (Neighbor)
        return card.rank == m.rank || getMaalPoints(card, m) > 0
    }

    private fun isSameColor(s1: Suit, s2: Suit): Boolean {
        val redSuits = listOf(Suit.HEARTS, Suit.DIAMONDS)
        return (s1 in redSuits) == (s2 in redSuits)
    }

    fun getMaalPoints(card: Card, maalCard: Card?): Int {
        val m = maalCard ?: return 0
        if (card.rank == Rank.JOKER) return 5 // Standard Joker: 5 Points
        
        val v1 = card.rank.value
        val v2 = m.rank.value
        
        val isTiplu = card.rank == m.rank
        val isNeighbor = card.suit == m.suit && (
            v1 == v2 + 1 || v1 == v2 - 1 || 
            (m.rank == Rank.KING && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.TWO) ||
            (m.rank == Rank.TWO && card.rank == Rank.ACE) || (m.rank == Rank.ACE && card.rank == Rank.KING)
        )
        
        val sameColor = isSameColor(m.suit, card.suit)

        return when {
            // Tiplu (Same suit): 3 Points
            isTiplu && card.suit == m.suit -> 3
            // Alter Tiplu (Same color, different suit): 5 Points
            isTiplu && sameColor -> 5
            // Tiplu (Other color): 0 Point
            isTiplu -> 0
            // Poplu/Jhiplu (Same suit): 2 Points
            isNeighbor -> 2
            else -> 0
        }
    }

    private fun calculateTotalPoints(base: Int, count: Int): Int {
        if (count <= 0) return 0
        if (count == 1) return base
        return when (base) {
            2 -> if (count == 2) 5 else 8
            3 -> if (count == 2) 8 else 12
            5 -> if (count == 2) 15 else 25
            10 -> if (count == 2) 25 else 45
            else -> base * count
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
        
        // 1. Starting Bonus (Tunnelas)
        if (startingBonus > 0) {
            var remainingBonus = startingBonus
            
            // Joker Tunnelas (30 points)
            val numJokerTunnelas = remainingBonus / 30
            if (numJokerTunnelas > 0) {
                val jokerCard = Card(Suit.NONE, Rank.JOKER)
                repeat(numJokerTunnelas) {
                    breakdown.add(MaalBreakdown(jokerCard, 30, "Joker Tunnela"))
                }
                remainingBonus %= 30
            }

            // Regular Tunnelas (5 points)
            val numNormalTunnelas = remainingBonus / 5
            if (numNormalTunnelas > 0) {
                // Try to find the actual cards from the 'shown' list for UI
                val shownTunnelaCards = (shownCards[player] ?: emptyList())
                    .groupBy { it.rank to it.suit }
                    .filter { it.value.size >= 3 && it.key.first != Rank.JOKER }
                    .map { it.value.first() }
                
                repeat(numNormalTunnelas) { index ->
                    val card = shownTunnelaCards.getOrNull(index)
                    breakdown.add(MaalBreakdown(card, 5, "Tunnela"))
                }
            }
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
        if (marriageCount > 0) {
            val totalPoints = calculateTotalPoints(10, marriageCount)
            val bonus = totalPoints - (10 * marriageCount)
            val label = if (marriageCount == 1) "Single" else "*$marriageCount (+$bonus Bonus)"
            breakdown.add(MaalBreakdown(null, totalPoints, "Marriage Set ($label)"))
            
            repeat(marriageCount) {
                usedCards.add(tiplus[it]); usedCards.add(poplus[it]); usedCards.add(jhiplus[it])
            }
        }
        
        // Remaining hand after marriage
        val remainingHand = hand.toMutableList()
        usedCards.forEach { card -> 
            val idx = remainingHand.indexOfFirst { it.isSameInstance(card) }
            if (idx != -1) remainingHand.removeAt(idx)
        }
        
        // 3. Standard Breakdown for remaining cards with bonus for duplicates
        val counts = remainingHand.groupingBy { it.rank to it.suit }.eachCount()
        
        counts.forEach { (rankSuit, count) ->
            val card = remainingHand.first { it.rank == rankSuit.first && it.suit == rankSuit.second }
            val basePoints = getMaalPoints(card, m)
            
            if (basePoints > 0) {
                val totalPoints = calculateTotalPoints(basePoints, count)
                val bonus = totalPoints - (basePoints * count)
                
                val label = if (count == 1) "Single" else "*$count (+$bonus Bonus)"
                
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
                // Formula: (Count of others * Your Maal) - (Sum of others' Maal)
                val totalOthersMaal = maals.filterIndexed { i, _ -> i != 0 }.sum()
                val othersCount = playerCount - 1
                val baseMaalDiff = (othersCount * humanMaal) - totalOthersMaal
                
                explanationBuilder.append("--- Final Calculation for You ---\n")
                explanationBuilder.append("Maal Difference: ($othersCount × $humanMaal) (Yours) - $totalOthersMaal (Others) = $baseMaalDiff\n")
                
                val bonusExplanation = StringBuilder()
                
                // winnerAdjustment represents the net gain/loss from win/loss bonuses
                val winnerAdjustment = if (winner == 1) {
                    // You won: You COLLECT points from everyone else
                    var totalCollected = 0
                    for (p in 2..playerCount) {
                        val isWinnerDubli = isDubliShow[1] == true
                        val winBase = if (isWinnerDubli) 5 else 3
                        val loseNoShowBase = if (isWinnerDubli) 15 else 10
                        
                        val pointsToCollect = if (hasShown[p] == true) winBase else loseNoShowBase
                        val reason = if (hasShown[p] == true) "showed" else "didn't show"
                        bonusExplanation.append("Collected $pointsToCollect from Player $p (they $reason).\n")
                        totalCollected += pointsToCollect
                    }
                    totalCollected // Positive because you are receiving these points
                } else {
                    // You lost: You PAY points to the winner
                    val isWinnerDubli = isDubliShow[winner] == true
                    val pointsToPay = if (hasShown[1] == true) {
                        // If you showed, you pay 3 (or 0 if you also have Dubli)
                        if (isDubliShow[1] == true) 0 else 3
                    } else {
                        // If you didn't show, you pay 10 (or 15 if winner has Dubli)
                        if (isWinnerDubli) 15 else 10
                    }
                    
                    val reason = if (hasShown[1] == true) {
                        if (isDubliShow[1] == true && pointsToPay == 0) "you showed and had Dubli" else "you showed"
                    } else {
                        "you didn't show"
                    }
                    bonusExplanation.append("Paid $pointsToPay to Player $winner because $reason.\n")
                    -pointsToPay // Negative because you are losing these points
                }
                
                explanationBuilder.append("Win/Loss Bonus:\n$bonusExplanation")
                
                // FINAL FORMULA: Maal Difference + Net Win/Loss Bonus
                adjustment = baseMaalDiff + winnerAdjustment
                explanationBuilder.append("Total Adjustment: $baseMaalDiff + ($winnerAdjustment) = $adjustment\n")
                
                val finalResultLabel = if (adjustment >= 0) "WON" else "LOST"
                val absAdjustment = if (adjustment >= 0) adjustment else -adjustment
                explanationBuilder.append("\n------------------------------")
                explanationBuilder.append("\n>>> YOU $finalResultLabel $absAdjustment POINTS <<<")
                explanationBuilder.append("\n------------------------------\n")
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

    private fun getStrategy(difficulty: Difficulty): DiscardStrategy {
        return when (difficulty) {
            Difficulty.EASY -> EasyDiscardStrategy()
            Difficulty.MEDIUM -> MediumDiscardStrategy()
            Difficulty.HARD -> HardDiscardStrategy()
        }
    }

    fun getSuggestedDiscards(
        hand: List<Card>,
        gameState: GameState,
        player: Int,
        difficulty: Difficulty
    ): List<Card> {
        val strategy = getStrategy(difficulty)
        return strategy.findBestDiscard(hand, gameState, player)
    }
}
