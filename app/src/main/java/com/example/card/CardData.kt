package com.example.card

import java.util.UUID

// Represents a playing card with a suit and a rank.
// Added a unique ID to distinguish identical cards (same rank/suit) for selection.
data class Card(
    val suit: Suit, 
    val rank: Rank, 
    val id: String = UUID.randomUUID().toString()
) {
    override fun toString(): String {
        if (rank == Rank.JOKER) return "Joker"
        return "${rank.symbol}${suit.symbol}"
    }
    
    // Identity check based on unique ID
    fun isSameInstance(other: Card): Boolean = this.id == other.id
}

// Enum for card suits with their symbols.
enum class Suit(val symbol: Char) {
    HEARTS('♥'), DIAMONDS('♦'), CLUBS('♣'), SPADES('♠'),
    NONE(' ') // Suit for Jokers
}

// Enum for card ranks with their symbols and values.
enum class Rank(val symbol: String, val value: Int) {
    JOKER("Joker", 15),
    ACE("A", 14), KING("K", 13), QUEEN("Q", 12), JACK("J", 11),
    TEN("10", 10), NINE("9", 9), EIGHT("8", 8), SEVEN("7", 7),
    SIX("6", 6), FIVE("5", 5), FOUR("4", 4), THREE("3", 3), TWO("2", 2);

    companion object {
        fun fromValue(value: Int): Rank? = entries.find { it.value == value }
    }
}
