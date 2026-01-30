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

    /**
     * Strategic check: Only prioritize Dubli if we have 5+ pairs and very few regular melds.
     */
    fun isAimingForDubli(player: Int, hand: List<Card>, gameState: GameState): Boolean {
        if (gameState.hasShown[player] == true && gameState.isDubliShow[player] != true) return false
        if (gameState.isDubliShow[player] == true) return true

        val dublis = findDublis(hand)
        val initialMelds = findAllInitialMelds(hand)
        
        // Refined: Only aim for Dubli if 5+ pairs AND 1 or 0 melds found.
        return dublis.size >= 5 && initialMelds.size <= 1
    }

    fun findDublis(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.rank to it.suit }
            .map { it.value }
            .flatMap { group ->
                group.chunked(2).filter { it.size == 2 }
            }
    }

    fun findAllMeldedCards(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val dublis = findDublis(hand)
        val melds = findAllMelds(hand, gameState, player)
        
        val meldedCards = melds.flatten()
        val dubliCards = if (dublis.size >= 4) dublis.flatten() else emptyList()
        
        // Union of cards contributing to both potential systems.
        return (meldedCards + dubliCards).distinctBy { it.id }
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
        if (firstIdx >= allCards.size || depth > 20) return false 

        if (canDiscard) {
            used.set(firstIdx)
            if (findMeldsRecursive(allCards, used, gameState, player, meldCount, pureMeldCount, false, depth + 1)) return true
            used.clear(firstIdx)
        }

        if (allCards.size - used.cardinality() >= 3) {
            val first = allCards[firstIdx]
            for (i in firstIdx + 1 until allCards.size - 1) {
                if (used.get(i)) continue
                for (j in i + 1 until allCards.size) {
                    if (used.get(j)) continue
                    
                    if (isValidGeneralMeld(first, allCards[i], allCards[j], gameState, player, ignoreShownStatus = true)) {
                        val isPure = isValidInitialMeld(first, allCards[i], allCards[j])
                        used.set(firstIdx); used.set(i); used.set(j)
                        if (findMeldsRecursive(allCards, used, gameState, player, meldCount + 1, pureMeldCount + (if (isPure) 1 else 0), canDiscard, depth + 1)) return true
                        used.clear(j); used.clear(i); used.clear(firstIdx)
                    }
                }
            }
        }

        if (gameState.isJoker(allCards[firstIdx], player)) {
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
                if (nonJokers.size == 2 && nonJokers[0].rank == nonJokers[1].rank) return false
                if (nonJokers.size == 2) {
                    val vSets = nonJokers.map { c -> if (c.rank == Rank.ACE) listOf(1, 14) else listOf(c.rank.value) }
                    for (v1 in vSets[0]) for (v2 in vSets[1]) if (abs(v1 - v2) in 1..2) return true
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
        if (cards.all { it.rank == c1.rank } && cards.distinctBy { it.suit }.size == 3) return hasShown
        return false
    }

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
            if (!meldedIds.contains(card.id) && isPartOfConsecutiveRun(card, meldedCards, gameState, player)) extensionIds.add(card.id)
        }
        return HandAnalysis(meldedIds, consecutiveRunIds, gapRunIds, identicalMatchIds, pairIds, extensionIds)
    }

    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int, analysis: HandAnalysis): Int {
        if (gameState.isJoker(card, player)) return 1000
        if (GameEngine.isMaal(card, gameState.maalCard)) return 800
        
        val isIdentical = analysis.identicalMatchIds.contains(card.id)
        val dubliScore = if (isIdentical) 500 else 0
        
        val meldScore = calculateMeldPotential(card, hand, gameState, player, analysis)
        
        // Return the highest potential found. Lone cards will have 0 here.
        return maxOf(meldScore, dubliScore)
    }

    private fun calculateMeldPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int, analysis: HandAnalysis): Int {
        val hasShown = gameState.hasShown[player] == true
        val hasConsecutiveRun = analysis.consecutiveRunIds.contains(card.id)
        val hasGapRun = analysis.gapRunIds.contains(card.id)
        
        var suitScore = 0
        if (hasConsecutiveRun) suitScore = 150
        else if (hasGapRun) suitScore = 120

        val sameRankOthers = hand.filter { !it.isSameInstance(card) && it.rank == card.rank && it.suit != card.suit && !gameState.isJoker(it, player) }
        val triplePotential = if (sameRankOthers.isNotEmpty()) {
            val uniqueSuitsCount = (sameRankOthers + card).distinctBy { it.suit }.size
            if (hasShown) (if (uniqueSuitsCount >= 3) 85 else 80) else (if (uniqueSuitsCount >= 3) 60 else 50)
        } else 0
        return maxOf(suitScore, triplePotential)
    }

    fun isPartOfConsecutiveRun(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = othersList.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other -> other.suit == card.suit && run {
            val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
            cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 1 } }
        } }
    }

    fun isPartOfGapRun(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        val others = othersList.filter { !it.isSameInstance(card) && !gameState.isJoker(it, player) }
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.any { other -> other.suit == card.suit && run {
            val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
            cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 2 } }
        } }
    }

    fun isPartOfIdenticalMatch(card: Card, othersList: List<Card>): Boolean {
        if (card.rank == Rank.JOKER) return othersList.any { !it.isSameInstance(card) && it.rank == Rank.JOKER }
        return othersList.any { !it.isSameInstance(card) && it.rank == card.rank && it.suit == card.suit }
    }

    fun isPartOfPair(card: Card, othersList: List<Card>, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(card, player)) return false
        return othersList.any { !it.isSameInstance(card) && !gameState.isJoker(it, player) && it.rank == card.rank }
    }

    fun findBestDiscardOptions(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        val nonJokers = hand.filter { !gameState.isJoker(it, player) }
        if (nonJokers.isEmpty()) return listOf(hand.first())
        val analysis = analyzeHand(hand, gameState, player)
        val scores = nonJokers.map { card ->
            if (analysis.meldedIds.contains(card.id)) {
                card to 10000.0 
            } else {
                val potential = calculateCardPotential(card, hand, gameState, player, analysis)
                card to potential.toDouble()
            }
        }
        val minScore = scores.minOf { it.second }
        // Return ALL cards that have this minimum score (ties)
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): DiscardDecision {
        val options = findBestDiscardOptions(hand, gameState, player)
        // Pick one (prefer higher rank if tie)
        val bestCard = options.maxByOrNull { it.rank.value } ?: options.first()

        if (gameState.isJoker(bestCard, player) && gameState.isDubliShow[player] != true) return DiscardDecision(bestCard, "Error: AI tried to discard a Joker.")
        val hasShown = gameState.hasShown[player] == true
        val analysis = analyzeHand(hand, gameState, player)
        val reason = when {
            GameEngine.isMaal(bestCard, gameState.maalCard) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} (Maal)."
            !hasShown && findAllInitialMelds(hand).size >= 3 -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} to protect your melds."
            analysis.meldedIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} from a meld."
            analysis.identicalMatchIds.contains(bestCard.id) -> "Discarding one ${bestCard.rank.symbol}${bestCard.suit.symbol} from a pair."
            analysis.consecutiveRunIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} near a sequence."
            analysis.gapRunIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} (gap connection)."
            analysis.pairIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} (mixed-suit pair)."
            else -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} (worthless card)."
        }
        return DiscardDecision(bestCard, reason)
    }

    fun shouldPickFromDiscard(card: Card, hand: List<Card>, gameState: GameState, player: Int): DrawDecision {
        if (gameState.isJoker(card, player)) return DrawDecision(true, "Always pick a Joker!")
        if (GameEngine.isMaal(card, gameState.maalCard)) return DrawDecision(true, "Pick this Maal card!")
        
        val currentMelds = findAllMelds(hand, gameState, player)
        val isAlreadyMelded = currentMelds.any { meld -> meld.any { it.rank == card.rank && it.suit == card.suit } }

        if (hand.any { it.rank == card.rank && it.suit == card.suit }) {
            val countInHand = hand.count { it.rank == card.rank && it.suit == card.suit }
            if (countInHand >= 3 && !isAimingForDubli(player, hand, gameState)) return DrawDecision(false, "Already have a Tunnela.")
        }

        if (isAimingForDubli(player, hand, gameState)) {
            val formsPair = isPartOfIdenticalMatch(card, hand)
            if (formsPair) return DrawDecision(true, "Take this! It forms an identical pair (Dubli).")
        }

        val hasShown = gameState.hasShown[player] == true
        for (i in 0 until hand.size - 1) {
            val c2 = hand[i]
            if (!isPotentiallyRelated(card, c2, gameState, player)) continue
            for (j in i + 1 until hand.size) {
                val c3 = hand[j]
                val areAlreadyMeldedTogether = currentMelds.any { meld -> 
                    meld.contains(c2) && meld.contains(c3) && meld.any { it.rank == card.rank && it.suit == card.suit }
                }
                if (areAlreadyMeldedTogether) continue

                if (hasShown) { 
                    if (isValidGeneralMeld(card, c2, c3, gameState, player)) return DrawDecision(true, "Take it! Completes a meld.") 
                } else { 
                    if (isValidInitialMeld(card, c2, c3)) return DrawDecision(true, "Take it! Forms a pure meld.") 
                }
            }
        }

        if (isAlreadyMelded && !isAimingForDubli(player, hand, gameState)) return DrawDecision(false, "Already in a meld.")
        if (isPartOfConsecutiveRun(card, hand, gameState, player)) return DrawDecision(true, "Strong sequence connection.")
        return DrawDecision(false, "Draw from stock to find cards for your required melds.")
    }

    private fun findAnyMeldGreedyIndices(cards: List<Card>, used: BitSet, gameState: GameState, player: Int, ignoreShownStatus: Boolean): List<Int>? {
        val indices = mutableListOf<Int>()
        for (i in cards.indices) if (!used.get(i)) indices.add(i)
        if (indices.size < 2) return null
        val jokerIndices = indices.filter { gameState.isJoker(cards[it], player) }
        if (jokerIndices.size >= 2 && (gameState.hasShown[player] == true || ignoreShownStatus)) return jokerIndices.take(2)
        if (indices.size < 3) return null
        for (i in indices.indices) {
            for (j in i + 1 until indices.size) {
                for (k in j + 1 until indices.size) {
                    if (isValidGeneralMeld(cards[indices[i]], cards[indices[j]], cards[indices[k]], gameState, player, ignoreShownStatus)) return listOf(indices[i], indices[j], indices[k])
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
