package com.example.card.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.card.*

@Composable
fun ActionButtons(gameState: GameState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (gameState.winner == null && gameState.currentPlayer == 1) {
            // SHOW button logic
            if (gameState.hasShown[1] == false && 
                (gameState.currentTurnPhase == TurnPhase.INITIAL_CHECK || gameState.currentTurnPhase == TurnPhase.DRAW || gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD || gameState.currentTurnPhase == TurnPhase.SHOW_OR_END)
            ) {
                val selected = gameState.selectedCards.toList()
                val alreadyShown = gameState.shownCards[1]?.size ?: 0
                val reqMelds = 3 - (alreadyShown / 3)
                val reqCards = reqMelds * 3

                val canShow = when (selected.size) {
                    3 -> {
                        if (alreadyShown == 0 && gameState.currentTurnPhase == TurnPhase.INITIAL_CHECK) {
                            val jokers = selected.filter { it.rank == Rank.JOKER }
                            val identical = selected.all { it.rank == selected[0].rank && it.suit == selected[0].suit }
                            jokers.size == 3 || identical
                        } else false
                    }
                    reqCards -> {
                        if (reqMelds > 0) AiPlayer.findAllInitialMelds(selected).size >= reqMelds else false
                    }
                    14 -> {
                        if (alreadyShown == 0) AiPlayer.findDublis(selected).size >= 7 else false
                    }
                    else -> false
                }

                Button(
                    onClick = { gameState.humanShows() }, 
                    enabled = canShow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("SHOW", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            
            // DISCARD/WIN button logic
            if ((gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD || gameState.currentTurnPhase == TurnPhase.INITIAL_CHECK) && gameState.selectedCards.size == 1) {
                val selected = gameState.selectedCards.first()
                val hand = gameState.playerHands[1]?.toList() ?: emptyList()
                val canWin = AiPlayer.canFinish(hand.filter { it !== selected }, gameState, 1)

                Button(
                    onClick = { 
                        if (canWin) {
                            gameState.humanWinsGame(selected)
                        } else {
                            gameState.humanDiscardsCard(selected)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (canWin) Color(0xFFFFD700) else Color(0xFF1976D2))
                ) {
                    Text(
                        text = if (canWin) "WIN" else "DISCARD", 
                        fontWeight = FontWeight.Bold,
                        color = if (canWin) Color.Black else Color.White
                    )
                }
            }

            // END TURN button
            if (gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) {
                Button(
                    onClick = { gameState.humanEndsTurnWithoutShowing() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("END TURN", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
