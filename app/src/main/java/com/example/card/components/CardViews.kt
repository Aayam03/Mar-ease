package com.example.card.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.card.Card as CardData
import com.example.card.GameState
import com.example.card.Rank
import com.example.card.Suit

@Composable
fun CardView(
    modifier: Modifier = Modifier,
    card: CardData?,
    faceUp: Boolean,
    isSelected: Boolean = false,
    isHintHighlight: Boolean = false,
    onCardClick: (() -> Unit)? = null,
    customBorder: BorderStroke? = null
) {
    val cardColor = when (card?.suit) {
        Suit.HEARTS, Suit.DIAMONDS -> Color.Red
        else -> Color.Black
    }
    
    val combinedModifier = if (onCardClick != null) {
        modifier.clickable(onClick = onCardClick)
            .then(if (isSelected) Modifier.offset(y = (-16).dp) else Modifier)
    } else {
        modifier
    }
    
    val border = when {
        isSelected -> BorderStroke(2.dp, Color(0xFFFF69B4)) // Pink Glow
        isHintHighlight -> BorderStroke(2.dp, Color.Red)
        customBorder != null -> customBorder
        else -> BorderStroke(1.dp, Color.Black)
    }
    
    Card(
        modifier = combinedModifier,
        shape = RoundedCornerShape(4.dp),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (faceUp && card != null) {
                if (card.rank == Rank.JOKER) {
                    Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                         Text(text = "🃏", fontSize = 48.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = card.rank.symbol, color = cardColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        Text(text = card.suit.symbol.toString(), color = cardColor, fontSize = 32.sp)
                        Text(text = card.rank.symbol, color = cardColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D47A1), shape = RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
fun PlayerHandView(
    hand: List<CardData>,
    cardHeight: Dp,
    cardWidth: Dp,
    selectedCards: List<CardData>,
    highlightedCards: List<CardData>,
    onCardClick: (CardData) -> Unit,
    isJokerSeen: Boolean,
    gameState: GameState,
    onHandPositioned: (Offset) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.onGloballyPositioned { onHandPositioned(it.localToRoot(Offset.Zero)) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hand (${hand.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (isJokerSeen) Text(" 🃏", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val overlap = cardWidth * 0.3f
            
            val arrangedHand = hand.sortedWith(
                compareBy<CardData> { 
                    when (it.suit) {
                        Suit.HEARTS -> 0
                        Suit.SPADES -> 1
                        Suit.DIAMONDS -> 2
                        Suit.CLUBS -> 3
                        else -> 4
                    }
                }.thenBy { it.rank.value }
            )

            items(arrangedHand) { card ->
                val isJoker = gameState.isJoker(card, 1)
                val isHinted = highlightedCards.any { it.isSameInstance(card) }
                val isSelected = selectedCards.any { it.isSameInstance(card) }
                val isMelded = gameState.meldedCards.any { it.isSameInstance(card) }
                val isLastDrawn = gameState.lastDrawnCard?.isSameInstance(card) == true
                
                val displayBorder = when {
                    isLastDrawn -> BorderStroke(3.dp, Color.Yellow) // Yellow for last drawn card
                    isMelded -> BorderStroke(2.dp, Color(0xFF4CAF50)) // Green for Melds
                    isJoker && (gameState.hasShown[1] == true) -> BorderStroke(2.dp, Color.Cyan)
                    else -> null
                }

                Box(modifier = Modifier.width(cardWidth - overlap)) {
                    CardView(
                        card = card,
                        faceUp = true,
                        isSelected = isSelected,
                        isHintHighlight = isHinted,
                        onCardClick = { onCardClick(card) },
                        modifier = Modifier.height(cardHeight).width(cardWidth),
                        customBorder = displayBorder
                    )
                }
            }
        }
    }
}

@Composable
fun PileView(
    name: String, 
    pile: List<CardData>, 
    faceUp: Boolean, 
    cardHeight: Dp, 
    cardWidth: Dp, 
    highlight: Boolean = false, 
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text("$name (${pile.size})", style = MaterialTheme.typography.labelSmall, fontSize = 14.sp)
        Box(modifier = Modifier.height(cardHeight).width(cardWidth), contentAlignment = Alignment.Center) {
            val card = pile.lastOrNull()
            CardView(card = card, faceUp = faceUp && card != null, isHintHighlight = highlight, modifier = Modifier.fillMaxSize())
        }
    }
}
