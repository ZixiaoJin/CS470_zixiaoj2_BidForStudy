package com.example.bidforstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.bidforstudy.ui.BidForStudyApp
import com.example.bidforstudy.ui.theme.BidForStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BidForStudyTheme {
                BidForStudyApp()
            }
        }
    }
}
