package com.example.bidforstudy.ui

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
import com.example.bidforstudy.data.AuctionKey
import com.example.bidforstudy.data.BidEntry
import com.example.bidforstudy.data.BiddingManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

data class RoomSlot(
    val roomNumber: String,
    val capacity: Int,
    val timeRange: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableRoomsScreen(
    date: String,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val selectedDate = remember(date) { LocalDate.parse(date, formatter) }

    val today = LocalDate.now()
    val oneWeekAhead = today.plusWeeks(1)
    val oneMonthAhead = today.plusMonths(1)

    val isPast = selectedDate.isBefore(today)
    val isAfterMonth = selectedDate.isAfter(oneMonthAhead)
    val isWithinWeekButNotPast = !isPast && selectedDate.isBefore(oneWeekAhead)
    val isActiveWindow =
        !selectedDate.isBefore(oneWeekAhead) && !selectedDate.isAfter(oneMonthAhead)

    val showRooms = !isPast && !isAfterMonth

    // Rooms: single-person & multi-person
    val slots = listOf(
        RoomSlot("Room 201", 1, "09:00 – 11:00"),
        RoomSlot("Room 201", 1, "11:00 – 13:00"),
        RoomSlot("Room 305", 2, "09:00 – 11:00"),
        RoomSlot("Room 305", 2, "11:00 – 13:00"),
        RoomSlot("Room B12", 3, "09:00 – 11:00"),
        RoomSlot("Room B12", 3, "11:00 – 13:00"),
    )

    val selectedSlotState = remember { mutableStateOf<RoomSlot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rooms on $date") },
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
            when {
                isPast || isAfterMonth -> {
                    Text(
                        text = "No rooms open for bidding on this date.",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val explanation = when {
                        isPast -> "This date is in the past."
                        isAfterMonth -> "Bidding opens only up to one month in advance."
                        else -> ""
                    }

                    if (explanation.isNotEmpty()) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                selectedSlotState.value == null -> {
                    val titleText = when {
                        isWithinWeekButNotPast ->
                            "Rooms (bidding closed – view only)"
                        isActiveWindow ->
                            "Rooms available for bidding"
                        else ->
                            "Rooms"
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isWithinWeekButNotPast) {
                        Text(
                            text = "This date is within one week. Bidding has closed, " +
                                    "but you can still view room details and bid history.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (isActiveWindow) {
                        Text(
                            text = "Select a single-person room to place a bid.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(slots) { slot ->
                            RoomSlotCard(
                                slot = slot,
                                reservationDate = selectedDate,
                                isActiveWindow = isActiveWindow,
                                isWithinWeek = isWithinWeekButNotPast,
                                onClick = { selectedSlotState.value = slot }
                            )
                        }
                    }
                }

                else -> {
                    val slot = selectedSlotState.value!!
                    BidDetailPage(
                        date = selectedDate,
                        slot = slot,
                        authViewModel = authViewModel,
                        isWithinWeekButNotPast = isWithinWeekButNotPast,
                        isActiveWindow = isActiveWindow,
                        onBackToList = { selectedSlotState.value = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomSlotCard(
    slot: RoomSlot,
    reservationDate: LocalDate,
    isActiveWindow: Boolean,
    isWithinWeek: Boolean,
    onClick: () -> Unit
) {
    val key = remember(slot, reservationDate) {
        AuctionKey(
            roomNumber = slot.roomNumber,
            capacity = slot.capacity,
            timeRange = slot.timeRange,
            reservationDate = reservationDate
        )
    }
    val currentBid = BiddingManager.getCurrentBid(key)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Room: ${slot.roomNumber}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (slot.capacity == 1)
                    "Single-person room"
                else
                    "Group room: up to ${slot.capacity} people",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Time: ${slot.timeRange}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Current bid: $currentBid tokens",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Bidding is open only between 1 week and 1 month ahead
            val canBid = isActiveWindow

            val buttonLabel = when {
                isWithinWeek -> "View bid (closed)"
                canBid && slot.capacity == 1 -> "Place bid (active)"
                canBid && slot.capacity > 1 -> "Group bid (active)"
                else -> "View"
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun BidDetailPage(
    date: LocalDate,
    slot: RoomSlot,
    authViewModel: AuthViewModel,
    isWithinWeekButNotPast: Boolean,
    isActiveWindow: Boolean,
    onBackToList: () -> Unit
) {
    val currentUser = authViewModel.currentUser
    val key = remember(slot, date) {
        AuctionKey(
            roomNumber = slot.roomNumber,
            capacity = slot.capacity,
            timeRange = slot.timeRange,
            reservationDate = date
        )
    }

    val endTime = key.reservationDate.minusWeeks(1).atStartOfDay()
    val now = LocalDateTime.now()

    val singleBidActive = (slot.capacity == 1) && now.isBefore(endTime) && isActiveWindow
    val groupBidActive  = (slot.capacity > 1) && now.isBefore(endTime) && isActiveWindow

    val bidStatusText = if (singleBidActive || groupBidActive) "Active" else "Closed"

    var currentBid by remember { mutableStateOf(BiddingManager.getCurrentBid(key)) }
    var bidHistory by remember { mutableStateOf(BiddingManager.getLastBidsForAuction(key, 5)) }
    var bidInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var userTokens by remember { mutableStateOf(authViewModel.getCurrentUserTokens()) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Room ${slot.roomNumber} – ${slot.timeRange}",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Capacity: ${slot.capacity}", style = MaterialTheme.typography.bodyMedium)
        Text("Reservation date: $date", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Bid end time: $endTime",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Bid status: $bidStatusText",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Current bid: $currentBid tokens", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Your available tokens: $userTokens", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))

        if (slot.capacity == 1) {
            OutlinedTextField(
                value = bidInput,
                onValueChange = {
                    bidInput = it
                    error = null
                    message = null
                },
                label = { Text("Bid amount (tokens)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = singleBidActive && currentUser != null
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (currentUser == null) {
                        error = "Please log in again to place a bid."
                        return@Button
                    }

                    val amount = bidInput.toIntOrNull()
                    if (amount == null) {
                        error = "Please enter a valid number."
                        return@Button
                    }

                    val result = BiddingManager.placeSingleBid(
                        key = key,
                        bidderId = currentUser,
                        amount = amount
                    )

                    if (!result.success) {
                        error = result.errorMessage ?: "Failed to place bid."
                        message = null
                    } else {
                        currentBid = BiddingManager.getCurrentBid(key)
                        bidHistory = BiddingManager.getLastBidsForAuction(key, 5)
                        userTokens = authViewModel.getCurrentUserTokens()
                        error = null
                        message = "Bid placed successfully!"
                        bidInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = singleBidActive && currentUser != null
            ) {
                Text("Place bid")
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Multi-person group bidding
            var inviteAmount by remember { mutableStateOf("") }
            var joinCodeInput by remember { mutableStateOf("") }
            var joinAmount by remember { mutableStateOf("") }

            Text(
                text = "This is a multi-person room. You can start or join a group bid.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Invite a group",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = inviteAmount,
                onValueChange = {
                    inviteAmount = it
                    error = null
                    message = null
                },
                label = { Text("Your bid amount (tokens)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser != null && groupBidActive
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    if (currentUser == null) {
                        error = "Please log in to start a group."
                        return@Button
                    }
                    val amt = inviteAmount.toIntOrNull()
                    if (amt == null || amt <= 0) {
                        error = "Enter a positive number."
                        return@Button
                    }
                    val res = BiddingManager.startGroupBid(
                        key = key,
                        ownerId = currentUser,
                        amount = amt
                    )
                    if (!res.success) {
                        error = res.errorMessage
                        message = null
                    } else {
                        message = "Group created. Your join code: $currentUser"
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser != null && groupBidActive
            ) {
                Text("Invite group")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Join a group",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = joinCodeInput,
                onValueChange = {
                    joinCodeInput = it
                    error = null
                    message = null
                },
                label = { Text("Join code (owner's user ID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser != null && groupBidActive
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = joinAmount,
                onValueChange = {
                    joinAmount = it
                    error = null
                    message = null
                },
                label = { Text("Your bid amount (tokens)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser != null && groupBidActive
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    if (currentUser == null) {
                        error = "Please log in to join a group."
                        return@Button
                    }
                    val amt = joinAmount.toIntOrNull()
                    if (amt == null || amt <= 0) {
                        error = "Enter a positive number."
                        return@Button
                    }
                    val res = BiddingManager.joinGroupBid(
                        key = key,
                        joinCode = joinCodeInput,
                        userId = currentUser,
                        amount = amt
                    )
                    if (!res.success) {
                        error = res.errorMessage
                        message = null
                    } else {
                        message = "Joined group $joinCodeInput successfully."
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser != null && groupBidActive
            ) {
                Text("Join group")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }


        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(4.dp))
        }
        message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Bid history (last 5)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (bidHistory.isEmpty()) {
            Text("No bids yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            bidHistory.forEach { bid ->
                BidHistoryItem(bid)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onBackToList) {
            Text("Back to rooms")
        }
    }
}

@Composable
private fun BidHistoryItem(bid: BidEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text("Bid: ${bid.amount} tokens", style = MaterialTheme.typography.bodyMedium)
        Text("Time: ${bid.timestamp}", style = MaterialTheme.typography.labelSmall)
    }
}
