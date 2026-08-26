package com.dentist.booking.ui.screens.clinicadmin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.User
import com.dentist.booking.ui.screens.superadmin.SubscriptionBadge
import com.dentist.booking.ui.screens.superadmin.StatCard
import com.dentist.booking.util.DocumentGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicAdminScreen(
    viewModel: ClinicAdminViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val clinic by viewModel.clinic.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val doctors by viewModel.doctors.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val treatments by viewModel.selectedPatientTreatments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val success by viewModel.successMessage.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    // 0=Dashboard, 1=Bookings, 2=Doctors, 3=Customers, 4=Treatments, 5=Profile

    var showAddDoc by remember { mutableStateOf(false) }
    var showAddCust by remember { mutableStateOf(false) }
    var showAssignDocBooking by remember { mutableStateOf<Booking?>(null) }
    var showRejectBooking by remember { mutableStateOf<Booking?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var selectedCustomerForHistory by remember { mutableStateOf<User?>(null) }

    // Check if subscription is inactive
    val isSubscriptionInactive = clinic != null && 
            (clinic!!.subscriptionStatus == "expired" || clinic!!.subscriptionStatus == "suspended")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clinic?.name ?: "Clinic Portal", fontWeight = FontWeight.Bold) },
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
            // Subscription warning banner
            if (isSubscriptionInactive) {
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
                            text = "Warning: Clinic subscription is ${clinic?.subscriptionStatus?.uppercase()}. Customers cannot book new appointments.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Quick SnackBar style success/error indicators
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

            if (selectedCustomerForHistory != null) {
                // Patient history view override
                PatientHistoryView(
                    patient = selectedCustomerForHistory!!,
                    treatments = treatments,
                    onBack = { selectedCustomerForHistory = null },
                    onDownload = {
                        val report = DocumentGenerator.generateTreatmentReport(selectedCustomerForHistory!!.name, treatments)
                        DocumentGenerator.shareReport(context, selectedCustomerForHistory!!.name, report)
                    }
                )
            } else {
                ScrollableTabRow(selectedTabIndex = activeTab, modifier = Modifier.fillMaxWidth()) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Dash") })
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Bookings") })
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Doctors") })
                    Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text("Patients") })
                    Tab(selected = activeTab == 4, onClick = { activeTab = 4 }, text = { Text("History") })
                    Tab(selected = activeTab == 5, onClick = { activeTab = 5 }, text = { Text("Profile") })
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (activeTab) {
                        0 -> AdminDashboardTab(viewModel)
                        1 -> AdminBookingsTab(bookings, doctors, onAccept = { showAssignDocBooking = it }, onReject = { showRejectBooking = it })
                        2 -> AdminDoctorsTab(doctors, onAddClick = { showAddDoc = true })
                        3 -> AdminCustomersTab(customers, onAddClick = { showAddCust = true })
                        4 -> AdminHistoryTab(customers) {
                            selectedCustomerForHistory = it
                            viewModel.loadPatientTreatmentHistory(it.id!!)
                        }
                        5 -> AdminProfileTab(viewModel, clinic, onResetPassword = { showChangePassword = true })
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

    // Assign Doctor to booking Dialog
    if (showAssignDocBooking != null) {
        var selectedDocId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAssignDocBooking = null },
            title = { Text("Assign Doctor") },
            text = {
                Column {
                    Text("Select a doctor to assign to this appointment:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (doctors.isEmpty()) {
                        Text("No doctors registered. Create doctors first.", color = Color.Red, fontSize = 13.sp)
                    } else {
                        doctors.forEach { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDocId = doc.id!! }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedDocId == doc.id, onClick = { selectedDocId = doc.id!! })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${doc.name} (${doc.specialization})", fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedDocId.isNotEmpty()) {
                            viewModel.acceptBooking(showAssignDocBooking!!.id!!, selectedDocId)
                            showAssignDocBooking = null
                        }
                    },
                    enabled = selectedDocId.isNotEmpty()
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDocBooking = null }) { Text("Cancel") }
            }
        )
    }

    // Reject booking Dialog
    if (showRejectBooking != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRejectBooking = null },
            title = { Text("Reject Booking") },
            text = {
                Column {
                    Text("Provide an optional reason for rejecting this booking request:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectBooking(showRejectBooking!!.id!!, reason.trim().ifEmpty { null })
                        showRejectBooking = null
                    }
                ) {
                    Text("Confirm Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectBooking = null }) { Text("Cancel") }
            }
        )
    }

    // Add Doctor Dialog
    if (showAddDoc) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var pwd by remember { mutableStateOf("") }
        var spec by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDoc = false },
            title = { Text("Register New Doctor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Doctor Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("Specialization") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty() && phone.isNotEmpty() && pwd.isNotEmpty()) {
                            viewModel.addDoctor(name, phone, pwd, spec)
                            showAddDoc = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDoc = false }) { Text("Cancel") }
            }
        )
    }

    // Add Customer Dialog
    if (showAddCust) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var pwd by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCust = false },
            title = { Text("Add Customer Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("If a customer already exists globally (searched by phone number), they will simply be linked to this clinic. Otherwise, a new account will be created.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("Password (for new profiles)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty() && phone.isNotEmpty() && pwd.isNotEmpty()) {
                            viewModel.addCustomer(name, phone, pwd)
                            showAddCust = false
                        }
                    }
                ) { Text("Link / Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCust = false }) { Text("Cancel") }
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

// Sub-components for Clinic Admin Screen

@Composable
fun AdminDashboardTab(viewModel: ClinicAdminViewModel) {
    val doctors by viewModel.doctors.collectAsState()
    val customers by viewModel.customers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Clinic Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Pending Bookings", viewModel.getPendingCount().toString(), Color(0xFFFFC107).copy(alpha = 0.2f), Modifier.weight(1f))
                StatCard("Today's Appointments", viewModel.getTodayBookings().toString(), Color(0xFFCCE5FF), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Doctors Active", doctors.size.toString(), Color(0xFFD4EDDA), Modifier.weight(1f))
                StatCard("Registered Patients", customers.size.toString(), Color.LightGray.copy(alpha = 0.3f), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AdminBookingsTab(
    bookings: List<Booking>,
    doctors: List<User>,
    onAccept: (Booking) -> Unit,
    onReject: (Booking) -> Unit
) {
    if (bookings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No bookings registered for this clinic.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookings) { booking ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
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

                        if (booking.status == "accepted") {
                            Text("Assigned Doctor: ${booking.doctorName ?: "Unresolved"}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        } else if (booking.status == "rejected" && !booking.rejectionReason.isNullOrEmpty()) {
                            Text("Rejection Reason: ${booking.rejectionReason}", fontSize = 13.sp, color = Color.Red)
                        }

                        if (booking.status == "pending") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onAccept(booking) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Accept & Assign", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { onReject(booking) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject Request", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDoctorsTab(doctors: List<User>, onAddClick: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add Doctor", tint = Color.White)
            }
        }
    ) { p ->
        Box(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            if (doctors.isEmpty()) {
                Text("No doctors registered. Press '+' to register a doctor.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(doctors) { doc ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(doc.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Phone: ${doc.phone}", fontSize = 13.sp)
                                Text("Specialization: ${doc.specialization ?: "General Dentist"}", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminCustomersTab(customers: List<User>, onAddClick: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Link Customer", tint = Color.White)
            }
        }
    ) { p ->
        Box(modifier = Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            if (customers.isEmpty()) {
                Text("No patients linked to this clinic. Press '+' to register.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(customers) { cust ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Phone: ${cust.phone}", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminHistoryTab(customers: List<User>, onSelectPatient: (User) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select patient to view clinical records:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        if (customers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No patients registered to this clinic.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(customers) { cust ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPatient(cust) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Phone: ${cust.phone}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "View History")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminProfileTab(viewModel: ClinicAdminViewModel, clinic: Clinic?, onResetPassword: () -> Unit) {
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
                Text("⚙️", fontSize = 36.sp)
            }
        }

        Text(clinic?.name ?: "Clinic Portal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Role: Clinic Administrator", fontSize = 14.sp, color = Color.Gray)

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
            Text("Refresh Clinic Data", color = Color.Black)
        }
    }
}

// 6. Detailed Patient History View (Isolation compliant)
@Composable
fun PatientHistoryView(
    patient: User,
    treatments: List<com.dentist.booking.data.model.Treatment>,
    onBack: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onBack() }
                .padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to patients list", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(patient.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Phone: ${patient.phone}", fontSize = 13.sp, color = Color.Gray)
            }
            Button(onClick = onDownload, enabled = treatments.isNotEmpty()) {
                Icon(Icons.Default.Share, contentDescription = "Share/Download")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Clinical Logs (Clinic Scoped)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        if (treatments.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No treatment logs recorded for this patient at this clinic.", color = Color.Gray, textAlign = TextAlign.Center)
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
                                Text("Visit Date: ${tr.visitDate}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Doc: ${tr.doctorName ?: "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(tr.notes, fontSize = 13.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}
