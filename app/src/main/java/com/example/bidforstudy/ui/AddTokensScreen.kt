package com.example.bidforstudy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.bidforstudy.auth.AuthViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTokensScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var examDateInput = remember { mutableStateOf("") }
    var message = remember { mutableStateOf<String?>(null) }
    var error = remember { mutableStateOf<String?>(null) }
    var currentTokens = remember { mutableStateOf(authViewModel.getCurrentUserTokens()) }

    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Tokens") },
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
                .fillMaxWidth()
        ) {
            Text(
                "Your incoming exam date:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = examDateInput.value,
                onValueChange = {
                    examDateInput.value = it
                    error.value = null
                    message.value = null
                },
                label = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    try {
                        val examDate =
                            LocalDate.parse(examDateInput.value.trim(), formatter)
                        val today = LocalDate.now()
                        val oneMonthAhead = today.plusMonths(1)

                        if (examDate.isBefore(today) || examDate.isAfter(oneMonthAhead)) {
                            error.value = "Date must be within one month from today."
                            message.value = null
                        } else {
                            authViewModel.addTokensForCurrentUser(10)
                            currentTokens.value = authViewModel.getCurrentUserTokens()
                            message.value = "10 tokens added for your exam on $examDate!"
                            error.value = null
                        }
                    } catch (e: DateTimeParseException) {
                        error.value = "Please enter a valid date in format YYYY-MM-DD."
                        message.value = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm")
            }

            Spacer(modifier = Modifier.height(16.dp))

            error.value?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            message.value?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Current tokens: ${currentTokens.value}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
