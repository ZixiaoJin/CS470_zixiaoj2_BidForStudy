package com.example.bidforstudy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bidforstudy.auth.AuthViewModel
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.material3.Button

private enum class HomeTab { MAIN, PERSONAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    onAddTokensClick: () -> Unit,
    onDateSelected: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenBidHistory: () -> Unit,
    onOpenReservationHistory: () -> Unit,
    onOpenPendingBids: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.MAIN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BidForStudy") },
                actions = {
                    IconButton(onClick = onAddTokensClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Tokens"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.MAIN,
                    onClick = { selectedTab = HomeTab.MAIN },
                    label = { Text("Main") },
                    icon = { /* add icon later if you like */ }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.PERSONAL,
                    onClick = { selectedTab = HomeTab.PERSONAL },
                    label = { Text("Personal") },
                    icon = { /* add icon later if you like */ }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                HomeTab.MAIN -> CalendarSection(onDateSelected = onDateSelected)
                HomeTab.PERSONAL -> PersonalSection(
                    authViewModel = authViewModel,
                    onLogout = onLogout,
                    onOpenBidHistory = onOpenBidHistory,
                    onOpenReservationHistory = onOpenReservationHistory,
                    onOpenPendingBids = onOpenPendingBids
                )
            }
        }
    }
}

@Composable
private fun CalendarSection(
    onDateSelected: (String) -> Unit
) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val nextMonth = currentMonth.plusMonths(1)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            "Select a date",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        MonthCalendar(yearMonth = currentMonth, onDateSelected = onDateSelected)
        Spacer(modifier = Modifier.height(24.dp))
        MonthCalendar(yearMonth = nextMonth, onDateSelected = onDateSelected)
    }
}

@Composable
private fun MonthCalendar(
    yearMonth: YearMonth,
    onDateSelected: (String) -> Unit
) {
    val title =
        "${yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${yearMonth.year}"

    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    // Weekday header: Sun–Sat
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()

    // java.time.DayOfWeek: Monday = 1 ... Sunday = 7
    // We want Sunday index 0, Monday 1, ... Saturday 6
    val leadingBlanks = firstDay.dayOfWeek.value % 7   // Sunday -> 0

    // Build list of cells (null = empty cell, LocalDate = real day)
    val cells = mutableListOf<LocalDate?>()

    repeat(leadingBlanks) {
        cells.add(null) // leading empty slots
    }

    for (day in 1..daysInMonth) {
        cells.add(yearMonth.atDay(day))
    }

    // Pad with trailing blanks so total cells is a multiple of 7
    val remainder = cells.size % 7
    if (remainder != 0) {
        repeat(7 - remainder) {
            cells.add(null)
        }
    }

    val weeks = cells.chunked(7)

    weeks.forEach { week ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            week.forEach { date ->
                if (date == null) {
                    // Empty box to keep grid alignment
                    Box(
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    TextButton(
                        onClick = { onDateSelected(date.toString()) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalSection(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onOpenBidHistory: () -> Unit,
    onOpenReservationHistory: () -> Unit,
    onOpenPendingBids: () -> Unit
) {
    val tokens = authViewModel.getCurrentUserTokens()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Personal", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Available tokens: $tokens", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onOpenBidHistory, modifier = Modifier.fillMaxWidth()) {
            Text("View bid history")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onOpenReservationHistory, modifier = Modifier.fillMaxWidth()) {
            Text("View reservation history")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onOpenPendingBids, modifier = Modifier.fillMaxWidth()) {
            Text("View pending bids")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Log out")
        }
    }
}
