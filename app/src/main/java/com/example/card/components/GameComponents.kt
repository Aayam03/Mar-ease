package com.example.card.components

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.card.AnimationSource
import com.example.card.AnimationType
import com.example.card.GameState
import com.example.card.Hint
import com.example.card.TurnPhase

@Composable
fun TopAreaView(
    gameState: GameState,
    cardHeight: Dp,
    cardWidth: Dp,
    onStockPilePositioned: (Offset) -> Unit,
    onDiscardPilePositioned: (Offset) -> Unit,
    onPlayerIconPositioned: (Int, Offset) -> Unit,
    onToggleShowView: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(start = 12.dp)) { 
        PlayerIndicatorsView(gameState, onPlayerIconPositioned, onToggleShowView)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(48.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.onGloballyPositioned { onStockPilePositioned(it.localToRoot(Offset.Zero)) }) {
                PileView(
                    name = "Stock", 
                    pile = gameState.stockPile, 
                    faceUp = false,
                    cardHeight = cardHeight * 0.85f, 
                    cardWidth = cardWidth * 0.85f,
                    onClick = { if (gameState.currentPlayer == 1 && gameState.currentTurnPhase == TurnPhase.DRAW) gameState.humanDrawsFromStock() }
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Maal", style = MaterialTheme.typography.titleSmall, fontSize = 14.sp)
                Box(modifier = Modifier.height(cardHeight * 0.75f).width(cardWidth * 0.75f), contentAlignment = Alignment.Center) {
                    val isMaalVisible = gameState.maalCard != null
                    if (isMaalVisible && gameState.maalCard != null) {
                        CardView(card = gameState.maalCard, faceUp = true, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                    }
                }
            }

            Box(modifier = Modifier.onGloballyPositioned { onDiscardPilePositioned(it.localToRoot(Offset.Zero)) }) {
                PileView(
                    name = "Discard", 
                    pile = gameState.discardPile, 
                    faceUp = true, 
                    cardHeight = cardHeight * 0.85f,
                    cardWidth = cardWidth * 0.85f,
                    highlight = gameState.hint?.cards?.any { it.isSameInstance(gameState.discardPile.lastOrNull() ?: it) } ?: false,
                    onClick = { if (gameState.currentPlayer == 1 && gameState.currentTurnPhase == TurnPhase.DRAW) gameState.humanDrawsFromDiscard() }
                )
            }
        }
    }
}

@Composable
fun PlayerIndicatorsView(gameState: GameState, onPlayerIconPositioned: (Int, Offset) -> Unit, onToggleShowView: (Int) -> Unit) {
    Row(modifier = Modifier.wrapContentWidth().padding(start = 60.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        for (i in 1..gameState.playerCount) {
            val shown = gameState.hasShown[i] == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerIndicator(i, gameState, onPlayerIconPositioned)
                if (shown) {
                    IconButton(
                        onClick = { onToggleShowView(i) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("👁️", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerIndicator(player: Int, gameState: GameState, onPlayerIconPositioned: (Int, Offset) -> Unit) {
    val isCurrent = player == gameState.currentPlayer
    val icon = gameState.playerIcons[player] ?: ""
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(4.dp)
            .onGloballyPositioned { onPlayerIconPositioned(player, it.localToRoot(Offset.Zero)) }
    ) {
        Text("P$player", fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        Text(icon, fontSize = 24.sp)
    }
}

@Composable
fun HintView(hint: Hint?, modifier: Modifier = Modifier) {
    if (hint == null) return
    var isMinimized by remember { mutableStateOf(false) }

    if (isMinimized) {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { isMinimized = false },
            contentAlignment = Alignment.Center
        ) {
            Text("💡", fontSize = 20.sp)
        }
    } else {
        Card(
            modifier = modifier.width(220.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hint: ${hint.title}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "➖", 
                        modifier = Modifier.clickable { isMinimized = true }.padding(2.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(hint.message, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun AnimatedCard(
    gameState: GameState,
    stockPilePos: Offset,
    discardPilePos: Offset,
    playerPositions: Map<Int, Offset>,
    myHandPos: Offset,
    cardHeight: Dp,
    cardWidth: Dp
) {
    val animState = gameState.animationState ?: return

    val startPos = when (animState.source) {
        AnimationSource.STOCK -> stockPilePos
        AnimationSource.DISCARD -> discardPilePos
        AnimationSource.PLAYER -> if (animState.player == 1) myHandPos else playerPositions[animState.player] ?: Offset.Zero
    }

    val endPos = when (animState.type) {
        AnimationType.DRAW -> if (animState.player == 1) myHandPos else playerPositions[animState.player] ?: Offset.Zero
        AnimationType.DISCARD -> discardPilePos
    }

    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(animState) {
        animationStarted = false
        animationStarted = true
    }

    val offset by animateOffsetAsState(
        targetValue = if (animationStarted) endPos else startPos,
        label = "cardAnimation",
        finishedListener = { gameState.animationState = null }
    )

    if (startPos != Offset.Zero) {
        CardView(
            card = animState.card,
            faceUp = animState.isFaceUp,
            modifier = Modifier.offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }.height(cardHeight).width(cardWidth)
        )
    }
}
