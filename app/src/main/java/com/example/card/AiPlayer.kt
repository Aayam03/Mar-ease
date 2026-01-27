package com.example.card

import java.util.BitSet
import kotlin.math.abs

data class DiscardDecision(val card: Card, val reason: String)
data class DrawDecision(val shouldPick: Boolean, val reason: String)

/**
 * Pre-calculated analysis of a hand to avoid O(N^2) checks inside loops.
 */
data class HandAnalysis(
    val meldedIds: Set<String>,
    val consecutiveRunIds: Set<String>,
    val gapRunIds: Set<String>,
    val identicalMatchIds: Set<String>,
    val pairIds: Set<String>,
    val extensionIds: Set<String>
)

object AiPlayer {

    fun isAimingForDubli(player: Int, hand: List<Card>, gameState: GameState): Boolean {
        // Dubli is not possible after any form of show, except for Dubli show.
        if (gameState.hasShown[player] == true && gameState.isDubliShow[player] != true) return false
        
        // If already in Dubli show state, definitely aiming for Dubli.
        if (gameState.isDubliShow[player] == true) return true

        val dublis = findDublis(hand)
        val initialMelds = findAllInitialMelds(hand)
        
        // Smarter Dubli Logic:
        // 1. If we have 5 or more pairs, prioritize Dubli regardless of other melds.
        // 2. If we have 4 pairs AND 1 or fewer initial melds, prioritize Dubli.
        // 3. Otherwise, stick to regular melds.
        return when {
            dublis.size >= 5 -> true
            dublis.size >= 4 && initialMelds.size <= 1 -> true
            else -> false
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
        
        if (isAimingForDubli(player, hand, gameState)) {
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
            val mIndices = findAnyMeldGreedyIndices(hand, used, gameState, player, ignoreShownStatus = false) ?: break
            val m = mIndices.map { hand[it] }
            result.add(m)
            mIndices.forEach { used.set(it) }
        }
        return result
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

        return if (result.size >= 3) result.take(3) else result
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
        
        if (isAimingForDubli(player, totalCards, gameState)) return false

        val used = BitSet(totalCards.size)
        return when (totalCards.size) {
            22 -> findMeldsRecursive(totalCards, used, gameState, player, 0, 0, canDiscard = true, 0)
            21 -> findMeldsRecursive(totalCards, used, gameState, player, 0, 0, canDiscard = false, 0)
            else -> false
        }
    }

    private fun findMeldsRecursive(
        allCards: List<Card>,
        used: BitSet,
        gameState: GameState,
        player: Int, 
        meldCount: Int, 
        pureMeldCount: Int,
        canDiscard: Boolean,
        depth: Int
    ): Boolean {
        if (meldCount == 7) {
            return gameState.hasShown[player] == true || pureMeldCount >= 3
        }
        
        val firstIdx = used.nextClearBit(0)
        if (firstIdx >= allCards.size || depth > 80) return false 

        if (canDiscard) {
            used.set(firstIdx)
            if (findMeldsRecursive(allCards, used, gameState, player, meldCount, pureMeldCount, false, depth + 1)) return true
            used.clear(firstIdx)
        }

        val first = allCards[firstIdx]

        if (allCards.size - used.cardinality() >= 3) {
            for (i in firstIdx + 1 until allCards.size - 1) {
                if (used.get(i)) continue
                for (j in i + 1 until allCards.size) {
                    if (used.get(j)) continue
                    
                    val second = allCards[i]
                    val third = allCards[j]

                    if (isValidGeneralMeld(first, second, third, gameState, player, ignoreShownStatus = true)) {
                        val isPure = isValidInitialMeld(first, second, third)
                        used.set(firstIdx); used.set(i); used.set(j)
                        if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, pureMeldCount + (if (isPure) 1 else 0), canDiscard, depth + 1)) return true
                        used.clear(j); used.clear(i); used.clear(firstIdx)
                    }
                }
            }
        }

        if (gameState.isJoker(first, player)) {
            for (i in firstIdx + 1 until allCards.size) {
                if (!used.get(i) && gameState.isJoker(allCards[i], player)) {
                    used.set(firstIdx); used.set(i)
                    if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, pureMeldCount, canDiscard, depth + 1)) return true
                    used.clear(i); used.clear(firstIdx)
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

        if (cards.all { it.rank == c1.rank } && cards.distinctBy { it.suit }.size == 3) {
            return hasShown
        }

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

    internal fun isValidInitialMeld(c1: Card, c2: Card, c3: Card): Boolean {
        if (c1.rank == Rank.JOKER || c2.rank == Rank.JOKER || c3.rank == Rank.JOKER) return false
        
        if (c1.rank == c2.rank && c1.suit == c2.suit && c2.rank == c3.rank && c2.suit == c3.suit) return true
        
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

    private fun analyzeHand(hand: List<Card>, gameState: GameState, player: Int): HandAnalysis {
        val meldedCards = findAllMeldedCards(hand, gameState, player)
        val meldedIds = meldedCards.map { it.id }.toSet()
        
        val consecutiveRunIds = mutableSetOf<String>()
        val gapRunIds = mutableSetOf<String>()
        val identicalMatchIds = mutableSetOf<String>()
        val pairIds = mutableSetOf<String>()
        val extensionIds = mutableSetOf<String>()

        for (card in hand) {
            if (isPartOfConsecutiveRun(card, hand, gameState, player)) consecutiveRunIds.add(card.id)
            if (isPartOfGapRun(card, hand, gameState, player)) gapRunIds.add(card.id)
            if (isPartOfIdenticalMatch(card, hand)) identicalMatchIds.add(card.id)
            if (isPartOfPair(card, hand, gameState, player)) pairIds.add(card.id)
            
            if (!meldedIds.contains(card.id) && isPartOfConsecutiveRun(card, meldedCards, gameState, player)) {
                extensionIds.add(card.id)
            }
        }

        return HandAnalysis(meldedIds, consecutiveRunIds, gapRunIds, identicalMatchIds, pairIds, extensionIds)
    }

    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int, analysis: HandAnalysis): Int {
        if (gameState.isJoker(card, player)) return 1000

        if (GameEngine.isMaal(card, gameState.maalCard)) {
            return 800
        }

        val isDubliStrategy = isAimingForDubli(player, hand, gameState)
        val isIdentical = analysis.identicalMatchIds.contains(card.id)
        val dubliScore = if (isIdentical) 500 else 0
        
        if (isDubliStrategy) {
            return dubliScore
        }
        
        val meldScore = calculateMeldPotential(card, hand, gameState, player, analysis)
        return maxOf(meldScore, dubliScore)
    }

    private fun calculateMeldPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int, analysis: HandAnalysis): Int {
        val hasShown = gameState.hasShown[player] == true

        val hasConsecutiveRun = analysis.consecutiveRunIds.contains(card.id)
        val hasIdenticalTriple = analysis.identicalMatchIds.contains(card.id)
        val hasGapRun = analysis.gapRunIds.contains(card.id)

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

        var score = 0
        if (hasConsecutiveRun) score += 150
        if (hasIdenticalTriple) score += 100
        if (hasGapRun) score += 120
        
        return maxOf(score, triplePotential)
    }

    fun isPartOfConsecutiveRun(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = othersList.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && run {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 1 } }
            }
        }
    }

    fun isPartOfGapRun(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = othersList.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other ->
            other.suit == card.suit && run {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 2 } }
            }
        }
    }

    fun isPartOfIdenticalMatch(card: Card, othersList: List<Card>): Boolean {
        if (card.rank == Rank.JOKER) {
            val others = othersList.filter { !it.isSameInstance(card) && it.rank == Rank.JOKER }
            return others.isNotEmpty()
        }
        
        val others = othersList.filter { !it.isSameInstance(card) && it.rank == card.rank && it.suit == card.suit }
        return others.isNotEmpty()
    }

    fun isPartOfPair(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = othersList.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        return others.any { it.rank == card.rank }
    }

    fun findBestDiscardOptions(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val nonJokers = hand.filter { !gameState.isJoker(it, player) }
        if (nonJokers.isEmpty()) return listOf(hand.first())

        val analysis = analyzeHand(hand, gameState, player)

        val scores = nonJokers.asSequence().map { card ->
            if (analysis.meldedIds.contains(card.id)) return@map card to 10000 
            
            val maalPoints = GameEngine.getMaalPoints(card, gameState.maalCard)
            if (maalPoints > 0) {
                val isPair = analysis.identicalMatchIds.contains(card.id)
                val boost = if (isPair) 500 else 0
                return@map card to (9000 + maalPoints + boost)
            }

            var score = calculateCardPotential(card, hand, gameState, player, analysis)

            if (analysis.extensionIds.contains(card.id)) {
                score = 200 
            }

            if (card.rank == Rank.ACE || card.rank == Rank.KING) score -= 1
            
            card to score
        }.toList()

        val minScore = scores.minOf { it.second }
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): DiscardDecision {
        val options = findBestDiscardOptions(hand, gameState, player)
        val bestCard = options.first()
        
        if (gameState.isJoker(bestCard, player) && gameState.isDubliShow[player] != true) {
            return DiscardDecision(bestCard, "Error: AI tried to discard a Joker.")
        }
        
        val hasShown = gameState.hasShown[player] == true
        val analysis = analyzeHand(hand, gameState, player)
        
        val reason = when {
            GameEngine.isMaal(bestCard, gameState.maalCard) -> 
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} (Maal). This is unusual but happens if every card is valuable."
            !hasShown && findAllInitialMelds(hand).size >= 3 -> 
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} to protect your 3 required pure melds for showing."
            analysis.meldedIds.contains(bestCard.id) ->
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} from a meld because every other card in your hand is even more critical (e.g., Maal or better melds)."
            analysis.identicalMatchIds.contains(bestCard.id) -> 
                "Discarding one ${bestCard.rank.symbol}${bestCard.suit.symbol} from a pair. Holding identical cards is great for Dubli or Tipli, but this one is the least likely to complete a set right now."
            analysis.consecutiveRunIds.contains(bestCard.id) -> 
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} even though it's near a sequence. This is a strategic 'sacrifice' to prioritize higher-value cards or guaranteed melds."
            analysis.gapRunIds.contains(bestCard.id) ->
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol}. It was a 'gap' connection (e.g., 5 and 7 waiting for 6), which is harder to complete than a consecutive one."
            analysis.pairIds.contains(bestCard.id) ->
                "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} because it's only a mixed-suit pair, which can't be used for the initial show."
            else -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} as it has no useful connections with your other cards and the lowest strategic value."
        }
        
        return DiscardDecision(bestCard, reason)
    }

    fun shouldPickFromDiscard(card: Card, hand: List<Card>, gameState: GameState, player: Int): DrawDecision {
        if (gameState.isJoker(card, player)) return DrawDecision(true, "Always pick a Joker! It can substitute for any card.")
        if (GameEngine.isMaal(card, gameState.maalCard)) return DrawDecision(true, "Pick this Maal card! It gives you immediate points and acts as a Joker.")

        // Check if the card is already in hand (redundant pick)
        if (hand.any { it.rank == card.rank && it.suit == card.suit }) {
            // Note: In 21-card, identical cards are allowed, but picking a 4th identical card 
            // when you already have a Tunnela (3 identical) is often not optimal unless for Dubli.
            val countInHand = hand.count { it.rank == card.rank && it.suit == card.suit }
            if (countInHand >= 3 && !isAimingForDubli(player, hand, gameState)) {
                return DrawDecision(false, "You already have a Tunnela of ${card.rank.symbol}${card.suit.symbol}. Drawing another is usually redundant.")
            }
        }

        val isDubliStrategy = isAimingForDubli(player, hand, gameState)
        
        if (isDubliStrategy) {
            val formsPair = isPartOfIdenticalMatch(card, hand)
            return if (formsPair) DrawDecision(true, "Take this! It forms an identical pair (Dubli), getting you closer to a Dubli show.")
                   else DrawDecision(false, "Draw from stock. This discard doesn't form a pair for your Dubli strategy.")
        }

        val hasShown = gameState.hasShown[player] == true
        
        for (i in 0 until hand.size - 1) {
            val c2 = hand[i]
            if (!isPotentiallyRelated(card, c2, gameState, player)) continue
            
            for (j in i + 1 until hand.size) {
                val c3 = hand[j]
                if (hasShown) {
                    if (isValidGeneralMeld(card, c2, c3, gameState, player)) {
                        return DrawDecision(true, "Take it! It completes a meld with ${c2.rank.symbol}${c2.suit.symbol} and ${c3.rank.symbol}${c3.suit.symbol}.")
                    }
                } else {
                    if (isValidInitialMeld(card, c2, c3)) {
                        val type = if (card.rank == c2.rank) "Tunnela (Identical Triple)" else "Pure Run"
                        return DrawDecision(true, "Pick this! It forms a $type with ${c2.rank.symbol}${c2.suit.symbol} and ${c3.rank.symbol}${c3.suit.symbol}, which is required for showing.")
                    }
                }
            }
        }
        
        // Check if picking this card creates a NEW meld that we don't already have.
        // If it just replicates a card in an existing meld (like the user's 4Heart case), we should decline.
        val meldedCards = findAllMeldedCards(hand, gameState, player)
        if (meldedCards.any { it.rank == card.rank && it.suit == card.suit }) {
            return DrawDecision(false, "You already have ${card.rank.symbol}${card.suit.symbol} in a meld. Drawing a duplicate usually doesn't help unless you're going for Dubli.")
        }

        // Strong connection check (near-meld)
        if (isPartOfConsecutiveRun(card, hand, gameState, player)) {
            return DrawDecision(true, "Pick this. It creates a consecutive sequence, making it very likely you'll form a run on a future turn.")
        }

        return DrawDecision(false, "Draw from stock. This discard doesn't help you form a pure meld or a strong connection.")
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
