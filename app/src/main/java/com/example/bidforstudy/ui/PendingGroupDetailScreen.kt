package com.example.bidforstudy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bidforstudy.auth.AuthViewModel
import com.example.bidforstudy.data.BiddingManager
import com.example.bidforstudy.data.PendingGroupBid
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingGroupDetailScreen(
    authViewModel: AuthViewModel,
    pendingGroup: PendingGroupBid,
    onBack: () -> Unit
) {
    val currentUser = authViewModel.currentUser
    val isOwner = (currentUser != null && currentUser == pendingGroup.ownerId)
    val isSecondChance = pendingGroup.isSecondChance

    var yourBidInput by remember {
        mutableStateOf(
            pendingGroup.members
                .find { it.userId == currentUser }
                ?.amount
                ?.toString()
                ?: ""
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSecondChance) "Second-chance Group Offer"
                        else "Group Bid Details"
                    )
                },
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
        ) {
            Text(
                "Room ${pendingGroup.key.roomNumber} – ${pendingGroup.key.timeRange}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Date: ${pendingGroup.key.reservationDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Join code: ${pendingGroup.joinCode}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (isSecondChance) {
                Text(
                    "This is a second-chance offer for your group. " +
                            "Only the group owner can submit or cancel this offer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Group members and bids:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            pendingGroup.members.forEach { m ->
                Text(
                    "- ${m.userId}: ${m.amount} tokens",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val groupTotal = pendingGroup.members.sumOf { it.amount }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Current group total: $groupTotal tokens",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- CHANGE BID: only for normal pending groups, not second-chance ---
            if (!isSecondChance && currentUser != null) {
                Text("Your bid amount:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = yourBidInput,
                    onValueChange = {
                        yourBidInput = it
                        error = null
                        message = null
                    },
                    label = { Text("Your bid (tokens)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val amt = yourBidInput.toIntOrNull()
                        if (amt == null || amt <= 0) {
                            error = "Enter a positive number."
                            return@Button
                        }
                        val res = BiddingManager.updateGroupMemberBid(
                            key = pendingGroup.key,
                            joinCode = pendingGroup.joinCode,
                            userId = currentUser,
                            newAmount = amt
                        )
                        if (!res.success) {
                            error = res.errorMessage
                            message = null
                        } else {
                            // update local view
                            val m = pendingGroup.members.find { it.userId == currentUser }
                            if (m != null) m.amount = amt
                            error = null
                            message = "Your bid updated."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change bid")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Status messages ---
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (message != null) {
                Text(text = message!!, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- OWNER ACTIONS: submit / cancel ---
            if (isOwner) {
                Button(
                    onClick = {
                        val res = BiddingManager.cancelGroupBid(
                            key = pendingGroup.key,
                            joinCode = pendingGroup.joinCode,
                            requesterId = pendingGroup.ownerId
                        )
                        if (!res.success) {
                            error = res.errorMessage
                            message = null
                        } else {
                            message = if (isSecondChance)
                                "Second-chance offer cancelled."
                            else
                                "Group cancelled."
                            error = null
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSecondChance) "Cancel second-chance offer"
                        else "Cancel group"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val res = BiddingManager.submitGroupBid(
                            key = pendingGroup.key,
                            joinCode = pendingGroup.joinCode,
                            requesterId = pendingGroup.ownerId,
                            now = LocalDateTime.now()
                        )
                        if (!res.success) {
                            error = res.errorMessage
                            message = null
                        } else {
                            message = if (isSecondChance)
                                "Second-chance group bid submitted!"
                            else
                                "Group bid submitted!"
                            error = null
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSecondChance) "Submit second-chance bid"
                        else "Submit group bid"
                    )
                }
            }
        }
    }
}
