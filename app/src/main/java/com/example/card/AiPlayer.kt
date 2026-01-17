package com.example.card

import kotlin.math.abs

data class DiscardDecision(val card: Card, val reason: String)

object AiPlayer {

    /**
     * Finds all cards that are currently part of any valid meld in the hand.
     * Takes into account whether the player has shown or not.
     */
    fun findAllMeldedCards(hand: List<Card>, gameState: GameState, player: Int): List<Card> {
        return findAllMelds(hand, gameState, player).flatten()
    }

    /**
     * Finds all non-overlapping melds currently in the hand.
     */
    private fun findAllMelds(hand: List<Card>, gameState: GameState, player: Int): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        val temp = hand.toMutableList()
        val hasShown = gameState.hasShown[player] == true
        
        var safety = 0
        while (safety < 8) { // Max melds possible in 21 cards
            safety++
            val m = if (hasShown) {
                findAnyMeldGreedy(temp, gameState, player)
            } else {
                // If not shown, only count melds valid for initial show
                findSingleInitialRun(temp) ?: 
                findSingleInitialTriple(temp) ?: 
                findThreeJokers(temp)
            } ?: break
            
            result.add(m)
            temp.removeByReference(m)
        }
        return result
    }

    private fun findThreeJokers(cards: List<Card>): List<Card>? {
        val jokers = cards.filter { it.rank == Rank.JOKER }
        return if (jokers.size >= 3) jokers.take(3) else null
    }

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
            return jokers.size == 2
        }

        // If there are jokers in a 3-card meld
        if (jokers.isNotEmpty()) {
            // Case A: 3 Jokers is always valid (if shown)
            if (nonJokers.isEmpty()) return true

            // Case B: Joker for a Triple (Same Rank, Different Suits)
            if (nonJokers.all { it.rank == nonJokers[0].rank }) {
                return nonJokers.distinctBy { it.suit }.size == nonJokers.size
            }

            // Case C: Joker for a Run (Same Suit, Sequential)
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
     */
    fun calculateCardPotential(card: Card, hand: List<Card>, gameState: GameState, player: Int): Int {
        if (gameState.isJoker(card, player)) return 1000

        val hasShown = gameState.hasShown[player] == true

        // 1. Regular/Consecutive Run
        val hasConsecutiveRun = isPartOfConsecutiveRun(card, hand, gameState, player)
        if (hasConsecutiveRun) return 100

        // 2. Identical Triple (Required for Initial Show)
        val hasIdenticalTriple = isPartOfIdenticalMatch(card, hand, gameState, player)
        if (hasIdenticalTriple) return 90

        // 3. Gap Run (Base value 70)
        val hasGapRun = isPartOfGapRun(card, hand, gameState, player)
        if (hasGapRun) return 80

        // 4. Standard Triple Logic (Different Suits)
        val sameRankOthers = hand.filter {
            !it.isSameInstance(card) &&
                    it.rank == card.rank &&
                    it.suit != card.suit &&
                    !gameState.isJoker(it, player)
        }

        val triplePotential = if (sameRankOthers.isNotEmpty()) {
            val uniqueSuitsCount = (sameRankOthers + card).distinctBy { it.suit }.size

            if (hasShown) {
                // AFTER SHOW: Higher than Gap Run (70), lower than Regular Run (100)
                if (uniqueSuitsCount >= 3) 85 else 80
            } else {
                // BEFORE SHOW: Lower than Gap Run (70)
                if (uniqueSuitsCount >= 3) 60 else 50
            }
        } else {
            0
        }

        val gapRunScore = if (hasGapRun) 70 else 0
        return maxOf(gapRunScore, triplePotential)
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

        val meldedCards = findAllMeldedCards(hand, gameState, player)

        val scores = nonJokers.map { card ->
            if (meldedCards.any { it.isSameInstance(card) }) return@map card to 10000 

            var score = calculateCardPotential(card, hand, gameState, player)
            if (card.rank == Rank.ACE || card.rank == Rank.KING) score -= 1
            
            card to score
        }

        val minScore = scores.minOf { it.second }
        return scores.filter { it.second == minScore }.map { it.first }
    }

    fun findCardToDiscard(hand: List<Card>, gameState: GameState, player: Int): DiscardDecision {
        val bestCard = findBestDiscardOptions(hand, gameState, player).first()
        
        if (gameState.isJoker(bestCard, player)) {
            return DiscardDecision(bestCard, "Error: AI tried to discard a Joker.")
        }
        
        val hasGapRun = isPartOfGapRun(bestCard, hand, gameState, player)
        val hasShown = gameState.hasShown[player] == true
        
        val reason = when {
            !hasShown && findAllInitialMelds(hand).isNotEmpty() -> 
                "Discarding ${bestCard.rank.symbol} to keep your 3 required melds intact for showing."
            hasGapRun -> 
                "Discarding ${bestCard.rank.symbol} as it's the weakest link in a potential sequence (Gap Run)."
            isPartOfPair(bestCard, hand, gameState, player) ->
                "Discarding ${bestCard.rank.symbol} because it's only a pair, and other cards have better connection potential."
            else -> "This card has the lowest connection value and doesn't help form any new melds."
        }
        
        return DiscardDecision(bestCard, reason)
    }
    fun shouldTakeCard(hand: List<Card>, cardToConsider: Card, gameState: GameState, player: Int): Boolean {
        if (gameState.isJoker(cardToConsider, player)) return true

        val existingMelds = findAllMelds(hand, gameState, player)
        // Identify cards already "locked" in runs
        val busyInRuns = existingMelds.filter { m ->
            val isRun = m.size >= 3 && m.all { it.suit == m[0].suit } &&
                    m.distinctBy { it.rank }.size == m.size &&
                    !m.any { gameState.isJoker(it, player) }
            isRun
        }.flatten()

        // 1. Identical Match Check (Same Rank, Same Suit)
        val identicalInHand = hand.find {
            it.rank == cardToConsider.rank &&
                    it.suit == cardToConsider.suit &&
                    !gameState.isJoker(it, player)
        }

        if (identicalInHand != null) {
            // RULE: If that card in hand is already in a Run, ignore the discard
            val isLockedInRun = busyInRuns.any { it.isSameInstance(identicalInHand) }
            if (isLockedInRun) return false

            // REDUCE CHANCE: Only take it if we already have 2 of them (making 3)
            // or if we really need it to show. Don't take it just to make a pair (2 cards).
            val countInHand = hand.count { it.rank == cardToConsider.rank && it.suit == cardToConsider.suit }
            if (countInHand < 2 && gameState.hasShown[player] != true) {
                // If it only makes a pair and we haven't shown,
                // it's often better to wait for a Run or a third card.
                return false
            }
            return true
        }

        // 2. Sequence/Run Logic
        val suitCards = hand.filter { it.suit == cardToConsider.suit && !gameState.isJoker(it, player) }
        val cVals = if (cardToConsider.rank == Rank.ACE) listOf(1, 14) else listOf(cardToConsider.rank.value)

        for (cv in cVals) {
            for (other in suitCards) {
                val isOtherLocked = busyInRuns.any { it.isSameInstance(other) }
                if (isOtherLocked) continue

                val ovs = if (other.rank == Rank.ACE) listOf(1, 14) else listOf(other.rank.value)
                for (ov in ovs) {
                    if (abs(cv - ov) <= 2) return true
                }
            }
        }

        // 3. Show Strategy
        if (gameState.hasShown[player] != true) {
            val before = findAllInitialMelds(hand).size
            val after = findAllInitialMelds(hand + cardToConsider).size
            if (after > before) return true
        }

        return false
    }
    /**
     * Determines if the player can perform an initial 'show' to view Maal.
     * Criteria: Must have 3 valid initial melds.
     * Hand status: Must have 12 cards remaining after setting aside 9 cards (3 melds).
     */
    fun canShowInitial(hand: List<Card>): Boolean {
        val melds = findAllInitialMelds(hand)
        if (melds.size >= 3) {
            val totalMeldedCards = melds.sumOf { it.size }
            val remainingCards = hand.size - totalMeldedCards
            return remainingCards >= 12
        }
        return false
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

    fun MutableList<Card>.removeByReference(cardsToRemove: List<Card>) {
        cardsToRemove.forEach { cardToRemove ->
            val iterator = this.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() === cardToRemove) {
                    iterator.remove()
                    break
                }
            }
        }
    }
}
