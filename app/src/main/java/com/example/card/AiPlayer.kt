package com.example.card

import kotlin.math.abs

object AiPlayer {

    /**
     * Finds 3 non-overlapping melds for the initial 'show'.
     * Valid initial melds: Runs or Identical Triples (Same rank and same suit 3 times).
     */
    fun findAllInitialMelds(hand: List<Card>): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        val available = hand.toMutableList()
        var safetyCounter = 0

        // 1. Find Runs
        while (result.size < 3 && safetyCounter < 30) {
            safetyCounter++
            val run = findSingleInitialRun(available) ?: break
            result.add(run)
            available.removeByReference(run)
        }

        // 2. Find Identical Triples (Same rank AND same suit)
        while (result.size < 3 && safetyCounter < 60) {
            safetyCounter++
            val triple = findSingleInitialTriple(available) ?: break
            result.add(triple)
            available.removeByReference(triple)
        }

        // 3. 3 Jokers (Special case)
        if (result.size < 3) {
            val jokers = available.filter { it.rank == Rank.JOKER }
            if (jokers.size >= 3) {
                val set = jokers.take(3)
                result.add(set)
                available.removeByReference(set)
            }
        }

        return if (result.size >= 3) result.take(3) else emptyList()
    }

    private fun findSingleInitialRun(cards: List<Card>): List<Card>? {
        val nonJokers = cards.filter { it.rank != Rank.JOKER }
        return nonJokers.groupBy { it.suit }.values.firstNotNullOfOrNull { suitCards ->
            val withValues = suitCards.flatMap { c -> 
                if (c.rank == Rank.ACE) listOf(c to 1, c to 14) else listOf(c to c.rank.value)
            }.sortedBy { it.second }

            for (i in 0 until withValues.size - 2) {
                for (j in i + 1 until withValues.size - 1) {
                    for (k in j + 1 until withValues.size) {
                        val a = withValues[i]; val b = withValues[j]; val c = withValues[k]
                        if (!a.first.isSameInstance(b.first) && !b.first.isSameInstance(c.first) && !a.first.isSameInstance(c.first)) {
                            if (a.second + 1 == b.second && b.second + 1 == c.second) {
                                return listOf(a.first, b.first, c.first)
                            }
                        }
                    }
                }
            }
            null
        }
    }

    private fun findSingleInitialTriple(cards: List<Card>): List<Card>? {
        val nonJokers = cards.filter { it.rank != Rank.JOKER }
        // For INITIAL show, ONLY identical cards (same rank and same suit) are valid
        val identicalGroup = nonJokers.groupBy { it.rank to it.suit }.values.firstOrNull { it.size >= 3 }
        return identicalGroup?.take(3)
    }

    fun canFinish(hand: List<Card>, gameState: GameState, player: Int): Boolean {
        val totalCards = (gameState.shownCards[player] ?: emptyList()) + hand
        if (totalCards.size !in 21..22) return false
        
        return when (totalCards.size) {
            22 -> findMeldsRecursive(totalCards.toMutableList(), gameState, player, 0, canDiscard = true, 0)
            21 -> findMeldsRecursive(totalCards.toMutableList(), gameState, player, 0, canDiscard = false, 0)
            else -> false
        }
    }

    private fun findMeldsRecursive(
        cards: MutableList<Card>, 
        gameState: GameState, 
        player: Int, 
        meldCount: Int, 
        canDiscard: Boolean,
        depth: Int
    ): Boolean {
        if (meldCount == 7) return true
        if (cards.isEmpty() || depth > 80) return false 

        if (canDiscard) {
            val first = cards.removeAt(0)
            if (findMeldsRecursive(cards, gameState, player, meldCount, false, depth + 1)) return true
            cards.add(0, first)
        }

        val jokers = cards.filter { gameState.isJoker(it, player) }
        // Only use Joker melds after 'show'
        if (jokers.size >= 2 && gameState.hasShown[player] == true) {
            val combo = jokers.take(2)
            val nextCards = cards.toMutableList()
            nextCards.removeByReference(combo)
            if (findMeldsRecursive(nextCards, gameState, player, meldCount + 1, canDiscard, depth + 1)) return true
        }

        if (cards.size < 3) return false
        val first = cards[0]
        val rest = cards.subList(1, cards.size)
        
        val potential = rest.filter { 
            (it.rank == first.rank && it.suit == first.suit) || it.suit == first.suit || gameState.isJoker(it, player) || gameState.isJoker(first, player)
        }

        for (i in 0 until potential.size - 1) { 
            for (j in i + 1 until potential.size) {
                val combo = listOf(first, potential[i], potential[j])
                if (isValidGeneralMeld(combo, gameState, player)) {
                    val nextCards = cards.toMutableList()
                    nextCards.removeByReference(combo)
                    if (findMeldsRecursive(nextCards, gameState, player, meldCount + 1, canDiscard, depth + 1)) return true
                }
            }
        }
        return false
    }

    fun isValidGeneralMeld(cards: List<Card>, gameState: GameState, player: Int): Boolean {
        // A meld is either 3 cards, or 2 Jokers (special pair)
        if (cards.size != 3 && cards.size != 2) return false

        val hasShown = gameState.hasShown[player] == true
        val jokers = cards.filter { gameState.isJoker(it, player) }
        val nonJokers = cards.filter { !gameState.isJoker(it, player) }

        // Rule 1: Only use Joker after 'show'
        if (jokers.isNotEmpty() && !hasShown) return false

        // Special case: 2 Jokers (treated as a meld only after show)
        if (cards.size == 2) {
            return jokers.size == 2 && hasShown
        }

        // If there are jokers in a 3-card meld
        if (jokers.isNotEmpty()) {
            // Case A: 3 Jokers is always valid (if shown)
            if (nonJokers.isEmpty()) return true

            // Case B: Joker for a Triple (Same Rank, Different Suits)
            // This MUST come first or be handled strictly to avoid Identical Triple confusion
            if (nonJokers.all { it.rank == nonJokers[0].rank }) {
                // RULE: Only allowed for Standard Triple (Different Suits)
                // If we have 2 non-jokers, they must have different suits.
                // If they have the same suit, it's an Identical Triple attempt -> RETURN FALSE
                return nonJokers.distinctBy { it.suit }.size == nonJokers.size
            }

            // Case C: Joker for a Run (Same Suit, Sequential)
            if (nonJokers.all { it.suit == nonJokers[0].suit }) {
                // Safety: If they are the same suit but same rank, it's an Identical Triple.
                // Your rule says: No Jokers for Identical Triples.
                if (nonJokers.size == 2 && nonJokers[0].rank == nonJokers[1].rank) {
                    return false
                }

                // Sequence check for 2 cards + 1 joker
                if (nonJokers.size == 2) {
                    val vSets = nonJokers.map { c -> 
                        if (c.rank == Rank.ACE) listOf(1, 14) else listOf(c.rank.value) 
                    }
                    for (v1 in vSets[0]) {
                        for (v2 in vSets[1]) {
                            // Gap must be 1 (consecutive) or 2 (one card missing)
                            // e.g., 4 and 5 (gap 1) + Joker = 4,5,J
                            // e.g., 4 and 6 (gap 2) + Joker = 4,J,6
                            if (abs(v1 - v2) in 1..2) return true
                        }
                    }
                    return false
                }
                return true // 1 non-joker + 2 jokers + same suit = valid run
            }

            return false
        }

        // --- No Jokers Logic ---
        val first = cards[0]

        // 1. Run check: same suit, sequential
        if (cards.all { it.suit == first.suit }) {
            val vSets = cards.map { c -> if (c.rank == Rank.ACE) listOf(1, 14) else listOf(c.rank.value) }
            for (v1 in vSets[0]) for (v2 in vSets[1]) for (v3 in vSets[2]) {
                val s = listOf(v1, v2, v3).sorted()
                if (s[0] + 1 == s[1] && s[1] + 1 == s[2]) return true
            }
        }

        // 2. Identical Triple check: same rank and same suit (Allowed without jokers)
        if (cards.all { it.rank == first.rank && it.suit == first.suit }) return true

        // 3. Standard Triple check: same rank, all 3 suits different
        if (cards.all { it.rank == first.rank } && cards.distinctBy { it.suit }.size == 3) return true

        return false
    }

    /**
     * Improved Potential Value calculation logic.
     * Priorities:
     * 1. Combination (Run or Triple) in the same symbol (suit).
     * 2. Gap Run (also same symbol).
     * 3. Set Potential (Same rank, different symbols/suits) - only if no suit combinations exist.
     */
    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int): Int {
        if (gameState.isJoker(card, player)) return 1000

        // 1. First search for possible Combination (Run and Triple) in the same symbol (suit)
        val hasConsecutiveRun = isPartOfConsecutiveRun(card, hand, gameState, player)
        val hasIdenticalTriple = isPartOfIdenticalMatch(card, hand, gameState, player)
        
        if (hasConsecutiveRun) return 100
        if (hasIdenticalTriple) return 90

        // 2. Check for Gap Run (also same symbol/suit combination)
        val hasGapRun = isPartOfGapRun(card, hand, gameState, player)
        if (hasGapRun) return 70

        // 3. Among the cards that do not have a possible combination (gap or continuous Run)
        // Check for the possibility of 3 cards having Same rank but different Symbols (Set potential)
        val sameRankOthers = hand.filter {
            !it.isSameInstance(card) &&
            it.rank == card.rank &&
            it.suit != card.suit && // Different symbols
            !gameState.isJoker(it, player)
        }
        
        if (sameRankOthers.isNotEmpty()) {
            val uniqueSuitsCount = (sameRankOthers + card).distinctBy { it.suit }.size
            // Priority for Set potential (higher for triples, lower for pairs)
            return if (uniqueSuitsCount >= 3) 60 else 50
        }

        return 0
    }

    fun isPartOfConsecutiveRun(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 1 } }
            }()
        }
    }

    fun isPartOfGapRun(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 2 } }
            }()
        }
    }

    fun isPartOfIdenticalMatch(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        return others.any { it.rank == card.rank && it.suit == card.suit }
    }

    fun isPartOfPair(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        return others.any { it.rank == card.rank }
    }

    fun findBestDiscardOptions(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val nonJokers = hand.filter { !gameState.isJoker(it, player) }
        if (nonJokers.isEmpty()) return listOf(hand.first())

        // 1. Identify cards already part of a meld to protect them
        val meldedCards = mutableSetOf<Card>()
        val temp = hand.toMutableList()
        var safety = 0
        while (safety < 7) {
            safety++
            val m = findAnyMeldGreedy(temp, gameState, player) ?: break
            meldedCards.addAll(m); temp.removeByReference(m)
        }

        val scores = nonJokers.map { card ->
            // Highest priority: Keep melded cards
            if (meldedCards.any { it.isSameInstance(card) }) return@map card to 10000 

            var score = calculateCardPotential(card, hand, gameState, player)
            
            // ACE and KING have fewer run possibilities (cannot form K-A-2)
            if (card.rank == Rank.ACE || card.rank == Rank.KING) {
                score -= 1
            }
            
            card to score
        }

        val minScore = scores.minOf { it.second }
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): Card {
        return findBestDiscardOptions(hand, gameState, player).first()
    }

    fun shouldTakeCard(hand: List<Card>, cardToConsider: Card, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(cardToConsider, player)) return true
        
        // Special case for first turn special show
        if (gameState.isFirstTurn && gameState.hasShown[player] != true) {
            val totalHand = hand + cardToConsider
            val has3Jokers = totalHand.count { it.rank == Rank.JOKER } >= 3
            val has3Identical = totalHand.groupBy { it.rank to it.suit }.any { it.value.size >= 3 }
            if (has3Jokers || has3Identical) return true
        }

        if (hand.any { it.rank == cardToConsider.rank && it.suit == cardToConsider.suit && !gameState.isJoker(it, player) }) return true

        val suitCards = hand.filter { it.suit == cardToConsider.suit && !gameState.isJoker(it, player) }
        val cVals = if (cardToConsider.rank == Rank.ACE) listOf(1, 14) else listOf(cardToConsider.rank.value)
        for (cv in cVals) {
            for (other in suitCards) {
                val ovs = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                for (ov in ovs) {
                    if (abs(cv - ov) in 1..2) return true 
                }
            }
        }

        if (gameState.hasShown[player] != true) {
            val before = findAllInitialMelds(hand).isNotEmpty()
            val after = findAllInitialMelds(hand + cardToConsider).isNotEmpty()
            return after && !before
        }
        fun countMelds(c: List<Card>): Int {
            val temp = c.toMutableList(); var count = 0
            var safety = 0
            while (safety < 7) {
                safety++
                val m = findAnyMeldGreedy(temp, gameState, player) ?: break
                count++; temp.removeByReference(m)
            }
            return count
        }
        return countMelds(hand + cardToConsider) > countMelds(hand)
    }

    private fun findAnyMeldGreedy(cards: List<Card>, gameState: GameState, player: Int): List<Card>? {
        val hasShown = gameState.hasShown[player] == true
        if (cards.size < 3) {
            val jokers = cards.filter { gameState.isJoker(it, player) }
            if (jokers.size >= 2 && hasShown) return jokers.take(2)
            return null
        }
        
        val jokers = cards.filter { gameState.isJoker(it, player) }
        if (jokers.size >= 2 && hasShown) return jokers.take(2)

        for (i in 0 until cards.size - 2) {
            for (j in i + 1 until cards.size - 1) {
                for (k in j + 1 until cards.size) {
                    val combo = listOf(cards[i], cards[j], cards[k])
                    if (isValidGeneralMeld(combo, gameState, player)) return combo
                }
            }
        }
        return null
    }

    private fun MutableList<Card>.removeByReference(toRemove: List<Card>) {
        toRemove.forEach { card ->
            val idx = indexOfFirst { it.isSameInstance(card) }
            if (idx != -1) removeAt(idx)
        }
    }
}
