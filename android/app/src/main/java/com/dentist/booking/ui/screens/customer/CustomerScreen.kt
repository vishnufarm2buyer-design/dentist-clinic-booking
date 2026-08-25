package com.dentist.booking.ui.screens.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.Treatment
import com.dentist.booking.ui.screens.superadmin.SubscriptionBadge
import com.dentist.booking.util.DocumentGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: CustomerViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val clinics by viewModel.linkedClinics.collectAsState()
    val selectedClinic by viewModel.selectedClinic.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val treatments by viewModel.treatments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val success by viewModel.successMessage.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0=Home, 1=Book, 2=Bookings, 3=History, 4=Profile
    var showDropdown by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

    // Check selected clinic subscription status
    val isClinicInactive = selectedClinic != null &&
            (selectedClinic!!.subscriptionStatus == "expired" || selectedClinic!!.subscriptionStatus == "suspended")

    // Filter bookings by selected clinic
    val clinicBookings = bookings.filter { it.clinicId == selectedClinic?.id }
    // Filter treatments by selected clinic or show all
    var viewAllTreatments by remember { mutableStateOf(true) }
    val filteredTreatments = if (viewAllTreatments) {
        treatments
    } else {
        treatments.filter { it.clinicId == selectedClinic?.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showDropdown = true }
                    ) {
                        Text(selectedClinic?.name ?: "Select Clinic", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Clinic")
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        clinics.forEach { clinic ->
                            DropdownMenuItem(
                                text = { Text(clinic.name) },
                                onClick = {
                                    viewModel.selectClinic(clinic)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                },
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
            // Expired/Suspended clinic warning banner
            if (isClinicInactive && selectedClinic != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Alert", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clinic subscription is ${selectedClinic?.subscriptionStatus?.uppercase()}. New bookings are temporarily disabled.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

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

            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Home") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Book") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Bookings") })
                Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text("History") })
                Tab(selected = activeTab == 4, onClick = { activeTab = 4 }, text = { Text("Profile") })
            }

            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> CustomerHomeTab(selectedClinic)
                    1 -> CustomerBookTab(viewModel, selectedClinic, isClinicInactive)
                    2 -> CustomerBookingsTab(clinicBookings, onCancel = { viewModel.cancelBooking(it.id!!) })
                    3 -> CustomerHistoryTab(
                        treatments = filteredTreatments,
                        viewAll = viewAllTreatments,
                        onToggleView = { viewAllTreatments = it },
                        onShare = {
                            val reportText = DocumentGenerator.generateTreatmentReport("My Treatments", filteredTreatments)
                            DocumentGenerator.shareReport(context, "My_Treatments", reportText)
                        }
                    )
                    4 -> CustomerProfileTab(onResetPassword = { showChangePassword = true })
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

// Sub-components for Customer Screen

@Composable
fun CustomerHomeTab(selectedClinic: Clinic?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedClinic == null) {
            Text("You are not registered to any clinics. Contact your clinic administrator to link your profile.", color = Color.Gray, textAlign = TextAlign.Center)
        } else {
            Text("Welcome to", fontSize = 16.sp, color = Color.Gray)
            Text(selectedClinic.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Clinic Portal Instructions:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Book appointments from the 'Book' tab.", fontSize = 13.sp)
                    Text("2. Check confirmation status in 'Bookings'.", fontSize = 13.sp)
                    Text("3. View and download your complete treatment logs from the 'History' tab.", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CustomerBookTab(viewModel: CustomerViewModel, selectedClinic: Clinic?, isInactive: Boolean) {
    var dateInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }

    // Prepopulate date as today's date
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateInput = sdf.format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Book Appointment", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Request a booking date and describe your dental concern. The clinic staff will assign a doctor to review your request.", fontSize = 13.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            label = { Text("Requested Date (YYYY-MM-DD)") },
            placeholder = { Text("YYYY-MM-DD") },
            enabled = !isInactive && selectedClinic != null,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = reasonInput,
            onValueChange = { reasonInput = it },
            label = { Text("Reason for Booking") },
            placeholder = { Text("e.g. Toothache checkup, regular cleaning") },
            enabled = !isInactive && selectedClinic != null,
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedClinic != null) {
                    viewModel.bookAppointment(selectedClinic.id!!, dateInput, reasonInput)
                    reasonInput = ""
                }
            },
            enabled = !isInactive && selectedClinic != null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Book Appointment", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CustomerBookingsTab(bookings: List<Booking>, onCancel: (Booking) -> Unit) {
    if (bookings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No appointments booked for this clinic.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookings) { booking ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date: ${booking.requestedDate}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            SubscriptionBadge(status = booking.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Reason: ${booking.reason}", fontSize = 13.sp)

                        if (booking.status == "accepted" && !booking.doctorName.isNullOrEmpty()) {
                            Text("Assigned Dentist: ${booking.doctorName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        } else if (booking.status == "rejected" && !booking.rejectionReason.isNullOrEmpty()) {
                            Text("Rejection Reason: ${booking.rejectionReason}", fontSize = 13.sp, color = Color.Red)
                        }

                        if (booking.status == "pending" || booking.status == "accepted") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onCancel(booking) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Appointment", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerHistoryTab(
    treatments: List<Treatment>,
    viewAll: Boolean,
    onToggleView: (Boolean) -> Unit,
    onShare: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewAll, onCheckedChange = onToggleView)
                Text("All Clinics", fontSize = 13.sp)
            }
            Button(onClick = onShare, enabled = treatments.isNotEmpty()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Report")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (treatments.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No treatment records found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(treatments) { tr ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tr.visitDate, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(tr.clinicName ?: "Clinic", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Doctor: ${tr.doctorName ?: "N/A"}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(tr.notes, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerProfileTab(onResetPassword: () -> Unit) {
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
                Text("🧑", fontSize = 36.sp)
            }
        }

        Text("My Customer Account", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Standard Client Profile", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onResetPassword,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Account Password")
        }
    }
}
