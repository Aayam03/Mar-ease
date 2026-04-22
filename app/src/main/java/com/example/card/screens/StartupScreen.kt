package com.example.card.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.card.GameViewModel
import com.example.card.R
import com.example.card.components.UserProfileIcon

@Composable
fun StartupScreen(navController: NavController, viewModel: GameViewModel) {
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    val logoSize = (screenHeight * 0.3f).coerceAtLeast(150.dp)
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            UserProfileIcon(showHints = false, viewModel = viewModel, onShowHistory = { navController.navigate("history") })
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
