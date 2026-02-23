package com.example.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.card.components.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun CardGameApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("startup") {
            StartupScreen(navController = navController)
        }
        composable("player_selection") {
            PlayerSelectionScreen { playerCount, difficulty ->
                navController.navigate("game_board/$playerCount/$difficulty/false") {
                    popUpTo("startup")
                }
            }
        }
        composable("game_board/{playerCount}/{difficulty}/{showHints}") { backStackEntry ->
            val playerCount = backStackEntry.arguments?.getString("playerCount")?.toIntOrNull() ?: 4
            val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: "HARD"
            val difficulty = try { Difficulty.valueOf(difficultyStr) } catch (_: Exception) { Difficulty.HARD }
            val showHints = backStackEntry.arguments?.getString("showHints")?.toBoolean() ?: false
            GameBoardScreen(playerCount = playerCount, difficulty = difficulty, showHints = showHints, navController = navController)
        }
    }
}

@Composable
fun StartupScreen(navController: NavController, viewModel: GameViewModel = viewModel()) {
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    val logoSize = (screenHeight * 0.3f).coerceAtLeast(150.dp)
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            UserProfileIcon(showHints = false, viewModel = viewModel)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.mar_ease), 
                contentDescription = "Mar-ease Logo",
                modifier = Modifier.size(logoSize)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.navigate("game_board/4/HARD/true") }) {
                Text("Learn", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("player_selection") }) {
                Text("Play", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun UserProfileIcon(showHints: Boolean, viewModel: GameViewModel) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    var showProfileDetails by remember { mutableStateOf(false) }
    val stats = viewModel.userStats

    Box(modifier = Modifier.padding(16.dp)) {
        if (user.photoUrl != null) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = "User Profile",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { showProfileDetails = !showProfileDetails },
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { showProfileDetails = !showProfileDetails },
                color = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Profile",
                    modifier = Modifier.padding(8.dp),
                    tint = Color.White
                )
            }
        }

        if (showProfileDetails) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp)
                    .width(220.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = user.displayName ?: "Guest", color = Color.White, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray)
                    
                    if (showHints) {
                        Text(text = "LEARN MODE", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "Games: ${stats.learnGames}", color = Color.White, fontSize = 14.sp)
                        Text(text = "Total Points: ${stats.learnPoints}", color = Color.White, fontSize = 14.sp)
                    } else {
                        Text(text = "PLAY MODE", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "Games: ${stats.playGames}", color = Color.White, fontSize = 14.sp)
                        Text(text = "Total Points: ${stats.playPoints}", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GameBoardScreen(
    playerCount: Int, 
    difficulty: Difficulty,
    showHints: Boolean, 
    navController: NavController,
    viewModel: GameViewModel = viewModel()
) {
    LaunchedEffect(playerCount, showHints, difficulty) {
        viewModel.initGame(playerCount, showHints, difficulty)
    }

    val gameState = viewModel.gameState ?: return

    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    
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

    // Explaining Highlights in Learn Mode
    if (showHints) {
        val lastDrawn = gameState.lastDrawnCard
        val hasMelds = gameState.meldedCards.isNotEmpty()
        val hasSelection = gameState.selectedCards.isNotEmpty()
        val hasHints = gameState.hint?.cards?.isNotEmpty() == true
        val hasJokers = gameState.hasShown[1] == true && (gameState.playerHands[1]?.any { gameState.isJoker(it, 1) } == true)

        LaunchedEffect(lastDrawn) {
            if (lastDrawn != null && viewModel.explainedHighlights["yellow"] != true) {
                gameState.showGameMessage("Yellow Highlight: Indicates the card you just drew.")
                viewModel.markHighlightExplained("yellow")
            }
        }
        LaunchedEffect(hasMelds) {
            if (hasMelds && viewModel.explainedHighlights["green"] != true) {
                gameState.showGameMessage("Green Highlight: Identifies cards that are part of a valid sequence or set.")
                viewModel.markHighlightExplained("green")
            }
        }
        LaunchedEffect(hasSelection) {
            if (hasSelection && viewModel.explainedHighlights["pink"] != true) {
                gameState.showGameMessage("Pink Highlight: Shows the cards you've currently selected.")
                viewModel.markHighlightExplained("pink")
            }
        }
        LaunchedEffect(hasHints) {
            if (hasHints && viewModel.explainedHighlights["red"] != true) {
                gameState.showGameMessage("Red Highlight: Points to cards suggested by the hint.")
                viewModel.markHighlightExplained("red")
            }
        }
        LaunchedEffect(hasJokers) {
            if (hasJokers && viewModel.explainedHighlights["cyan"] != true) {
                gameState.showGameMessage("Cyan Highlight: Marks cards that have become Jokers after Maal revelation.")
                viewModel.markHighlightExplained("cyan")
            }
        }

        // Contextual Dubli Strategy Hint
        val myHand = gameState.playerHands[1]?.toList() ?: emptyList()
        LaunchedEffect(myHand, gameState.currentTurnPhase) {
            if (!viewModel.explainedDubliStrategy && 
                gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD &&
                AiPlayer.isAimingForDubli(1, myHand, gameState)) {
                
                delay(1000)
                viewModel.markDubliStrategyExplained()
                viewModel.showDubliOverlay = true
            }
        }
    }

    LaunchedEffect(gameState.winner) {
        if (gameState.winner != null) {
            val finalPointsDiff = GameEngine.getFinalScoreDifference(
                gameState.winner!!, 
                gameState.playerCount, 
                gameState.playerHands, 
                gameState.shownCards, 
                gameState.hasShown, 
                gameState.maalCard
            )
            viewModel.updateStats(showHints, -finalPointsDiff)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20))) {
        if (gameState.isInitializing) {
            LoadingScreen()
        } else {
            if (showHints) {
                MainGameLayoutLearn(
                    gameState = gameState,
                    cardHeight = cardHeight,
                    cardWidth = cardWidth,
                    onStockPilePositioned = { stockPilePos = it },
                    onDiscardPilePositioned = { discardPilePos = it },
                    onPlayerIconPositioned = { p, pos -> playerPositions[p] = pos },
                    onToggleShowView = { p -> selectedPlayerForShowView = p },
                    onHandPositioned = { myHandPos = it }
                )
            } else {
                MainGameLayoutPlay(
                    gameState = gameState,
                    cardHeight = cardHeight,
                    cardWidth = cardWidth,
                    onStockPilePositioned = { stockPilePos = it },
                    onDiscardPilePositioned = { discardPilePos = it },
                    onPlayerIconPositioned = { p, pos -> playerPositions[p] = pos },
                    onToggleShowView = { p -> selectedPlayerForShowView = p },
                    onHandPositioned = { myHandPos = it }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    UserProfileIcon(showHints, viewModel)
                }
            }

            GameControls(
                showHints = showHints,
                onPauseClick = { viewModel.togglePauseMenu(true) },
                onHelpClick = { viewModel.toggleHelp(true) }
            )

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

        OverlayManager(
            viewModel = viewModel,
            gameState = gameState,
            navController = navController,
            selectedPlayerForShowView = selectedPlayerForShowView,
            cardHeight = cardHeight,
            cardWidth = cardWidth,
            onDismissShowView = { selectedPlayerForShowView = null }
        )
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

                Button(onClick = { gameState.humanShows() }, enabled = canShow) {
                    Text("SHOW", fontWeight = FontWeight.Bold)
                }
            }
            
            // DISCARD/WIN button logic
            if (gameState.currentTurnPhase == TurnPhase.PLAY_OR_DISCARD && gameState.selectedCards.size == 1) {
                val selected = gameState.selectedCards.first()
                val hand = gameState.playerHands[1]?.toList() ?: emptyList()
                val canWin = AiPlayer.canFinish(hand.filter { it !== selected }, gameState, 1)

                Button(onClick = { 
                    if (canWin) {
                        gameState.humanWinsGame(selected)
                    } else {
                        gameState.requestDiscardSelection() 
                    }
                }) {
                    Text(if (canWin) "WIN" else "DISCARD", fontWeight = FontWeight.Bold)
                }
            }

            // END TURN button
            if (gameState.currentTurnPhase == TurnPhase.SHOW_OR_END) {
                Button(onClick = { gameState.humanEndsTurnWithoutShowing() }) {
                    Text("END TURN", fontWeight = FontWeight.Bold)
                }
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

    if (gameState.winner == null && viewModel.hasClosedHelpOnce && !viewModel.showHelp && !viewModel.showPauseMenu && !viewModel.showDubliOverlay) { 
        Box(modifier = Modifier.fillMaxSize()) {
            HintView(
                hint = gameState.hint, 
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
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

    if (viewModel.showPauseMenu) {
        PauseMenuOverlay(
            onResume = { viewModel.togglePauseMenu(false) },
            onGoBack = { navController.navigate("startup") { popUpTo("startup") { inclusive = true } } },
            onLearnFromStart = { 
                gameState.setupGame(gameState.playerCount, gameState.difficulty)
                viewModel.togglePauseMenu(false)
            }
        )
    }

    if (viewModel.showDiscardSelection) {
        SelectionDialog(
            candidates = viewModel.discardCandidates,
            onSelected = { card -> 
                gameState.humanDiscardsCard(card)
                viewModel.closeDiscardSelection()
            },
            onDismiss = { viewModel.closeDiscardSelection() }
        )
    }

    if (gameState.winner != null) {
        GameEndOverlay(gameState, navController)
    }
    
    selectedPlayerForShowView?.let { player ->
        ShownCardsView(player, gameState, cardHeight, cardWidth, onDismissShowView)
    }
}

@Composable
fun MainGameLayoutLearn(
    gameState: GameState,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    onStockPilePositioned: (Offset) -> Unit,
    onDiscardPilePositioned: (Offset) -> Unit,
    onPlayerIconPositioned: (Int, Offset) -> Unit,
    onToggleShowView: (Int) -> Unit,
    onHandPositioned: (Offset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
        Box(modifier = Modifier.height(70.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ActionButtons(gameState)
        }
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
fun MainGameLayoutPlay(
    gameState: GameState,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    onStockPilePositioned: (Offset) -> Unit,
    onDiscardPilePositioned: (Offset) -> Unit,
    onPlayerIconPositioned: (Int, Offset) -> Unit,
    onToggleShowView: (Int) -> Unit,
    onHandPositioned: (Offset) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val players = (2..gameState.playerCount).toList()
        players.forEachIndexed { index, player ->
            val alignment = when (gameState.playerCount) {
                2 -> Alignment.TopCenter
                3 -> if (index == 0) Alignment.TopStart else Alignment.TopEnd
                4 -> when (index) {
                    0 -> Alignment.TopStart
                    1 -> Alignment.TopCenter
                    else -> Alignment.TopEnd
                }
                else -> when (index) {
                    0 -> Alignment.TopStart
                    1 -> Alignment.TopCenter
                    2 -> Alignment.TopEnd
                    else -> Alignment.CenterEnd
                }
            }
            Box(modifier = Modifier.align(alignment).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerIndicator(player, gameState, onPlayerIconPositioned)
                    if (gameState.hasShown[player] == true) {
                        Text("👁️", modifier = Modifier.clickable { onToggleShowView(player) }.padding(4.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.onGloballyPositioned { onStockPilePositioned(it.localToRoot(Offset.Zero)) }) {
                    PileView("Stock", gameState.stockPile, false, cardHeight * 0.8f, cardWidth * 0.8f) {
                        if (gameState.currentPlayer == 1 && (gameState.currentTurnPhase == TurnPhase.DRAW || gameState.currentTurnPhase == TurnPhase.INITIAL_CHECK)) gameState.humanDrawsFromStock()
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Maal", color = Color.White, fontSize = 12.sp)
                    Box(modifier = Modifier.size(cardWidth * 0.7f, cardHeight * 0.7f)) {
                        val isMaalVisible = gameState.hasShown[1] == true && gameState.maalCard != null
                        if (isMaalVisible && gameState.maalCard != null) {
                            CardView(card = gameState.maalCard, faceUp = true, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                        }
                    }
                }

                Box(modifier = Modifier.onGloballyPositioned { onDiscardPilePositioned(it.localToRoot(Offset.Zero)) }) {
                    PileView("Discard", gameState.discardPile, true, cardHeight * 0.8f, cardWidth * 0.8f) {
                        if (gameState.currentPlayer == 1 && (gameState.currentTurnPhase == TurnPhase.DRAW || gameState.currentTurnPhase == TurnPhase.INITIAL_CHECK)) gameState.humanDrawsFromDiscard()
                    }
                }
            }
            ActionButtons(gameState)
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(cardHeight + 60.dp)) {
            PlayerHandView(
                hand = gameState.playerHands[1] ?: emptyList(),
                cardHeight = cardHeight,
                cardWidth = cardWidth,
                selectedCards = gameState.selectedCards,
                highlightedCards = emptyList(),
                onCardClick = { card -> gameState.toggleCardSelection(card) },
                isJokerSeen = gameState.hasShown[1] ?: false,
                gameState = gameState,
                onHandPositioned = onHandPositioned
            )
        }
    }
}
