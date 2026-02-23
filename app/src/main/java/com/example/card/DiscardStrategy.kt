package com.example.card

import kotlin.random.Random

interface DiscardStrategy {
    fun findBestDiscard(
        hand: List<Card>,
        gameState: GameState,
        playerIndex: Int
    ): List<Card>
}

class EasyDiscardStrategy : DiscardStrategy {
    override fun findBestDiscard(hand: List<Card>, gameState: GameState, playerIndex: Int): List<Card> {
        // Easy: Just pick a random non-joker card
        val nonJokers = hand.filter { it.rank != Rank.JOKER }
        return if (nonJokers.isNotEmpty()) listOf(nonJokers.random()) else listOf(hand.random())
    }
}

class MediumDiscardStrategy : DiscardStrategy {
    override fun findBestDiscard(hand: List<Card>, gameState: GameState, playerIndex: Int): List<Card> {
        // Medium: Standard logic, but with basic pair detection
        val pairsCount = hand.groupBy { "${it.rank}${it.suit}" }.filter { it.value.size >= 2 }.size
        val isAimingForDubli = pairsCount >= 5
        return AiPlayer.findBestDiscardOptions(hand, gameState, playerIndex, isAimingForDubli)
    }
}

class HardDiscardStrategy : DiscardStrategy {
    override fun findBestDiscard(hand: List<Card>, gameState: GameState, playerIndex: Int): List<Card> {
        // Hard: Professional logic, preserves Maal, and switches to Dubli if beneficial
        val pairsCount = hand.groupBy { "${it.rank}${it.suit}" }.filter { it.value.size >= 2 }.size
        val isAimingForDubli = pairsCount >= 4
        
        val options = AiPlayer.findBestDiscardOptions(hand, gameState, playerIndex, isAimingForDubli)
        
        // Aggressive Maal preservation
        val nonMaalOptions = options.filter { !GameEngine.isMaal(it, gameState.maalCard) }
        return nonMaalOptions.ifEmpty { options }
    }
}
