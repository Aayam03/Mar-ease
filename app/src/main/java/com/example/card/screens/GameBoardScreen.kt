package com.example.card.screens

import android.annotation.SuppressLint
import androidx.compose.animation.*
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.card.*
import com.example.card.components.*
import com.example.card.ui.theme.CardTheme
import kotlinx.coroutines.delay

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun GameBoardScreen(
    playerCount: Int, 
    difficulty: Difficulty,
    showHints: Boolean, 
    navController: NavController,
    viewModel: GameViewModel
) {
    LaunchedEffect(playerCount, showHints, difficulty) {
        viewModel.initGame(playerCount, showHints, difficulty)
    }

    val gameState = viewModel.gameState ?: return

    val themeDifficulty = if (showHints) Difficulty.EASY else difficulty
    CardTheme(difficulty = themeDifficulty) {
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
                // Calculate points for the human player (P1)
                val results = GameEngine.getGameResult(
                    winner = gameState.winner!!,
                    playerCount = gameState.playerCount,
                    playerHands = gameState.playerHands.mapValues { it.value.toList() },
                    shownCards = gameState.shownCards.mapValues { it.value.toList() },
                    hasShown = gameState.hasShown.toMap(),
                    maalCard = gameState.maalCard,
                    isDubliShow = gameState.isDubliShow.toMap(),
                    startingBonuses = gameState.startingBonuses.toMap()
                )
                
                val p1Result = results.playerResults.find { it.player == 1 }
                val p1Points = p1Result?.adjustment ?: 0
                
                viewModel.updateStats(showHints, difficulty, p1Points)
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B3D2F))) {
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
                        UserProfileIcon(showHints, viewModel, onShowHistory = { navController.navigate("history") })
                    }
                    
                    if (showHints && gameState.hint != null) {
                        HintView(
                            hint = gameState.hint,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = cardHeight + 80.dp, end = 16.dp)
                        )
                    }

                    // Game Messages Overlay
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedVisibility(
                            visible = gameState.gameMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = gameState.gameMessage ?: "",
                                    color = Color.White,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
