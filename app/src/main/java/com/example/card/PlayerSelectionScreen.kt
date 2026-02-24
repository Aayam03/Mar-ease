package com.example.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSelectionScreen(
    viewModel: GameViewModel = viewModel(),
    onBack: () -> Unit = {},
    onStartGame: (Int, Difficulty) -> Unit
) {
    var selectedPlayers by remember { mutableIntStateOf(2) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF1B3D2F)) // Dark green background
    ) {
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            UserProfileIcon(showHints = false, viewModel = viewModel)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Game Settings", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Number of Players", 
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (2..6).forEach { count ->
                        FilterChip(
                            selected = selectedPlayers == count,
                            onClick = { selectedPlayers = count },
                            label = { Text("$count") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1976D2),
                                selectedLabelColor = Color.White,
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AI Difficulty", 
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Difficulty.entries.forEach { difficulty ->
                        FilterChip(
                            selected = selectedDifficulty == difficulty,
                            onClick = { selectedDifficulty = difficulty },
                            label = { Text(difficulty.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1976D2),
                                selectedLabelColor = Color.White,
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }
            }
            
            Button(
                onClick = { onStartGame(selectedPlayers, selectedDifficulty) },
                modifier = Modifier
                    .width(250.dp)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text("Start Game", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            
            // Extra spacer to ensure scrolling works well on small screens
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
