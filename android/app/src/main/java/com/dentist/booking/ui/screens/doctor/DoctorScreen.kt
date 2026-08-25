package com.dentist.booking.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.User
import com.dentist.booking.ui.screens.clinicadmin.AdminHistoryTab
import com.dentist.booking.ui.screens.clinicadmin.PatientHistoryView
import com.dentist.booking.ui.screens.superadmin.SubscriptionBadge
import com.dentist.booking.util.DocumentGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorScreen(
    viewModel: DoctorViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val bookings by viewModel.assignedBookings.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val treatments by viewModel.selectedPatientTreatments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val success by viewModel.successMessage.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Bookings, 1 = Patient History, 2 = Profile
    var showCompleteVisitDialog by remember { mutableStateOf<Booking?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var selectedPatientForHistory by remember { mutableStateOf<User?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Portal", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Success / Error indicator bar
            if (success != null || error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (success != null) Color(0xFFD4EDDA) else MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { viewModel.clearMessages() }
                ) {
                    Text(
                        text = success ?: error ?: "",
                        color = if (success != null) Color(0xFF155724) else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            if (selectedPatientForHistory != null) {
                // Patient history view override (identical to Admin's RLS scoping)
                PatientHistoryView(
                    patient = selectedPatientForHistory!!,
                    treatments = treatments,
                    onBack = { selectedPatientForHistory = null },
                    onDownload = {
                        val report = DocumentGenerator.generateTreatmentReport(selectedPatientForHistory!!.name, treatments)
                        DocumentGenerator.shareReport(context, selectedPatientForHistory!!.name, report)
                    }
                )
            } else {
                TabRow(selectedTabIndex = activeTab) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("My Bookings") })
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Patients") })
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Profile") })
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (activeTab) {
                        0 -> DoctorBookingsTab(bookings, onCompleteVisitClick = { showCompleteVisitDialog = it })
                        1 -> AdminHistoryTab(patients) {
                            selectedPatientForHistory = it
                            viewModel.loadPatientTreatmentHistory(it.id!!)
                        }
                        2 -> DoctorProfileTab(viewModel, onResetPassword = { showChangePassword = true })
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    // Complete Visit Dialog (Notes Required)
    if (showCompleteVisitDialog != null) {
        var notes by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCompleteVisitDialog = null },
            title = { Text("Complete Visit Notes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide mandatory treatment notes for ${showCompleteVisitDialog!!.customerName}:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            inputError = null
                        },
                        label = { Text("Treatment Notes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    inputError?.let {
                        Text(it, color = Color.Red, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (notes.trim().isEmpty()) {
                            inputError = "Notes cannot be empty"
                        } else {
                            viewModel.completeVisit(
                                bookingId = showCompleteVisitDialog!!.id!!,
                                customerId = showCompleteVisitDialog!!.customerId,
                                notes = notes.trim()
                            )
                            showCompleteVisitDialog = null
                        }
                    }
                ) {
                    Text("Save & Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteVisitDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Reset Password Dialog
    if (showChangePassword) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePassword = false },
            title = { Text("Change Password") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isNotEmpty()) {
                            viewModel.changePassword(passwordInput)
                            showChangePassword = false
                        }
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePassword = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DoctorBookingsTab(
    bookings: List<Booking>,
    onCompleteVisitClick: (Booking) -> Unit
) {
    val activeBookings = bookings.filter { it.status == "accepted" }
    val completedBookings = bookings.filter { it.status == "completed" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Assigned Active Appointments", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }

        if (activeBookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("No pending active appointments assigned.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(activeBookings) { booking ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(booking.customerName ?: "Patient", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            SubscriptionBadge(status = booking.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Requested Date: ${booking.requestedDate}", fontSize = 13.sp)
                        Text("Reason: ${booking.reason}", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onCompleteVisitClick(booking) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mark Visit Completed")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Completed Visits History", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Gray)
        }

        if (completedBookings.isEmpty()) {
            item {
                Text("No completed visit records found.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            items(completedBookings) { booking ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(booking.customerName ?: "Patient", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            SubscriptionBadge(status = booking.status)
                        }
                        Text("Completed Date: ${booking.requestedDate}", fontSize = 13.sp)
                        Text("Reason: ${booking.reason}", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorProfileTab(viewModel: DoctorViewModel, onResetPassword: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🩺", fontSize = 36.sp)
            }
        }

        Text("Doctor Account Info", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Clinical role: Dentist / Surgeon", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onResetPassword,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Account Password")
        }

        Button(
            onClick = { viewModel.loadData() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Assigned Data", color = Color.Black)
        }
    }
}
