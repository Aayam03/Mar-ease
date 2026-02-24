package com.example.card

import java.util.BitSet
import kotlin.math.abs
import kotlin.random.Random

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
     * Strategic check: Only prioritize Dubli if we have a very strong start for it.
     */
    fun isAimingForDubli(player: Int, hand: List<Card>, gameState: GameState): Boolean {
        if (gameState.hasShown[player] == true && gameState.isDubliShow[player] != true) return false
        if (gameState.isDubliShow[player] == true) return true

        val dublis = findDublis(hand)
        val initialMelds = findAllInitialMelds(hand)
        
        // Increased threshold: Need 6+ pairs AND absolutely no regular melds to commit.
        return dublis.size >= 6 && initialMelds.isEmpty()
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
        // Identical triples only (same rank and suit) for pure show
        val identicalGroup = nonJokers.groupBy { it.rank to it.suit }.values.firstOrNull { it.size >= 3 }
        return identicalGroup?.take(3)
    }

    fun canFinish(hand: List<Card>, gameState: GameState, player: Int): Boolean {
        // Defensive copy to avoid ConcurrentModificationException from UI interactions
        val shown = gameState.shownCards[player]?.toList() ?: emptyList()
        val totalCards = shown + hand

        // A winning hand must consist of exactly 21 cards formed into 7 melds.
        // If a player holds 22, they must be able to discard one to win.
        if (totalCards.size !in 21..22) return false

        // 1. Dubli Mode Win (8 pairs always wins)
        val pairs = findDublis(totalCards)
        if (pairs.size >= 8) return true
        if (gameState.isDubliShow[player] == true) return false

        // 2. Standard Win (Triplets/Melds)
        val jokers = totalCards.filter { gameState.isJoker(it, player) }
        val normalCards = totalCards.filter { !gameState.isJoker(it, player) }

        return if (totalCards.size == 22) {
            // Player has an extra card. See if discarding any non-joker leads to a win.
            val canWinByDiscardingNormal = normalCards.indices.any { i ->
                val remainingHand = normalCards.filterIndexed { idx, _ -> idx != i }
                canMeldAll(remainingHand, jokers.size, gameState, player)
            }
            if (canWinByDiscardingNormal) return true

            // If no normal card discard works, try discarding a joker (if any).
            if (jokers.isNotEmpty()) {
                canMeldAll(normalCards, jokers.size - 1, gameState, player)
            } else {
                false
            }
        } else { // Hand size is exactly 21
            canMeldAll(normalCards, jokers.size, gameState, player)
        }
    }

    /**
     * A more robust, non-recursive function to check if a hand can be fully melded.
     * It counts required jokers for incomplete groups.
     */
    private fun canMeldAll(cards: List<Card>, jokerCount: Int, gameState: GameState, player: Int): Boolean {
        val remainingCards = cards.toMutableList()
        
        // Step 1: Find and remove all "pure" melds (3 cards without jokers).
        var pureMeldCount = 0
        while (true) {
            findAndRemovePureMeld(remainingCards) ?: break
            pureMeldCount++
        }

        // Standard rules: If not yet shown, must have at least 3 pure melds (sequences or Tunnelas)
        if (gameState.hasShown[player] != true && pureMeldCount < 3) return false

        if (remainingCards.isEmpty()) {
            // All cards were part of pure melds, any remaining jokers must form melds of 3.
            return jokerCount % 3 == 0
        }

        // Step 2: Group remaining cards by rank and suit to find incomplete melds.
        val rankGroups = remainingCards.groupBy { it.rank }
        val suitGroups = remainingCards.groupBy { it.suit }

        val accountedFor = mutableSetOf<String>()
        var jokersNeeded = 0

        // Step 3: Check for incomplete sets (2 cards of same rank).
        rankGroups.values.forEach { group ->
            if (group.size == 2) {
                // Check for distinct suits, as two identical cards cannot be bridged by a joker.
                if (group[0].suit != group[1].suit && group.all { it.id !in accountedFor }) {
                    jokersNeeded++
                    accountedFor.addAll(group.map { it.id })
                }
            }
        }
        
        // Step 4: Check for incomplete runs (2 cards of same suit, close in rank).
        suitGroups.values.forEach { group ->
            val ungrouped = group.filter { it.id !in accountedFor }
            if (ungrouped.size == 2) {
                val c1 = ungrouped[0]
                val c2 = ungrouped[1]
                // `canFormMeldWithOneJoker` checks if two cards are a valid pair for bridging.
                if (canFormMeldWithOneJoker(c1, c2)) {
                    jokersNeeded++
                    accountedFor.addAll(ungrouped.map { it.id })
                }
            }
        }
        
        val unaccountedCards = remainingCards.count { it.id !in accountedFor }
        
        // Each remaining card that couldn't be paired up needs 2 jokers to form a meld.
        jokersNeeded += (unaccountedCards * 2)

        return jokerCount >= jokersNeeded
    }

    /**
     * Helper to find one pure meld (run or Tunnela) and remove it from the list.
     * Returns the meld found, or null if none.
     */
    private fun findAndRemovePureMeld(cards: MutableList<Card>): List<Card>? {
        if (cards.size < 3) return null
        
        for (i in 0 until cards.size) {
            for (j in i + 1 until cards.size) {
                for (k in j + 1 until cards.size) {
                    val c1 = cards[i]
                    val c2 = cards[j]
                    val c3 = cards[k]
                    if (isValidInitialMeld(c1, c2, c3)) {
                        val meld = listOf(c1, c2, c3)
                        // Removing items by index in descending order to avoid shift issues
                        cards.removeAt(k)
                        cards.removeAt(j)
                        cards.removeAt(i)
                        return meld
                    }
                }
            }
        }
        return null
    }

    private fun canFormMeldWithOneJoker(c1: Card, c2: Card): Boolean {
        // Joker bridge logic for sequences and sets
        if (c1.rank == c2.rank && c1.suit != c2.suit) return true
        
        if (c1.suit == c2.suit) {
            val v1s = if (c1.rank == Rank.ACE) listOf(1, 14) else listOf(c1.rank.value)
            val v2s = if (c2.rank == Rank.ACE) listOf(1, 14) else listOf(c2.rank.value)
            for (v1 in v1s) {
                for (v2 in v2s) {
                    // Sequence with 1 card gap or adjacent
                    if (abs(v1 - v2) in 1..2) return true
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
                // Sets (Trials) must have distinct suits. 
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

    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int, analysis: HandAnalysis, isAimingForDubli: Boolean = false): Int {
        if (gameState.isJoker(card, player)) return 1000
        if (GameEngine.isMaal(card, gameState.maalCard)) return 800
        
        val isIdentical = analysis.identicalMatchIds.contains(card.id)
        val meldScore = calculateMeldPotential(card, hand, gameState, player, analysis)
        
        val pairsCount = hand.groupBy { it.rank to it.suit }.filter { it.value.size >= 2 }.size
        
        var pairScore = if (isIdentical) {
            val base = if (isAimingForDubli) 500 else 0
            base + when {
                pairsCount >= 7 -> 750 
                pairsCount >= 5 -> 450 
                pairsCount >= 3 -> 250 
                else -> 80 
            }
        } else 0

        if (gameState.hasShown[player] == true && !isAimingForDubli) {
            val identicalCount = hand.count { it.rank == card.rank && it.suit == card.suit }
            if (identicalCount >= 2) {
                pairScore = 10 
            }
        }
        
        return meldScore + pairScore
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
            if (hasShown) (if (uniqueSuitsCount >= 3) 85 else 80) else 0
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

    fun isDead(card: Card, gameState: GameState): Boolean {
        if (card.rank == Rank.JOKER) return false
        val numberOfDecks = when (gameState.playerCount) {
            in 2..4 -> 3
            5 -> 4
            else -> 5
        }
        val discarded = gameState.discardPile.toList()
        val shown = gameState.shownCards.toMap().values.flatMap { it.toList() }

        val seenCount = discarded.count { it.rank == card.rank && it.suit == card.suit } +
                        shown.count { it.rank == card.rank && it.suit == card.suit }
        return seenCount >= numberOfDecks - 1
    }

    fun findBestDiscardOptions(hand: List<Card>, gameState: GameState, player: Int, isAimingForDubli: Boolean = false): List<Card> {
        val nonJokers = hand.filter { !gameState.isJoker(it, player) }
        if (nonJokers.isEmpty()) return listOf(hand.first())
        val analysis = analyzeHand(hand, gameState, player)
        val scores = nonJokers.map { card ->
            if (analysis.meldedIds.contains(card.id)) {
                card to 10000.0 
            } else {
                var potential = calculateCardPotential(card, hand, gameState, player, analysis, isAimingForDubli).toDouble()
                if (isDead(card, gameState)) {
                    potential -= 50.0 
                }
                card to potential
            }
        }
        val minScore = scores.minOf { it.second }
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): DiscardDecision {
        val aimingForDubli = isAimingForDubli(player, hand, gameState)
        val options = findBestDiscardOptions(hand, gameState, player, aimingForDubli)
        
        val bestCard = when (gameState.difficulty) {
            Difficulty.EASY -> {
                if (Random.nextFloat() < 0.5f) hand.filter { !gameState.isJoker(it, player) }.randomOrNull() ?: options.first()
                else options.random()
            }
            Difficulty.MEDIUM -> {
                options.random()
            }
            Difficulty.HARD -> {
                options.maxByOrNull { it.rank.value } ?: options.first()
            }
        }
        
        if (gameState.isJoker(bestCard, player) && gameState.isDubliShow[player] != true) return DiscardDecision(bestCard, "Error: AI tried to discard a Joker.")
        val hasShown = gameState.hasShown[player] == true
        val analysis = analyzeHand(hand, gameState, player)
        val isDeadCard = isDead(bestCard, gameState)
        val reason = when {
            isDeadCard -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} because it is 'dead' (all other copies are already played)."
            GameEngine.isMaal(bestCard, gameState.maalCard) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} because it's a Maal card that doesn't fit into your hand."
            !hasShown && findAllInitialMelds(hand).size >= 3 -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} to keep your ready-to-show sequences intact."
            analysis.meldedIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} from an existing combination to try for something better."
            analysis.identicalMatchIds.contains(bestCard.id) -> "Discarding one ${bestCard.rank.symbol}${bestCard.suit.symbol} from a pair."
            analysis.consecutiveRunIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} even though it's near another card, as other options were even less useful."
            analysis.gapRunIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} because it only has a 'gap' connection to your hand."
            analysis.pairIds.contains(bestCard.id) -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} from a mixed-suit pair."
            else -> "Discarding ${bestCard.rank.symbol}${bestCard.suit.symbol} because it doesn't connect with any other cards in your hand."
        }
        return DiscardDecision(bestCard, reason)
    }

    fun shouldPickFromDiscard(card: Card, hand: List<Card>, gameState: GameState, player: Int): DrawDecision {
        if (gameState.isJoker(card, player)) return DrawDecision(true, "Pick this up! It's a Joker, which can act as any card.")
        if (GameEngine.isMaal(card, gameState.maalCard)) return DrawDecision(true, "Pick this up! It's a Maal card and will give you points.")
        
        val hypotheticalHand = hand + card
        val discardSimulation = findCardToDiscard(hypotheticalHand, gameState, player)
        if (discardSimulation.card.isSameInstance(card)) {
            return DrawDecision(false, "Don't pick this up; drawing from the stock pile is likely better.")
        }

        if (gameState.difficulty == Difficulty.EASY && Random.nextFloat() < 0.6f) {
            return DrawDecision(false, "Easy AI missed an opportunity.")
        }
        if (gameState.difficulty == Difficulty.MEDIUM && Random.nextFloat() < 0.2f) {
            return DrawDecision(false, "Medium AI missed an opportunity.")
        }

        val currentMelds = findAllMelds(hand, gameState, player)
        val isAlreadyMelded = currentMelds.any { meld -> meld.any { it.rank == card.rank && it.suit == card.suit } }

        if (hand.any { it.rank == card.rank && it.suit == card.suit }) {
            val countInHand = hand.count { it.rank == card.rank && it.suit == card.suit }
            if (countInHand >= 3 && !isAimingForDubli(player, hand, gameState)) return DrawDecision(false, "You already have three of these cards (a Tunnela).")
        }

        if (isAimingForDubli(player, hand, gameState)) {
            val formsPair = isPartOfIdenticalMatch(card, hand)
            if (formsPair) return DrawDecision(true, "Pick this up! It forms an identical pair for your Dubli strategy.")
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
                    if (isValidGeneralMeld(card, c2, c3, gameState, player)) return DrawDecision(true, "Pick this up! It completes a combination (sequence or set) in your hand.") 
                } else { 
                    if (isValidInitialMeld(card, c2, c3)) return DrawDecision(true, "Pick this up! It forms a pure sequence or set needed for your initial show.") 
                }
            }
        }

        if (isAlreadyMelded && !isAimingForDubli(player, hand, gameState)) return DrawDecision(false, "You already have a combination using this card.")
        
        val connectingCard = findConnectingCard(card, hand)
        if (connectingCard != null) {
            return DrawDecision(true, "Pick this up! It connects directly to your ${connectingCard.rank.symbol}${connectingCard.suit.symbol} to form a potential sequence.")
        }
        
        return DrawDecision(false, "This card doesn't complete any combinations; drawing from the stock pile is likely better.")
    }
    
    private fun findConnectingCard(card: Card, others: List<Card>): Card? {
        val cVals = if (card.rank == Rank.ACE) listOf(1, 14) else listOf(card.rank.value)
        return others.find { other ->
            other.suit == card.suit && run {
                val oVals = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                cVals.any { cv -> oVals.any { ov -> abs(cv - ov) == 1 } }
            }
        }
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
