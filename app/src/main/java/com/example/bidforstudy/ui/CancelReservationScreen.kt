package com.example.bidforstudy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bidforstudy.auth.AuthViewModel
import com.example.bidforstudy.data.AuctionKey
import com.example.bidforstudy.data.BiddingManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelReservationScreen(
    authViewModel: AuthViewModel,
    roomNumber: String,
    date: String,
    timeRange: String,
    capacity: Int,
    amount: Int,
    onBack: () -> Unit
) {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val reservationDate = remember(date) { LocalDate.parse(date, formatter) }

    val key = remember {
        AuctionKey(
            roomNumber = roomNumber,
            capacity = capacity,
            timeRange = timeRange,
            reservationDate = reservationDate
        )
    }

    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cancel reservation") },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Room $roomNumber – $timeRange",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Capacity: $capacity", style = MaterialTheme.typography.bodyMedium)
            Text("Reservation date: $reservationDate", style = MaterialTheme.typography.bodyMedium)
            Text("Your bid amount: $amount tokens", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))

            if (capacity == 1) {
                Text(
                    text = "If you cancel at least one day before the reservation date, " +
                            "you will get 50% of your tokens back. The system will then " +
                            "offer this room to the next highest bidder as a pending bid.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "This is a group reservation. If the group owner cancels at least " +
                            "one day before the reservation date, each member will receive " +
                            "50% of their contribution back. The system will then try to " +
                            "offer the reservation to the next highest group as a " +
                            "second-chance pending group bid.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val currentUser = authViewModel.currentUser
                    if (currentUser == null) {
                        error = "Please log in again."
                        message = null
                        return@Button
                    }

                    val result = if (capacity == 1) {
                        // Single-person reservation cancel
                        BiddingManager.cancelReservationForUser(
                            key = key,
                            userId = currentUser,
                            amount = amount
                        )
                    } else {
                        // Group reservation cancel – only group owner can succeed
                        BiddingManager.cancelGroupReservationForOwner(
                            key = key,
                            ownerId = currentUser
                        )
                    }

                    if (!result.success) {
                        error = result.errorMessage ?: "Failed to cancel reservation."
                        message = null
                    } else {
                        error = null
                        message = if (capacity == 1) {
                            "Reservation cancelled. 50% of your tokens were refunded. " +
                                    "If there was another bidder, they now have a pending offer."
                        } else {
                            "Group reservation cancelled. 50% of each member's tokens were refunded. " +
                                    "If there was another group, they may now have a second-chance pending bid."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel reservation")
            }

            Spacer(modifier = Modifier.height(16.dp))

            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(4.dp))
            }
            message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
