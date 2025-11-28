package com.example.bidforstudy.ui

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
import com.example.bidforstudy.data.UserBidSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BidHistoryScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val userId = authViewModel.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Bid History") },
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
                Text("Please log in to view your bid history.")
            }
        } else {
            val history = BiddingManager.getUserBidHistory(userId, limit = 10)

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (history.isEmpty()) {
                    Text("You have no bids yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { item ->
                            BidHistoryCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BidHistoryCard(item: UserBidSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            if (item.isGroup && item.groupTotalAmount != null) {
                Text(
                    "Your bid: ${item.amount} tokens",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Group total: ${item.groupTotalAmount} tokens",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Bid: ${item.amount} tokens",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val status = when {
                item.isActive && item.isCurrentHighest -> "Active (current highest)"
                item.isActive && !item.isCurrentHighest -> "Active (outbid)"
                !item.isActive && item.isCurrentHighest -> "Closed (you won)"
                else -> "Closed"
            }

            Text(
                "Status: $status",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

