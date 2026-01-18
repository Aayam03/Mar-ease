package com.example.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlayerSelectionScreen(
    viewModel: GameViewModel = viewModel(),
    onPlayersSelected: (Int) -> Unit
) {
    var selectedPlayers by remember { mutableStateOf(2) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Profile Icon in top right
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
            Text("Select Number of Players", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (2..6).forEach { count ->
                    Button(
                        onClick = { selectedPlayers = count },
                        colors = if (selectedPlayers == count) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        }
                    ) {
                        Text("$count")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onPlayersSelected(selectedPlayers) }) {
                Text("Start Game")
            }
        }
    }
}
