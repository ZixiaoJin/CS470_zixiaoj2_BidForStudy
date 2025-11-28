package com.example.bidforstudy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bidforstudy.auth.AuthViewModel
import com.example.bidforstudy.data.BiddingManager
import com.example.bidforstudy.data.ReservationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationHistoryScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onReservationClick: (ReservationSummary) -> Unit
) {
    val userId = authViewModel.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Reservation History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (userId == null) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text("Please log in to view your reservations.")
            }
        } else {
            val history = BiddingManager.getUserReservationHistory(userId, limit = 10)

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (history.isEmpty()) {
                    Text("You have no reservations yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { item ->
                            ReservationCard(
                                item = item,
                                onClick = { onReservationClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    item: ReservationSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Room ${item.auctionKey.roomNumber} – ${item.auctionKey.timeRange}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Date: ${item.auctionKey.reservationDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Winning bid: ${item.amount} tokens",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Tap to manage / cancel (placeholder)",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
