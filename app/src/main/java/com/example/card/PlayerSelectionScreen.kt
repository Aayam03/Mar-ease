package com.example.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun PlayerSelectionScreen(
    viewModel: GameViewModel = viewModel(),
    onBack: () -> Unit = {},
    onStartGame: (Int, Difficulty) -> Unit
) {
    var selectedPlayers by remember { mutableStateOf(2) }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Game Settings", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Number of Players", 
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "AI Difficulty", 
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
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
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { onStartGame(selectedPlayers, selectedDifficulty) },
                modifier = Modifier.width(200.dp).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)) // Visible blue button
            ) {
                Text("Start Game", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}
