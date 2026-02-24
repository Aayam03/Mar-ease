package com.example.card

import kotlin.math.abs

object MeldEvaluator {

    fun canWin(hand: List<Card>, isJoker: (Card) -> Boolean): Boolean {
        val naturalCards = hand.filter { !isJoker(it) }.toMutableList()
        val jokerCount = hand.count(isJoker)

        // Find and remove all "pure" melds (3 cards without jokers).
        var pureMeldsRemoved: List<Card>?
        do {
            pureMeldsRemoved = findAndRemovePureMeld(naturalCards)
        } while (pureMeldsRemoved != null)

        if (naturalCards.isEmpty()) {
            return jokerCount % 3 == 0
        }

        val jokersNeeded = calculateMinimumJokersNeeded(naturalCards)
        return jokerCount >= jokersNeeded && (jokerCount - jokersNeeded) % 3 == 0
    }

    private fun calculateMinimumJokersNeeded(cards: List<Card>): Int {
        val accountedFor = mutableSetOf<String>()
        var jokersNeeded = 0

        // Step 1: Check for incomplete sets (2 cards of same rank).
        val rankGroups = cards.groupBy { it.rank }
        rankGroups.values.forEach { group ->
            if (group.size == 2 && group.all { it.id !in accountedFor }) {
                if (group[0].suit != group[1].suit) {
                    jokersNeeded++
                    accountedFor.addAll(group.map { it.id })
                }
            }
        }
        
        // Step 2: Check for incomplete runs (2 cards of same suit, close in rank).
        val suitGroups = cards.groupBy { it.suit }
        suitGroups.values.forEach { group ->
            val ungrouped = group.filter { it.id !in accountedFor }.sortedBy { it.rank.value }
            if (ungrouped.size >= 2) {
                 for (i in 0 until ungrouped.size - 1) {
                    val c1 = ungrouped[i]
                    if (c1.id in accountedFor) continue
                    for (j in i + 1 until ungrouped.size) {
                         val c2 = ungrouped[j]
                         if (c2.id in accountedFor) continue
                        if (canFormMeldWithOneJoker(c1, c2)) {
                            jokersNeeded++
                            accountedFor.add(c1.id)
                            accountedFor.add(c2.id)
                            break
                        }
                    }
                }
            }
        }
        
        val unaccountedCards = cards.count { it.id !in accountedFor }
        
        jokersNeeded += (unaccountedCards * 2)

        return jokersNeeded
    }

    private fun findAndRemovePureMeld(cards: MutableList<Card>): List<Card>? {
        if (cards.size < 3) return null
        
        for (i in 0 until cards.size) {
            for (j in i + 1 until cards.size) {
                for (k in j + 1 until cards.size) {
                    if (i >= cards.size || j >= cards.size || k >= cards.size) continue
                    
                    val c1 = cards[i]
                    val c2 = cards[j]
                    val c3 = cards[k]
                    
                    if (isValidInitialMeld(c1, c2, c3)) {
                        cards.removeAt(k)
                        cards.removeAt(j)
                        cards.removeAt(i)
                        return listOf(c1, c2, c3)
                    }
                }
            }
        }
        return null
    }

    private fun isValidInitialMeld(c1: Card, c2: Card, c3: Card): Boolean {
        if (c1.rank == Rank.JOKER || c2.rank == Rank.JOKER || c3.rank == Rank.JOKER) return false

        if (c1.rank == c2.rank && c1.suit == c2.suit && c2.rank == c3.rank && c2.suit == c3.suit) return true
        
        if (c1.suit == c2.suit && c2.suit == c3.suit) {
            val ranks = listOf(c1, c2, c3).map { it.rank.value }.distinct().sorted()
            if (ranks.size == 3 && ranks[0] + 1 == ranks[1] && ranks[1] + 1 == ranks[2]) {
                return true
            }
             if (ranks.contains(Rank.ACE.value)) {
                 val nonAces = ranks.filter { it != Rank.ACE.value }
                 if (nonAces.size == 2) {
                     if ((nonAces[0] == 2 && nonAces[1] == 3) || (nonAces[0] == 12 && nonAces[1] == 13)) return true
                 }
             }
        }
        return false
    }

    private fun canFormMeldWithOneJoker(c1: Card, c2: Card): Boolean {
        if (c1.suit == c2.suit) {
            val v1s = if (c1.rank == Rank.ACE) listOf(1, 14) else listOf(c1.rank.value)
            val v2s = if (c2.rank == Rank.ACE) listOf(1, 14) else listOf(c2.rank.value)
            for (v1 in v1s) {
                for (v2 in v2s) {
                    if (abs(v1 - v2) in 1..2) return true
                }
            }
        }
        return false
    }
}