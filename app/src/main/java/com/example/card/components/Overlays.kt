package com.example.card.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.card.GameEngine
import com.example.card.GameState

@Composable
fun PauseMenuOverlay(onResume: () -> Unit, onGoBack: () -> Unit, onLearnFromStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { onResume() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp).width(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paused", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text("Resume")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onLearnFromStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Learn from Start")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onGoBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Go Back")
                }
            }
        }
    }
}

@Composable
fun BasicsOverlay(hasShown: Boolean, onDismiss: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(currentPage) {
        scrollState.scrollTo(0)
    }

    val pages = remember(hasShown) {
        val list = mutableListOf<@Composable () -> Unit>()
        
        // Introduction Page (Contextual) - Only show if hasShown is true
        if (hasShown) {
            list.add {
                Column {
                    Text("You've just performed a 'Show'!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Great job! You have revealed the Maal and earned your initial points. Now you can use Jokers more freely and aim to complete all 7 melds.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        list.add {
            Column {
                Text("1. Objective", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Form valid combination of 3 cards(Melds) with your cards. The ultimate goal is to form 7 melds or 8 pairs (Dubli).",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("2. Turn Basics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Every turn begins by drawing a card (from Stock or Discard) by tapping on them and ends by discarding a card or clicking SHOW/END TURN.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("3. Melds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                     text = buildString{
                         append("- Runs: 3+ sequential cards of the same suit (e.g., 5♥, 6♥, 7♥).\n")
                         append("- Triples: 3 cards of the same rank (different suits OR exact same suit).\n")
                         append("- Jokers: Can substitute for any card to complete a Run or Triple. Multiple Jokers can even be used together with a single standard card to form a 3-card meld.\n")
                         append("Note : Only Runs and Triples of the Exact same card can be used for 'show'\n")
                     },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("4. Dubli (Alternative Strategy)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Dubli is a rare but powerful way to play. Instead of forming melds, you form pairs of the exact same card (same rank and same suit).\n\n" +
                    "- To Show: You need 7 pairs (14 cards total).\n" +
                    "- To Win: You need 8 pairs (16 cards total).\n" +
                    "- Joker Rules: In Dubli, printed Jokers can only pair with another printed Joker. Maal-based jokers act as their base card for pairing.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("5. Special First Turn Show", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "If you have 3 Jokers or 3 identical cards (same rank AND same suit) on your very first turn, you can SHOW them immediately for a bonus:\n" +
                    "- 3 Jokers: +25 Points\n- 3 Identical Cards: +10 Points",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("6. Standard Showing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Once you have at least 3 valid melds (or 7 pairs for Dubli), you can press SHOW whenever you have 21 or 22 cards in your hand. This reveals the Maal (Special Joker) and earns you points.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("7. Viewing Shown Cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "When a player has shown their melds, you can view them by clicking the EYE (👁️) icon next to their names. This helps you track which cards are no longer in play.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("8. The Maal (Special Joker)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "When someone shows, a card is picked as the Maal. That card, and cards related to it, become Jokers:\n" +
                    "- Standard Joker: Always a Joker.\n" +
                    "- The Maal Card: Exact match of revealed card.\n" +
                    "- Same Rank: Cards with same rank as Maal.\n" +
                    "- Neighbors: Cards with same suit as Maal but +/- 1 in rank.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("9. Maal Calculation (Points)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "After showing, you gain points for these cards in your hand:\n" +
                    "- Standard Joker: 5 Points\n" +
                    "- The Maal Card itself: 3 Points\n" +
                    "- Same Rank as Maal (Same Color): 5 Points\n" +
                    "- Neighbors of Maal: 2 Points",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("10. Winning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Form 7 melds total (or 8 pairs for Dubli) to win. Your final score depends on your Maal points vs others.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("11. Card Highlights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cards are highlighted with different colors to help you identify their state:", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))

                val highlights = listOf(
                    Triple(Color.Yellow, "Last Drawn", "The card you just picked up from the Stock or Discard pile."),
                    Triple(Color(0xFFFF69B4), "Selected", "Cards you've currently tapped for showing or discarding."),
                    Triple(Color(0xFF4CAF50), "Melded", "Cards that are already part of a valid sequence or set."),
                    Triple(Color.Cyan, "Maal Joker", "Cards that have become Jokers after the Maal is revealed."),
                    Triple(Color.Red, "Hint / Warning", "Suggested cards to draw/discard or important alerts.")
                )

                highlights.forEach { (color, label, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .border(3.dp, color, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                            Text(desc, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .width(500.dp)
                .heightIn(max = 450.dp)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) 
        {
            Box(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text("END", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                }

                Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f).padding(top = 40.dp).verticalScroll(scrollState)) {
                        pages[currentPage]()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Text("Previous", fontSize = 18.sp)
                        }
                        
                        Text("Page ${currentPage + 1} of ${pages.size}", style = MaterialTheme.typography.labelMedium)

                        TextButton(
                            onClick = { if (currentPage < pages.size - 1) currentPage++ else onDismiss() }
                        ) {
                            Text(if (currentPage < pages.size - 1) "Next" else "Finish", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameEndOverlay(gameState: GameState, navController: NavController) {
    val breakdown = GameEngine.getDetailedMaalBreakdown(1, gameState.playerHands, gameState.shownCards, gameState.hasShown, gameState.maalCard)
    val totalBonus = gameState.startingBonuses[1] ?: 0
    val totalMaal = gameState.calculateMaal(1)
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(8.dp).fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Game Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = gameState.hint?.message ?: "", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your Maal Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (totalBonus > 0) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                    Text("First Turn Bonus", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("+$totalBonus", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                }
                            }
                        }
                        items(breakdown) { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Box(modifier = Modifier.size(width = 35.dp, height = 50.dp)) {
                                    CardView(card = item.card, faceUp = true)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item.reason, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text("+${item.points}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text("Total Maal Points", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$totalMaal", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { gameState.setupGame(4) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Learn More")
                    }
                    Button(
                        onClick = { navController.navigate("player_selection") },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Play without Hints")
                    }
                }
            }
        }
    }
}

@Composable
fun ShownCardsView(
    player: Int, 
    gameState: GameState, 
    cardHeight: androidx.compose.ui.unit.Dp, 
    cardWidth: androidx.compose.ui.unit.Dp, 
    onDismiss: () -> Unit
) {
    val shownCards = gameState.shownCards[player] ?: emptyList()
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(scrollState), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Shown cards - Player $player", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                val melds = shownCards.chunked(3)
                melds.forEach { meld ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        meld.forEach { card -> CardView(card = card, faceUp = true, modifier = Modifier.height(cardHeight * 0.7f).width(cardWidth * 0.7f)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
