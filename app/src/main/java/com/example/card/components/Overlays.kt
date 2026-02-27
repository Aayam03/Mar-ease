package com.example.card.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.example.card.Card as CardData
import com.example.card.GameEngine
import com.example.card.GameState
import com.example.card.TurnPhase
import com.example.card.Rank
import com.example.card.Difficulty
import com.example.card.GameViewModel

@Composable
fun OverlayManager(
    viewModel: GameViewModel,
    gameState: GameState,
    navController: NavController,
    selectedPlayerForShowView: Int?,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    onDismissShowView: () -> Unit
) {
    if (viewModel.showPauseMenu) {
        PauseMenuOverlay(
            onResume = { viewModel.togglePauseMenu(false) },
            onGoBack = { navController.popBackStack() },
            onLearnFromStart = {
                viewModel.togglePauseMenu(false)
                gameState.setupGame(gameState.playerCount, gameState.difficulty)
            }
        )
    }

    if (viewModel.showHelp) {
        BasicsOverlay(
            hasShown = gameState.hasShown[1] ?: false,
            onDismiss = { viewModel.toggleHelp(false) }
        )
    }

    if (viewModel.showDubliOverlay) {
        DubliStrategyOverlay(onDismiss = { viewModel.showDubliOverlay = false })
    }

    if (gameState.selectionCandidate.isNotEmpty()) {
        SelectionDialog(
            candidates = gameState.selectionCandidate,
            onSelected = { card -> gameState.humanSelectsCard(card) },
            onDismiss = { gameState.clearSelectionCandidate() }
        )
    }

    if (gameState.winner != null) {
        GameEndOverlay(gameState = gameState, navController = navController)
    }

    if (selectedPlayerForShowView != null) {
        ShownCardsView(
            player = selectedPlayerForShowView,
            gameState = gameState,
            cardHeight = cardHeight,
            cardWidth = cardWidth,
            onDismiss = onDismissShowView
        )
    }
}

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
            @Suppress("DEPRECATION")
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paused", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text("Resume")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onLearnFromStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Restart Game")
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
        
        if (hasShown) {
            list.add {
                Column {
                    @Suppress("DEPRECATION")
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
                    "Form valid combinations (Melds) with your cards. The ultimate goal is to form 7 melds (for a win) or 8 pairs (Dubli strategy).",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "In this 21-card variant, you start with 21 cards. Every turn you draw one card (making it 22) and must discard one to end your turn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Note: You can only pick from the Discard pile if the top card helps you complete a meld immediately!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        list.add {
            Column {
                Text("3. Melds (Runs & Triples)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                     text = buildString {
                         append("• Runs: 3+ sequential cards of the same suit (e.g., 5♥, 6♥, 7♥).\n\n")
                         append("• Triples: 3 cards of the same rank (e.g., 8♠, 8♦, 8♣).\n\n")
                         append("• Jokers: Can substitute for any card. Multiple Jokers can be used in a single meld.\n\n")
                         append("• Pure Melds: For the initial 'Show', melds must be 'Pure' (no jokers unless the joker acts as its original rank/suit).")
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
                    "• To Show: You need 7 pairs (14 cards total).\n" +
                    "• To Win: You need 8 pairs (16 cards total).\n" +
                    "• Joker Rules: In Dubli, printed Jokers can only pair with another printed Joker. Maal-based jokers act as their base card for pairing.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("5. Special First Turn Show", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "If you have 3 Jokers or 3 identical cards (same rank AND same suit) on your very first turn, you can SHOW them immediately for a bonus:\n\n" +
                    "• 3 Jokers: +30 Points (Tunnela)\n" +
                    "• 3 Identical Cards: +5 Points (Tunnela)",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("6. Initial 'Show' Requirements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Before you can see the 'Maal' or use Jokers freely, you must 'Show' your hand by completing:\n\n" +
                    "• 3 Pure Melds (9 cards total)\n" +
                    "• OR 7 Pairs (Dubli strategy)\n\n" +
                    "Once you show, the 'Maal' card is revealed from the stock pile.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("7. The Maal (Special Jokers)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "When someone shows, a card is picked as the Maal. That card, and cards related to it, become Jokers for EVERYONE who has shown:\n\n" +
                    "• Tiplu: Exact match of the Maal card.\n" +
                    "• Poplu: Rank above Maal (same suit).\n" +
                    "• Jhiplu: Rank below Maal (same suit).\n" +
                    "• Alter Cards: Same rank/neighbors but different suits (lower points).",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("8. Maal Points & Marriage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "You earn points for holding Maal cards in your hand or shown melds:\n\n" +
                    "• Tiplu: 3 Points\n" +
                    "• Poplu/Jhiplu: 2 Points\n" +
                    "• Marriage (Tiplu + Poplu + Jhiplu of same suit): 10 Points!\n\n" +
                    "Multiple identical cards multiply points (Double = x3, Triple = x5).",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        list.add {
            Column {
                Text("9. Winning & Final Scores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "The game ends when a player completes 7 melds (or 8 pairs) and discards their last card.\n\n" +
                    "Final scores are calculated by comparing your total Maal points against every other player's total. Winners also get bonuses from players who didn't show!",
                    style = MaterialTheme.typography.bodyLarge
                )
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
                .padding(8.dp)
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.95f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) 
        {
            Box(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text("END TUTORIAL", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                }

                @Suppress("DEPRECATION")
                Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f).padding(top = 24.dp).verticalScroll(scrollState)) {
                        pages[currentPage]()
                    }

                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Text("Previous", fontSize = 20.sp)
                            }
                            
                            Text(
                                "Page ${currentPage + 1} of ${pages.size}", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(
                                onClick = { if (currentPage < pages.size - 1) currentPage++ else onDismiss() }
                            ) {
                                Text(if (currentPage < pages.size - 1) "Next" else "Let's Play!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DubliStrategyOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp).width(500.dp).heightIn(max = 400.dp).clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text("CLOSE", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                }

                @Suppress("DEPRECATION")
                Column(modifier = Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("New Strategy: Aim for Dubli!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your current hand has many identical pairs but few potential melds. This is a perfect opportunity to try the 'Dubli' strategy!\n\n" +
                        "Instead of regular runs/sets, collect 7 pairs of identical cards to show Maal, and 8 pairs to win.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDismiss) {
                        Text("I'll try it!")
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionDialog(
    candidates: List<CardData>, 
    onSelected: (CardData) -> Unit, 
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp).width(500.dp).heightIn(max = 450.dp).clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            @Suppress("DEPRECATION")
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select a card to Discard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "All of these cards are equally 'worthless' for your hand. Please choose which one to discard.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(candidates) { card ->
                        Box(modifier = Modifier.size(60.dp, 90.dp).clickable { onSelected(card) }) {

                            CardView(card = card, faceUp = true)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun GameEndOverlay(gameState: GameState, navController: NavController) {
    val result = remember {
        GameEngine.getGameResult(
            winner = gameState.winner!!,
            playerCount = gameState.playerCount,
            playerHands = gameState.playerHands.mapValues { it.value.toList() },
            shownCards = gameState.shownCards.mapValues { it.value.toList() },
            hasShown = gameState.hasShown.toMap(),
            maalCard = gameState.maalCard,
            isDubliShow = gameState.isDubliShow.toMap(),
            startingBonuses = gameState.startingBonuses.toMap()
        )
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(8.dp).fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            @Suppress("DEPRECATION")
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Game Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(result.playerResults) { playerRes ->
                        val isHuman = playerRes.player == 1
                        val icon = gameState.playerIcons[playerRes.player] ?: "🤖"
                        val name = if (isHuman) "You (P1)" else "Player ${playerRes.player}"
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (isHuman) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("Total Maal: ${playerRes.totalMaal}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                                
                                if (playerRes.hasShown) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Maal Breakdown:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Light)
                                    
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        mainAxisSpacing = 8.dp,
                                        crossAxisSpacing = 8.dp
                                    ) {
                                        playerRes.breakdown.forEach { item ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(modifier = Modifier.size(width = 30.dp, height = 45.dp)) {
                                                    CardView(card = item.card, faceUp = true)
                                                }
                                                Text("+${item.points}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                            }
                                        }
                                    }
                                } else {
                                    Text("Did not show cards", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                    
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Final Points Adjustment", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = result.explanation, 
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { gameState.setupGame(gameState.playerCount, gameState.difficulty) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("New Game")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("startup") { popUpTo("startup") { inclusive = true } } },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Exit to Menu")
                    }
                }
            }
        }
    }
}


@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val lineHeights = mutableListOf<Int>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0

        placeholders.forEach { placeable ->
            if (currentLineWidth + placeable.width > layoutWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                lineHeights.add(currentLine.maxOf { it.height })
                currentLine = mutableListOf()
                currentLineWidth = 0
            }
            currentLine.add(placeable)
            currentLineWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
            lineHeights.add(currentLine.maxOf { it.height })
        }

        val totalHeight = lineHeights.sum() + (lineHeights.size - 1).coerceAtLeast(0) * crossAxisSpacing.roundToPx()
        
        layout(layoutWidth, totalHeight) {
            var y = 0
            lines.forEachIndexed { index, line ->
                var x = 0
                line.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += lineHeights[index] + crossAxisSpacing.roundToPx()
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
            @Suppress("DEPRECATION")
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
