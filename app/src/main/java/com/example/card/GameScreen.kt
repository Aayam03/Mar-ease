package com.example.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.card.components.*
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
fun GameBoardScreen(
    playerCount: Int, 
    showHints: Boolean, 
    navController: NavController,
    viewModel: GameViewModel = viewModel()
) {
    // Ensure initGame is only called ONCE when the screen opens.
    LaunchedEffect(playerCount, showHints) {
        viewModel.initGame(playerCount, showHints)
    }

    val gameState = viewModel.gameState ?: return

    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    
    // ADJUST SCALE: 0.22f provides a safe buffer for landscape/different aspect ratios
    val cardHeight = (screenHeight * 0.22f).coerceAtMost(180.dp) 
    val cardWidth = cardHeight * 0.65f 

    var stockPilePos by remember { mutableStateOf(Offset.Zero) }
    var discardPilePos by remember { mutableStateOf(Offset.Zero) }
    var myHandPos by remember { mutableStateOf(Offset.Zero) }
    val playerPositions = remember { mutableMapOf<Int, Offset>() }
    var selectedPlayerForShowView by remember { mutableStateOf<Int?>(null) }

    val hasPlayerShown = gameState.hasShown[1] == true
    LaunchedEffect(hasPlayerShown) {
        if (hasPlayerShown && !gameState.isInitializing) {
            delay(1500) 
            viewModel.toggleHelp(true)
        }
    }

    // BACKGROUND AND CONTENT
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20))) { // Dark Green felt color
        if (gameState.isInitializing) {
            LoadingScreen()
        } else {
            // Main Game UI
            MainGameLayout(
                gameState = gameState,
                cardHeight = cardHeight,
                cardWidth = cardWidth,
                onStockPilePositioned = { stockPilePos = it },
                onDiscardPilePositioned = { discardPilePos = it },
                onPlayerIconPositioned = { p, pos -> playerPositions[p] = pos },
                onToggleShowView = { p -> selectedPlayerForShowView = p },
                onHandPositioned = { myHandPos = it }
            )

            // Overlays on top of the layout
            GameControls(
                showHints = showHints,
                onPauseClick = { viewModel.togglePauseMenu(true) },
                onHelpClick = { viewModel.toggleHelp(true) }
            )

            OverlayManager(
                viewModel = viewModel,
                gameState = gameState,
                navController = navController,
                selectedPlayerForShowView = selectedPlayerForShowView,
                cardHeight = cardHeight,
                cardWidth = cardWidth,
                onDismissShowView = { selectedPlayerForShowView = null }
            )

            // THE CARDS THEMSELVES (Animation Layer)
            AnimatedCard(
                gameState = gameState,
                stockPilePos = stockPilePos,
                discardPilePos = discardPilePos,
                playerPositions = playerPositions,
                myHandPos = myHandPos,
                cardHeight = cardHeight,
                cardWidth = cardWidth
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Dealing Cards...", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun MainGameLayout(
    gameState: GameState,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    onStockPilePositioned: (Offset) -> Unit,
    onDiscardPilePositioned: (Offset) -> Unit,
    onPlayerIconPositioned: (Int, Offset) -> Unit,
    onToggleShowView: (Int) -> Unit,
    onHandPositioned: (Offset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // TOP SECTION: Opponents and Piles
        Box(modifier = Modifier.weight(1.3f).fillMaxWidth()) {
            TopAreaView(
                gameState = gameState,
                cardHeight = cardHeight,
                cardWidth = cardWidth,
                onStockPilePositioned = onStockPilePositioned,
                onDiscardPilePositioned = onDiscardPilePositioned,
                onPlayerIconPositioned = onPlayerIconPositioned,
                onToggleShowView = onToggleShowView
            )
        }

        // MIDDLE SECTION: Actions (Fixed Height)
        Box(
            modifier = Modifier
                .height(70.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ActionButtons(gameState)
        }

        // BOTTOM SECTION: Player Hand
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PlayerHandView(
                hand = gameState.playerHands[1] ?: emptyList(),
                cardHeight = cardHeight,
                cardWidth = cardWidth,
                selectedCards = gameState.selectedCards,
                highlightedCards = gameState.hint?.cards ?: emptyList(),
                onCardClick = { card -> gameState.toggleCardSelection(card) },
                isJokerSeen = gameState.hasShown[1] ?: false,
                gameState = gameState,
                onHandPositioned = onHandPositioned
            )
        }
    }
}

@Composable
fun ActionButtons(gameState: GameState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (gameState.winner == null && gameState.currentPlayer == 1) {
            if (gameState.hasShown[1] == false && 
                (gameState.currentTurnPhase == TurnPhase.DRAW || gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD || gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) &&
                (gameState.playerHands[1]?.size == 21 || gameState.playerHands[1]?.size == 22 || (gameState.isFirstTurn && gameState.currentTurnPhase == TurnPhase.DRAW))
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

                Button(onClick = { gameState.humanShows() }, enabled = canShow) {
                    Text("SHOW", fontWeight = FontWeight.Bold)
                }
            }
            
            if (gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD && gameState.selectedCards.size == 1) {
                Button(onClick = { gameState.humanDiscardsCard(gameState.selectedCards.first()) }) {
                    Text("DISCARD", fontWeight = FontWeight.Bold)
                }
            }

            if (gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) {
                Button(onClick = { gameState.humanEndsTurnWithoutShowing() }) {
                    Text("END TURN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GameControls(
    showHints: Boolean,
    onPauseClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "⏸️",
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clickable { onPauseClick() }
        )

        if (showHints) {
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

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

    // Hint View
    if (gameState.winner == null && viewModel.hasClosedHelpOnce && !viewModel.showHelp && !viewModel.showPauseMenu) { 
        Box(modifier = Modifier.fillMaxSize()) {
            HintView(
                hint = gameState.hint, 
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
    }

    // Basics Overlay
    if (viewModel.showHelp) {
        BasicsOverlay(
            hasShown = gameState.hasShown[1] ?: false,
            onDismiss = { viewModel.toggleHelp(false) }
        )
    }

    // Pause Menu Overlay
    if (viewModel.showPauseMenu) {
        PauseMenuOverlay(
            onResume = { viewModel.togglePauseMenu(false) },
            onGoBack = { navController.navigate("startup") { popUpTo("startup") { inclusive = true } } },
            onLearnFromStart = { 
                gameState.setupGame(gameState.playerCount)
                viewModel.togglePauseMenu(false)
            }
        )
    }

    // Game End Overlay
    if (gameState.winner != null) {
        GameEndOverlay(gameState, navController)
    }
    
    // Shown Cards View
    selectedPlayerForShowView?.let { player ->
        ShownCardsView(player, gameState, cardHeight, cardWidth, onDismissShowView)
    }
}
