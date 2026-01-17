package com.example.card

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.card.ui.theme.CardTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase is initialized automatically by the google-services plugin, 
        // but it doesn't hurt to have it here if needed for specific contexts.
        setContent {
            CardTheme {
                CardGameApp()
            }
        }
    }
}
