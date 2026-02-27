package com.example.card.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.card.GameViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun UserProfileIcon(showHints: Boolean, viewModel: GameViewModel, onShowHistory: () -> Unit = {}) {
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
                    
                    Text(text = "PLAY MODE", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(text = "Easy", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Games: ${stats.easyGames} | Points: ${stats.easyPoints}", color = Color.White, fontSize = 13.sp)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Medium", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Games: ${stats.mediumGames} | Points: ${stats.mediumPoints}", color = Color.White, fontSize = 13.sp)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Hard", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Games: ${stats.hardGames} | Points: ${stats.hardPoints}", color = Color.White, fontSize = 13.sp)

                    Button(
                        onClick = { 
                            showProfileDetails = false
                            onShowHistory() 
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Game History", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
