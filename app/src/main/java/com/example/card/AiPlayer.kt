package com.example.card

import java.util.BitSet
import kotlin.math.abs

data class DiscardDecision(val card: Card, val reason: String)

object AiPlayer {

    private val playerStrategies = mutableMapOf<Int, Boolean>() // player ID to isAimingForDubli

    fun isAimingForDubli(player: Int, hand: List<Card>): Boolean {
        if (playerStrategies.containsKey(player)) return playerStrategies[player]!!
        
        val dublis = findDublis(hand)
        val aiming = dublis.size >= 5
        playerStrategies[player] = aiming
        return aiming
    }

    /**
     * Recommends a strategy based on the current hand.
     * Returns a Pair of (Strategy Name, Reason)
     */
    fun recommendStrategy(hand: List<Card>): Pair<String, String> {
        val dublis = findDublis(hand)
        val melds = findAllInitialMelds(hand)
        
        return if (dublis.size >= 4) {
            "Dubli Strategy" to "You have ${dublis.size} pairs already. Collecting 7 pairs for a Dubli show is likely your fastest path to revealing Maal."
        } else if (melds.isNotEmpty()) {
            "Meld Strategy" to "You already have ${melds.size} initial meld(s). Focus on completing 3 pure sequences or identical triples to show."
        } else {
            "Meld Strategy" to "Your hand is balanced. Try to form sequences (runs) or triples of the same rank to reach 3 melds."
        }
    }

    fun findDublis(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.rank to it.suit }
            .map { it.value }
            .flatMap { group ->
                group.chunked(2).filter { it.size == 2 }
            }
    }
    /**
     * Finds all cards that are currently part of any valid meld in the hand.
     * Takes into account both Melds and Dublis to protect them.
     */
    fun findAllMeldedCards(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val dublis = findDublis(hand)
        
        if (gameState.isDubliShow[player] == true || isAimingForDubli(player, hand)) {
            return dublis.flatten()
        }

        val melds = findAllMelds(hand, gameState, player)
        val meldedCards = melds.flatten()
        val dubliCards = if (dublis.size >= 4) dublis.flatten() else emptyList()
        
        // Union of cards contributing to both potential systems.
        val combined = (meldedCards + dubliCards).distinctBy { it.id }
        
        return combined
    }

    private fun findAllMelds(hand: List<Card>, gameState: GameState, player: Int): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        val used = BitSet(hand.size)
        
        var safety = 0
        while (safety < 8) {
            safety++
            val mIndices = findAnyMeldGreedyIndices(hand, used, gameState, player, ignoreShownStatus = true) ?: break
            val m = mIndices.map { hand[it] }
            result.add(m)
            mIndices.forEach { used.set(it) }
        }
        return result
    }

    private fun findThreeJokers(cards: List<Card>): List<Card>? {
        val jokers = cards.filter { it.rank == Rank.JOKER }
        return if (jokers.size >= 3) jokers.take(3) else null
    }

    fun findAllInitialMelds(hand: List<Card>): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        val available = hand.toMutableList()
        var safetyCounter = 0

        while (result.size < 3 && safetyCounter < 30) {
            safetyCounter++
            val run = findSingleInitialRun(available) ?: break
            result.add(run)
            available.removeByReference(run)
        }

        while (result.size < 3 && safetyCounter < 60) {
            safetyCounter++
            val triple = findSingleInitialTriple(available) ?: break
            result.add(triple)
            available.removeByReference(triple)
        }

        if (result.size < 3) {
            val jokers = findThreeJokers(available)
            if (jokers != null) {
                result.add(jokers)
                available.removeByReference(jokers)
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
        val identicalGroup = nonJokers.groupBy { it.rank to it.suit }.values.firstOrNull { it.size >= 3 }
        return identicalGroup?.take(3)
    }

    fun canFinish(hand: List<Card>, gameState: GameState, player: Int): Boolean {
        val totalCards = (gameState.shownCards[player] ?: emptyList()) + hand
        if (totalCards.size !in 21..22) return false
        
        val dublis = findDublis(totalCards)
        if (dublis.size >= 8) return true
        
        if (gameState.isDubliShow[player] == true || isAimingForDubli(player, totalCards)) return false

        val used = BitSet(totalCards.size)
        return when (totalCards.size) {
            22 -> findMeldsRecursive(totalCards, used, gameState, player, 0, canDiscard = true, 0)
            21 -> findMeldsRecursive(totalCards, used, gameState, player, 0, canDiscard = false, 0)
            else -> false
        }
    }

    fun findWinningDiscard(hand: List<Card>, gameState: GameState, player: Int): Card? {
        if (hand.size != 22) return null
        val totalHand = (gameState.shownCards[player] ?: emptyList()) + hand

        // 1. Dubli Win (8 pairs)
        val dublis = findDublis(totalHand)
        if (dublis.size >= 8) {
            val pairedIds = dublis.take(8).flatten().map { it.id }.toSet()
            // Find a card in hand that isn't part of the 8 winning pairs
            return hand.find { !pairedIds.contains(it.id) } ?: hand.first()
        }

        // 2. Regular Meld Win
        for (i in hand.indices) {
            val candidate = hand.toMutableList()
            val discard = candidate.removeAt(i)
            val fullHand = (gameState.shownCards[player] ?: emptyList()) + candidate
            if (findMeldsRecursive(fullHand, BitSet(fullHand.size), gameState, player, 0, canDiscard = false, 0)) {
                return discard
            }
        }
        return null
    }

    private fun findMeldsRecursive(
        allCards: List<Card>,
        used: BitSet,
        gameState: GameState,
        player: Int, 
        meldCount: Int, 
        canDiscard: Boolean,
        depth: Int
    ): Boolean {
        if (meldCount == 7) return true
        
        val firstIdx = used.nextClearBit(0)
        if (firstIdx >= allCards.size || depth > 80) return false 

        if (canDiscard) {
            used.set(firstIdx)
            if (findMeldsRecursive(allCards, used, gameState, player, meldCount, false, depth + 1)) return true
            used.clear(firstIdx)
        }

        val first = allCards[firstIdx]

        // Option A: Try to form natural melds first (to save Jokers/Maal for later)
        if (!gameState.isJoker(first, player) && allCards.size - used.cardinality() >= 3) {
            for (i in firstIdx + 1 until allCards.size - 1) {
                if (used.get(i) || gameState.isJoker(allCards[i], player)) continue
                val second = allCards[i]
                
                if (first.rank != second.rank && first.suit != second.suit) continue

                for (j in i + 1 until allCards.size) {
                    if (used.get(j) || gameState.isJoker(allCards[j], player)) continue
                    val third = allCards[j]
                    
                    if (isValidGeneralMeld(first, second, third, gameState, player)) {
                        used.set(firstIdx); used.set(i); used.set(j)
                        if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, canDiscard, depth + 1)) return true
                        used.clear(j); used.clear(i); used.clear(firstIdx)
                    }
                }
            }
        }

        // Option B: Use Jokers/Maal as wildcards
        if (gameState.hasShown[player] == true) {
            // 2-Joker meld
            if (gameState.isJoker(first, player)) {
                for (i in firstIdx + 1 until allCards.size) {
                    if (!used.get(i) && gameState.isJoker(allCards[i], player)) {
                        used.set(firstIdx); used.set(i)
                        if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, canDiscard, depth + 1)) return true
                        used.clear(i); used.clear(firstIdx)
                    }
                }
            }
            
            // 1 Joker + 2 other cards
            if (allCards.size - used.cardinality() >= 3) {
                for (i in firstIdx + 1 until allCards.size - 1) {
                    if (used.get(i)) continue
                    for (j in i + 1 until allCards.size) {
                        if (used.get(j)) continue
                        if (isValidGeneralMeld(first, allCards[i], allCards[j], gameState, player)) {
                            used.set(firstIdx); used.set(i); used.set(j)
                            if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, canDiscard, depth + 1)) return true
                            used.clear(j); used.clear(i); used.clear(firstIdx)
                        }
                    }
                }
            }
        }

        return false
    }

    internal fun isPotentiallyRelated(c1: Card, c2: Card, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(c1, player) || gameState.isJoker(c2, player)) return true
        if (c1.rank == c2.rank) return true
        if (c1.suit == c2.suit) return true
        return false
    }

    fun isValidGeneralMeld(c1: Card, c2: Card, c3: Card, gameState: GameState, player: Int, ignoreShownStatus: Boolean = false): Boolean {
        val cards = listOf(c1, c2, c3)
        val hasShown = gameState.hasShown[player] == true || ignoreShownStatus
        val jokers = cards.filter { gameState.isJoker(it, player) }
        val nonJokers = cards.filter { !gameState.isJoker(it, player) }

        if (jokers.isNotEmpty() && !hasShown) return false

        if (jokers.isNotEmpty()) {
            if (nonJokers.isEmpty()) return true

            if (nonJokers.all { it.rank == nonJokers[0].rank }) {
                return nonJokers.distinctBy { it.suit }.size == nonJokers.size
            }

            if (nonJokers.all { it.suit == nonJokers[0].suit }) {
                if (nonJokers.size == 2 && nonJokers[0].rank == nonJokers[1].rank) {
                    return false
                }

                if (nonJokers.size == 2) {
                    val vSets = nonJokers.map { c -> 
                        if (c.rank == Rank.ACE) listOf(1, 14) else listOf(c.rank.value) 
                    }
                    for (v1 in vSets[0]) {
                        for (v2 in vSets[1]) {
                            if (abs(v1 - v2) in 1..2) return true
                        }
                    }
                    return false
                }
                return true 
            }

            return false
        }

        if (cards.all { it.suit == c1.suit }) {
            val vSets = cards.map { c -> if (c.rank == Rank.ACE) listOf(1, 14) else listOf(c.rank.value) }
            for (v1 in vSets[0]) for (v2 in vSets[1]) for (v3 in vSets[2]) {
                val s = listOf(v1, v2, v3).sorted()
                if (s[0] + 1 == s[1] && s[1] + 1 == s[2]) return true
            }
        }

        if (cards.all { it.rank == c1.rank && it.suit == c1.suit }) return true

        if (cards.all { it.rank == c1.rank } && cards.distinctBy { it.suit }.size == 3) return true

        return false
    }

    // Overload for compatibility or 2-card Joker melds
    fun isValidGeneralMeld(cards: List<Card>, gameState: GameState, player: Int, ignoreShownStatus: Boolean = false): Boolean {
        if (cards.size == 3) return isValidGeneralMeld(cards[0], cards[1], cards[2], gameState, player, ignoreShownStatus)
        if (cards.size != 2) return false

        val hasShown = gameState.hasShown[player] == true || ignoreShownStatus
        val jokers = cards.filter { gameState.isJoker(it, player) }

        if (jokers.isNotEmpty() && !hasShown) return false
        return jokers.size == 2
    }

    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int): Int {
        if (gameState.isJoker(card, player)) return 1000

        if (GameEngine.isMaal(card, gameState.maalCard)) {
            return 800
        }

        val isDubliStrategy = isAimingForDubli(player, hand) || gameState.isDubliShow[player] == true
        val dubliScore = if (isPartOfIdenticalMatch(card, hand)) 500 else 0
        
        if (isDubliStrategy) {
            return dubliScore
        }
        
        val meldScore = calculateMeldPotential(card, hand, gameState, player)
        return maxOf(meldScore, dubliScore)
    }

    private fun calculateMeldPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int): Int {
        val hasShown = gameState.hasShown[player] == true

        val hasConsecutiveRun = isPartOfConsecutiveRun(card, hand, gameState, player)
        if (hasConsecutiveRun) return 150 // Increased to avoid discarding

        val hasIdenticalTriple = isPartOfIdenticalMatch(card, hand)
        if (hasIdenticalTriple) return 120 // Increased

        val hasGapRun = isPartOfGapRun(card, hand, gameState, player)
        if (hasGapRun) return 100 // Added explicit check here

        val sameRankOthers = hand.filter {
            !it.isSameInstance(card) &&
                    it.rank == card.rank &&
                    it.suit != card.suit &&
                    !gameState.isJoker(it, player)
        }

        val triplePotential = if (sameRankOthers.isNotEmpty()) {
            val uniqueSuitsCount = (sameRankOthers + card).distinctBy { it.suit }.size

            if (hasShown) {
                if (uniqueSuitsCount >= 3) 85 else 80
            } else {
                if (uniqueSuitsCount >= 3) 60 else 50
            }
        } else {
            0
        }

        return maxOf(if (hasGapRun) 100 else 0, triplePotential)
    }


    fun isPartOfConsecutiveRun(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && run {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 1 } }
            }
        }
    }

    fun isPartOfGapRun(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && run {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 2 } }
            }
        }
    }

    fun isPartOfIdenticalMatch(card: Card, hand: List<Card>): Boolean {
        if (card.rank == Rank.JOKER) {
            val others = hand.filter { !it.isSameInstance(card) && it.rank == Rank.JOKER }
            return others.isNotEmpty()
        }
        
        val others = hand.filter { !it.isSameInstance(card) && it.rank == card.rank && it.suit == card.suit }
        return others.isNotEmpty()
    }

    fun isPartOfPair(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = hand.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        return others.any { it.rank == card.rank }
    }

    fun findBestDiscardOptions(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val nonJokers = hand.filter { !gameState.isJoker(it, player) }
        if (nonJokers.isEmpty()) return listOf(hand.first())

        val meldedCards = findAllMeldedCards(hand, gameState, player)

        val scores = nonJokers.map { card ->
            // High score = keep
            if (meldedCards.any { it.isSameInstance(card) }) return@map card to 10000 
            
            val maalPoints = GameEngine.getMaalPoints(card, gameState.maalCard)
            if (maalPoints > 0) return@map card to (9000 + maalPoints)

            var score = calculateCardPotential(card, hand, gameState, player)
            if (card.rank == Rank.ACE || card.rank == Rank.KING) score -= 1
            
            card to score
        }

        val minScore = scores.minOf { it.second }
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): DiscardDecision {
        val bestCard = findBestDiscardOptions(hand, gameState, player).first()
        
        if (gameState.isJoker(bestCard, player) && gameState.isDubliShow[player] != true) {
            return DiscardDecision(bestCard, "Error: AI tried to discard a Joker.")
        }
        
        val hasShown = gameState.hasShown[player] == true
        
        val reason = when {
            GameEngine.isMaal(bestCard, gameState.maalCard) -> "Error: AI tried to discard Maal!"
            !hasShown && findAllInitialMelds(hand).isNotEmpty() -> 
                "Discarding ${bestCard.rank.symbol} to keep your 3 required melds intact for showing."
            isPartOfIdenticalMatch(bestCard, hand) -> 
                "Discarding ${bestCard.rank.symbol} from a pair because it's the safest option."
            isPartOfConsecutiveRun(bestCard, hand, gameState, player) -> 
                "Discarding ${bestCard.rank.symbol} despite being near a sequence (Emergency)."
            isPartOfGapRun(bestCard, hand, gameState, player) ->
                "Discarding ${bestCard.rank.symbol} as it's the weakest link in a potential sequence (Gap Run)."
            isPartOfPair(bestCard, hand, gameState, player) ->
                "Discarding ${bestCard.rank.symbol} because it's only a pair, and other cards have better connection potential."
            else -> "Discarding ${bestCard.rank.symbol} as it has the lowest strategic value."
        }
        
        return DiscardDecision(bestCard, reason)
    }

    fun shouldPickFromDiscard(card: Card, hand: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return true
        if (GameEngine.isMaal(card, gameState.maalCard)) return true

        val isDubliStrategy = isAimingForDubli(player, hand) || gameState.isDubliShow[player] == true
        
        if (isDubliStrategy) {
            return findDublis(hand + card).size > findDublis(hand).size
        }

        val hasShown = gameState.hasShown[player] == true
        
        for (i in 0 until hand.size - 1) {
            val c2 = hand[i]
            if (!isPotentiallyRelated(card, c2, gameState, player)) continue
            
            for (j in i + 1 until hand.size) {
                val c3 = hand[j]
                if (hasShown) {
                    if (isValidGeneralMeld(card, c2, c3, gameState, player)) return true
                } else {
                    if (isValidInitialMeld(card, c2, c3)) return true
                }
            }
        }
        
        return false
    }

    private fun isValidInitialMeld(c1: Card, c2: Card, c3: Card): Boolean {
        if (c1.rank == Rank.JOKER || c2.rank == Rank.JOKER || c3.rank == Rank.JOKER) return false
        
        // Identical Triple
        if (c1.rank == c2.rank && c1.suit == c2.suit && c2.rank == c3.rank && c2.suit == c3.suit) return true
        
        // Pure Run
        if (c1.suit == c2.suit && c2.suit == c3.suit) {
            val v1s = if (c1.rank == Rank.ACE) listOf(1, 14) else listOf(c1.rank.value)
            val v2s = if (c2.rank == Rank.ACE) listOf(1, 14) else listOf(c2.rank.value)
            val v3s = if (c3.rank == Rank.ACE) listOf(1, 14) else listOf(c3.rank.value)
            
            for (v1 in v1s) for (v2 in v2s) for (v3 in v3s) {
                val sorted = listOf(v1, v2, v3).sorted()
                if (sorted[0] + 1 == sorted[1] && sorted[1] + 1 == sorted[2]) return true
            }
        }
        return false
    }

    private fun findAnyMeldGreedyIndices(
        cards: List<Card>,
        used: BitSet,
        gameState: GameState,
        player: Int,
        ignoreShownStatus: Boolean
    ): List<Int>? {
        val indices = mutableListOf<Int>()
        for (i in cards.indices) if (!used.get(i)) indices.add(i)
        
        if (indices.size < 2) return null

        val jokerIndices = indices.filter { gameState.isJoker(cards[it], player) }
        if (jokerIndices.size >= 2 && (gameState.hasShown[player] == true || ignoreShownStatus)) {
            return jokerIndices.take(2)
        }

        if (indices.size < 3) return null

        for (i in indices.indices) {
            for (j in i + 1 until indices.size) {
                for (k in j + 1 until indices.size) {
                    val idx1 = indices[i]; val idx2 = indices[j]; val idx3 = indices[k]
                    if (isValidGeneralMeld(cards[idx1], cards[idx2], cards[idx3], gameState, player, ignoreShownStatus)) {
                        return listOf(idx1, idx2, idx3)
                    }
                }
            }
        }
        return null
    }

    private fun MutableList<Card>.removeByReference(toRemove: List<Card>) {
        for (card in toRemove) {
            val iterator = this.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().isSameInstance(card)) {
                    iterator.remove()
                    break
                }
            }
        }
    }
}
