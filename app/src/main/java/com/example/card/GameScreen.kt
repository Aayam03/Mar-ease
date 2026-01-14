package com.example.card

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.card.ui.theme.CardTheme
import kotlinx.coroutines.delay

@Composable
fun CardGameApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "startup") {
        composable("startup") {
            StartupScreen(navController = navController)
        }
        composable("player_selection") {
            PlayerSelectionScreen { playerCount ->
                navController.navigate("game_board/$playerCount/false") {
                    popUpTo("startup")
                }
            }
        }
        composable("game_board/{playerCount}/{showHints}") { backStackEntry ->
            val playerCount = backStackEntry.arguments?.getString("playerCount")?.toIntOrNull() ?: 4
            val showHints = backStackEntry.arguments?.getString("showHints")?.toBoolean() ?: false
            GameBoardScreen(playerCount = playerCount, showHints = showHints, navController = navController)
        }
    }
}

@Composable
fun StartupScreen(navController: NavController) {
    val config = LocalConfiguration.current
    val logoSize = (config.screenHeightDp.dp * 0.3f).coerceAtLeast(150.dp)
    
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.mar_ease), 
            contentDescription = "Mar-ease Logo",
            modifier = Modifier.size(logoSize)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.navigate("game_board/4/true") }) {
            Text("Learn", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("player_selection") }) {
            Text("Play", fontSize = 18.sp)
        }
    }
}


@Composable
fun GameBoardScreen(playerCount: Int, showHints: Boolean, navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val gameState = remember { GameState(coroutineScope, showHints).apply { setupGame(playerCount) } }
    var showHelp by remember { mutableStateOf(showHints) }
    var showPauseMenu by remember { mutableStateOf(false) }
    var hasClosedHelpOnce by remember { mutableStateOf(!showHints) }
    
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    // Set card size to 32% of screen height
    val cardHeight = screenHeight * 0.32f
    val cardWidth = cardHeight * 0.48f

    var stockPilePos by remember { mutableStateOf(Offset.Zero) }
    var discardPilePos by remember { mutableStateOf(Offset.Zero) }
    var myHandPos by remember { mutableStateOf(Offset.Zero) }
    val playerPositions = remember { mutableMapOf<Int, Offset>() }
    var selectedPlayerForShowView by remember { mutableStateOf<Int?>(null) }

    // Automatically show help after the player shows to explain new Jokers
    val hasPlayerShown = gameState.hasShown[1] == true
    LaunchedEffect(hasPlayerShown) {
        if (hasPlayerShown && !gameState.isInitializing) {
            delay(1500) 
            showHelp = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (gameState.isInitializing) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Dealing Cards...", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Moved TopAreaView to the left by wrapping in a Row with alignment
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TopAreaView(
                        gameState = gameState,
                        cardHeight = cardHeight,
                        cardWidth = cardWidth,
                        onStockPilePositioned = { stockPilePos = it },
                        onDiscardPilePositioned = { discardPilePos = it },
                        onPlayerIconPositioned = { player, pos -> playerPositions[player] = pos },
                        onToggleShowView = { player -> 
                            selectedPlayerForShowView = if (selectedPlayerForShowView == player) null else player
                        }
                    )
                }

                // Action Buttons Area
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (gameState.winner == null && gameState.currentPlayer == 1) {
                        if (gameState.hasShown[1] == false && 
                            (gameState.currentTurnPhase == TurnPhase.DRAW || gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD || gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) &&
                            (gameState.playerHands[1]?.size == 21 || (gameState.isFirstTurn && gameState.currentTurnPhase == TurnPhase.DRAW))
                        ) {
                            val canShow = if (gameState.isFirstTurn && gameState.currentTurnPhase == TurnPhase.DRAW) {
                                if (gameState.selectedCards.size == 3) {
                                    val jokers = gameState.selectedCards.filter { it.rank == Rank.JOKER }
                                    val identical = gameState.selectedCards.distinctBy { it.rank }.size == 1 &&
                                                   gameState.selectedCards.distinctBy { it.suit }.size == 1
                                    jokers.size == 3 || identical
                                } else false
                            } else {
                                gameState.selectedCards.size == 9 && AiPlayer.findAllInitialMelds(gameState.selectedCards.toList()).size >= 3
                            }

                            Button(
                                onClick = { gameState.humanShows() },
                                enabled = canShow
                            ) {
                                Text("SHOW", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD && gameState.selectedCards.size == 1) {
                            Button(
                                onClick = { gameState.humanDiscardsCard(gameState.selectedCards.first()) }
                            ) {
                                Text("DISCARD", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) {
                            Button(
                                onClick = { gameState.humanEndsTurnWithoutShowing() }
                            ) {
                                Text("END TURN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                PlayerHandView(
                    hand = gameState.playerHands[1] ?: emptyList(),
                    cardHeight = cardHeight,
                    cardWidth = cardWidth,
                    selectedCards = gameState.selectedCards,
                    highlightedCards = gameState.hint?.cards ?: emptyList(),
                    onCardClick = { card -> gameState.toggleCardSelection(card) },
                    isJokerSeen = gameState.hasShown[1] ?: false,
                    gameState = gameState,
                    onHandPositioned = { myHandPos = it }
                )
            }

            // Pause Button (Top Left)
            Text(
                text = "⏸️",
                fontSize = 24.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clickable { showPauseMenu = true }
            )

            // Help Button (Moved to Top Center to avoid being hidden by Hint box)
            if (showHints) {
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                ) {
                    Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Game Message Notification
            gameState.gameMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = msg, 
                            color = Color.White, 
                            modifier = Modifier.padding(32.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Hint View (Aligned right center, moved higher up)
            if (gameState.winner == null && hasClosedHelpOnce && !showHelp && !showPauseMenu) { 
                HintView(
                    hint = gameState.hint, 
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .offset(y = (-80).dp) 
                )
            }

            // Basics explanation overlay
            if (showHelp) {
                BasicsOverlay(
                    hasShown = hasPlayerShown,
                    onDismiss = { 
                        showHelp = false
                        hasClosedHelpOnce = true 
                    }
                )
            }

            // Pause Menu Overlay
            if (showPauseMenu) {
                PauseMenuOverlay(
                    onResume = { showPauseMenu = false },
                    onGoBack = { navController.navigate("startup") { popUpTo("startup") { inclusive = true } } },
                    onLearnFromStart = { 
                        navController.navigate("game_board/4/true") {
                            popUpTo("game_board/{playerCount}/{showHints}") { inclusive = true }
                        }
                        showPauseMenu = false
                    }
                )
            }

            if (gameState.winner != null) {
                GameEndOverlay(gameState, navController)
            }
            
            selectedPlayerForShowView?.let { player ->
                ShownCardsView(player, gameState, cardHeight, cardWidth) { selectedPlayerForShowView = null }
            }

            AnimatedCard(gameState, stockPilePos, discardPilePos, playerPositions, myHandPos, cardHeight, cardWidth)
        }
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
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp).fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("How to Play", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (hasShown) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "NEW: You\u0027ve just performed a \u0027Show\u0027! You can now view which cards other players have shown by clicking the EYE (👁️) icon next to their names.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = "1. Objective:\nForm valid combinations (Melds) with your cards. The ultimate goal is to form 7 melds.\n\n" +
                             "2. Turn Basics:\nEvery turn begins by drawing a card (from Stock or Discard) and ends by discarding a card or clicking SHOW/END TURN.\n\n" +
                             "3. Melds:\n- Runs: 3+ sequential cards of the same suit (e.g., 5♥, 6♥, 7♥).\n- Triples: 3 cards of the same rank (different suits OR exact same suit).\n- Joker Pair: 2 Jokers can form a valid meld.\n\n" +
                             "4. Special First Turn Show:\nIf you have 3 Jokers or 3 identical cards (same rank AND same suit) on your very first turn, you can SHOW them immediately for a bonus:\n" +
                             "- 3 Jokers: +25 Points\n- 3 Identical Cards: +10 Points\n\n" +
                             "5. Standard Showing:\nOnce you have at least 3 valid melds, discard down to 21 cards and press SHOW. This reveals the Maal (Special Joker) and earns you points.\n\n" +
                             "6. The Maal (Special Joker):\nWhen someone shows, a card is picked as the Maal. That card, and cards related to it, become Jokers:\n" +
                             "- Standard Joker: Always a Joker.\n- The Maal Card: Exact match of revealed card.\n- Same Rank: Cards with same rank as Maal and matching color.\n- Neighbors: Cards with same suit as Maal but +/- 1 in rank.\n\n" +
                             "Jokers are highlighted with a Cyan border AFTER you SHOW.\n\n" +
                             "7. Maal Calculation (Points):\nAfter showing, you gain points for these cards in your hand:\n" +
                             "- Standard Joker: 5 Points\n- The Maal Card itself: 3 Points\n- Same Rank as Maal (Same Color): 5 Points\n- Neighbors of Maal: 2 Points\n\n" +
                             "8. Winning:\nForm 7 melds total to win. Your final score depends on your Maal points vs others.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.align(Alignment.CenterHorizontally), // Moved to the center
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Got it!", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun GameEndOverlay(gameState: GameState, navController: NavController) {
    val breakdown = GameEngine.getDetailedMaalBreakdown(1, gameState.playerHands, gameState.shownCards, gameState.hasShown, gameState.maalCard)
    val totalBonus = gameState.bonusMaalPoints[1] ?: 0
    val totalMaal = gameState.calculateMaal(1)
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Game Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(text = gameState.hint?.reason ?: "", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your Maal Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
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

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { gameState.setupGame(4) }) {
                        Text("Learn More")
                    }
                    Button(onClick = { navController.navigate("player_selection") }) {
                        Text("Play without Hints")
                    }
                }
            }
        }
    }
}

@Composable
fun TopAreaView(
    gameState: GameState,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
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
                    if (gameState.hasShown[1] == true && gameState.maalCard != null) {
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
    // Increased start padding to move player icons right and increased spacing between them
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
fun ShownCardsView(player: Int, gameState: GameState, cardHeight: androidx.compose.ui.unit.Dp, cardWidth: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit) {
    val shownCards = gameState.shownCards[player] ?: emptyList()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Shown cards - Player $player", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                val melds = shownCards.chunked(3)
                melds.forEach { meld ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        meld.forEach { card -> CardView(card = card, faceUp = true, modifier = Modifier.height(cardHeight * 0.7f).width(cardWidth * 0.7f)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}


@Composable
fun PileView(name: String, pile: List<Card>, faceUp: Boolean, cardHeight: androidx.compose.ui.unit.Dp, cardWidth: androidx.compose.ui.unit.Dp, highlight: Boolean = false, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text("$name (${pile.size})", style = MaterialTheme.typography.labelSmall, fontSize = 14.sp)
        Box(modifier = Modifier.height(cardHeight).width(cardWidth), contentAlignment = Alignment.Center) {
            val card = pile.lastOrNull()
            CardView(card = card, faceUp = faceUp && card != null, isHintHighlight = highlight, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun HintView(hint: Hint?, modifier: Modifier = Modifier) {
    if (hint == null) return
    Card(
        modifier = modifier.width(280.dp), 
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) { 
            Text(text = "Hint: ${hint.action}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 24.sp) 
            Spacer(modifier = Modifier.height(6.dp))
            Text(hint.reason, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp) 
        }
    }
}

@Composable
fun PlayerHandView(
    hand: List<Card>,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    selectedCards: List<Card>,
    highlightedCards: List<Card>,
    onCardClick: (Card) -> Unit,
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
                compareBy<Card> { 
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
                
                Box(modifier = Modifier.width(cardWidth - overlap)) {
                    CardView(
                        card = card,
                        faceUp = true,
                        isSelected = isSelected,
                        isHintHighlight = isHinted,
                        onCardClick = { onCardClick(card) },
                        modifier = Modifier.height(cardHeight).width(cardWidth),
                        // Only highlight Joker with Cyan border AFTER the player has shown
                        customBorder = if (isJoker && (gameState.hasShown[1] == true)) BorderStroke(2.dp, Color.Cyan) else null
                    )
                }
            }
        }
    }
}

@Composable
fun CardView(
    modifier: Modifier = Modifier,
    card: Card?,
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
                // Check for Joker rank
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
fun AnimatedCard(
    gameState: GameState,
    stockPilePos: Offset,
    discardPilePos: Offset,
    playerPositions: Map<Int, Offset>,
    myHandPos: Offset,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp
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

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun GameBoardPreview() {
    CardTheme {
        GameBoardScreen(playerCount = 4, showHints = true, navController = rememberNavController())
    }
}
