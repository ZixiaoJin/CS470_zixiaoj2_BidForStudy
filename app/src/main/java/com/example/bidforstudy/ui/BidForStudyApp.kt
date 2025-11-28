package com.example.bidforstudy.ui

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bidforstudy.auth.AuthViewModel
import com.example.bidforstudy.auth.LoginScreen
import com.example.bidforstudy.auth.RegisterScreen

@Composable
fun BidForStudyApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                authViewModel = authViewModel,
                onRegisterSuccess = {
                    // after register go back to login
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                authViewModel = authViewModel,
                onAddTokensClick = {
                    navController.navigate("addTokens")
                },
                onDateSelected = { dateString ->
                    navController.navigate("availableRooms/$dateString")
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onOpenBidHistory = {
                    navController.navigate("bidHistory")
                },
                onOpenReservationHistory = {
                    navController.navigate("reservationHistory")
                },
                onOpenPendingBids = {
                    navController.navigate("pendingBids")
                }
            )
        }

        composable(
            route = "availableRooms/{date}",
            arguments = listOf(
                navArgument("date") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            AvailableRoomsScreen(
                date = date,
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("addTokens") {
            AddTokensScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bidHistory") {
            BidHistoryScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("reservationHistory") {
            ReservationHistoryScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onReservationClick = { reservation ->
                    val room = reservation.auctionKey.roomNumber
                    val date = reservation.auctionKey.reservationDate.toString()
                    val time = android.net.Uri.encode(reservation.auctionKey.timeRange)
                    val capacity = reservation.auctionKey.capacity
                    val amount = reservation.amount
                    navController.navigate("cancelReservation/$room/$date/$time/$capacity/$amount")
                }
            )
        }

        composable(
            route = "cancelReservation/{roomNumber}/{date}/{timeRange}/{capacity}/{amount}",
            arguments = listOf(
                navArgument("roomNumber") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
                navArgument("timeRange") { type = NavType.StringType },
                navArgument("capacity") { type = NavType.IntType },
                navArgument("amount") { type = NavType.IntType }
            )
        ) { entry ->
            val roomNumber = entry.arguments?.getString("roomNumber") ?: ""
            val date = entry.arguments?.getString("date") ?: ""
            val timeRange = entry.arguments?.getString("timeRange") ?: ""
            val capacity = entry.arguments?.getInt("capacity") ?: 1
            val amount = entry.arguments?.getInt("amount") ?: 0

            CancelReservationScreen(
                authViewModel = authViewModel,
                roomNumber = roomNumber,
                date = date,
                timeRange = timeRange,
                capacity = capacity,
                amount = amount,
                onBack = { navController.popBackStack() }
            )
        }

        composable("pendingBids") {
            PendingBidScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onOpenPendingGroupDetail = { pendingGroup ->
                    PendingGroupHolder.current = pendingGroup
                    navController.navigate("pendingGroupDetail")
                }
            )
        }

        composable("pendingGroupDetail") {
            val pg = PendingGroupHolder.current
            if (pg == null) {
                Text("No pending group selected.")
            } else {
                PendingGroupDetailScreen(
                    authViewModel = authViewModel,
                    pendingGroup = pg,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

