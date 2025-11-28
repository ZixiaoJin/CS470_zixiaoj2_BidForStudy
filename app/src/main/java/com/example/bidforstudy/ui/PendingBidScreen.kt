package com.example.bidforstudy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bidforstudy.auth.AuthViewModel
import com.example.bidforstudy.data.BiddingManager
import com.example.bidforstudy.data.PendingGroupBid
import com.example.bidforstudy.data.SecondChanceBid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBidScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onOpenPendingGroupDetail: (PendingGroupBid) -> Unit
) {
    val userId = authViewModel.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending bids") },
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
                Text("Please log in to view your pending bids.")
            }
        } else {
            val pendingGroups = BiddingManager.getPendingGroupsForUser(userId)
            val secondChances = BiddingManager.getSecondChanceBidsForUser(userId)

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (pendingGroups.isEmpty() && secondChances.isEmpty()) {
                    Text("You have no pending bids.")
                    return@Column
                }

                if (secondChances.isNotEmpty()) {
                    Text(
                        "Second-chance offers (single rooms)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(secondChances) { sc ->
                            SecondChanceCard(
                                sc = sc,
                                authViewModel = authViewModel
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (pendingGroups.isNotEmpty()) {
                    Text(
                        "Group bids you started or joined",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pendingGroups) { pg ->
                            PendingGroupCard(
                                group = pg,
                                currentUserId = userId,
                                onClick = { onOpenPendingGroupDetail(pg) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingGroupCard(
    group: PendingGroupBid,
    currentUserId: String,
    onClick: () -> Unit
) {
    val isOwner = (group.ownerId == currentUserId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Room ${group.key.roomNumber} – ${group.key.timeRange}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Date: ${group.key.reservationDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Join code: ${group.joinCode}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Members: ${group.members.size}/${group.capacity}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (group.isSecondChance)
                    "Second-chance group offer"
                else if (isOwner)
                    "You started this group."
                else
                    "You joined this group.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SecondChanceCard(
    sc: SecondChanceBid,
    authViewModel: AuthViewModel
) {
    val room = sc.key.roomNumber
    val time = sc.key.timeRange
    val date = sc.key.reservationDate
    val amount = sc.amount

    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Room $room – $time",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Date: $date", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Your original bid: $amount tokens",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val userId = authViewModel.currentUser
                        if (userId == null) {
                            error = "Please log in."
                            message = null
                            return@Button
                        }
                        val result = BiddingManager.submitSecondChanceBid(sc.key, userId)
                        if (!result.success) {
                            error = result.errorMessage ?: "Failed to accept offer."
                            message = null
                        } else {
                            error = null
                            message = "Offer accepted. Reservation added."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Submit")
                }

                OutlinedButton(
                    onClick = {
                        val userId = authViewModel.currentUser
                        if (userId == null) {
                            error = "Please log in."
                            message = null
                            return@OutlinedButton
                        }
                        val result = BiddingManager.cancelSecondChanceBid(sc.key, userId)
                        if (!result.success) {
                            error = result.errorMessage ?: "Failed to cancel offer."
                            message = null
                        } else {
                            error = null
                            message = "Pending offer cancelled."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
