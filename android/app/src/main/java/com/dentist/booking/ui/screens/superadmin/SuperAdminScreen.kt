package com.dentist.booking.ui.screens.superadmin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentist.booking.data.model.Clinic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminScreen(
    viewModel: SuperAdminViewModel,
    onLogout: () -> Unit
) {
    val clinics by viewModel.clinics.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val selectedClinic by viewModel.selectedClinic.collectAsState()
    val history by viewModel.subscriptionHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Clinics
    var showOnboardDialog by remember { mutableStateOf(false) }

    val filteredClinics = clinics.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Super Admin Portal", fontWeight = FontWeight.Bold) },
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
        },
        floatingActionButton = {
            if (activeTab == 1 && selectedClinic == null) {
                FloatingActionButton(
                    onClick = { showOnboardDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Onboard Clinic", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedClinic != null) {
                // Clinic Detail view (override layout)
                ClinicDetailView(
                    clinic = selectedClinic!!,
                    history = history,
                    onBack = { viewModel.selectClinic(null) },
                    onUpdateSubscription = { action, status, plan, start, end, notes ->
                        viewModel.updateSubscription(selectedClinic!!.id!!, action, status, plan, start, end, notes)
                    }
                )
            } else {
                // Normal Tabbed Navigation (Dashboard / Clinics List)
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Dashboard") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Clinics (${clinics.size})") }
                        )
                    }

                    if (error != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp
                            )
                        }
                    }

                    when (activeTab) {
                        0 -> DashboardView(stats = stats)
                        1 -> ClinicsListView(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            clinics = filteredClinics,
                            onClinicClick = { viewModel.selectClinic(it) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Onboarding Form Dialog
    if (showOnboardDialog) {
        OnboardClinicDialog(
            onDismiss = { showOnboardDialog = false },
            onSubmit = { name, adminName, adminPhone, adminPassword, plan, start, end, status ->
                viewModel.onboardClinic(
                    name = name,
                    adminName = adminName,
                    adminPhone = adminPhone,
                    adminPasswordPlain = adminPassword,
                    plan = plan,
                    startDate = start,
                    endDate = end,
                    status = status,
                    onSuccess = { showOnboardDialog = false }
                )
            }
        )
    }
}

// 1. Dashboard View
@Composable
fun DashboardView(stats: SuperAdminStats) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Platform Statistics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Total Clinics", stats.total.toString(), MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                StatCard("Active", stats.active.toString(), Color(0xFFD4EDDA), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Free Trials", stats.trial.toString(), Color(0xFFCCE5FF), Modifier.weight(1f))
                StatCard("Suspended", stats.suspended.toString(), Color(0xFFF8D7DA), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("Expired", stats.expired.toString(), Color(0xFFFFF3CD), Modifier.weight(0.5f))
                Spacer(modifier = Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, backgroundColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = label, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

// 2. Clinics Directory View
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicsListView(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    clinics: List<Clinic>,
    onClinicClick: (Clinic) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search Clinics...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (clinics.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No clinics found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(clinics) { clinic ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClinicClick(clinic) },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(clinic.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Plan: ${clinic.subscriptionPlan ?: "None"}", fontSize = 12.sp, color = Color.Gray)
                            }
                            SubscriptionBadge(status = clinic.subscriptionStatus)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionBadge(status: String) {
    val (bgColor, textColor) = when (status) {
        "active" -> Pair(Color(0xFFD4EDDA), Color(0xFF155724))
        "trial" -> Pair(Color(0xFFCCE5FF), Color(0xFF004085))
        "expired" -> Pair(Color(0xFFFFF3CD), Color(0xFF856404))
        "suspended" -> Pair(Color(0xFFF8D7DA), Color(0xFF721C24))
        else -> Pair(Color.LightGray, Color.DarkGray)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.uppercase(Locale.getDefault()),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// 3. Clinic Details & Audit Timeline View
@Composable
fun ClinicDetailView(
    clinic: Clinic,
    history: List<SubscriptionLog>,
    onBack: () -> Unit,
    onUpdateSubscription: (action: String, status: String, plan: String?, start: String?, end: String?, notes: String?) -> Unit
) {
    var showRenewDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onBack() }
                .padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to list", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Text(clinic.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Subscription overview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Status", fontWeight = FontWeight.SemiBold)
                    SubscriptionBadge(status = clinic.subscriptionStatus)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current Plan", fontWeight = FontWeight.SemiBold)
                    Text(clinic.subscriptionPlan ?: "None")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start Date", fontWeight = FontWeight.SemiBold)
                    Text(clinic.subscriptionStartDate ?: "N/A")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("End Date", fontWeight = FontWeight.SemiBold)
                    Text(clinic.subscriptionEndDate ?: "N/A")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription Action Grid
        Text("Modify Subscription", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onUpdateSubscription("activate", "active", null, null, null, "Clinic manually activated.") },
                enabled = clinic.subscriptionStatus != "active",
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Activate", fontSize = 12.sp, color = Color.White)
            }

            Button(
                onClick = { onUpdateSubscription("suspend", "suspended", null, null, null, "Clinic manually suspended.") },
                enabled = clinic.subscriptionStatus != "suspended",
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Suspend", fontSize = 12.sp, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onUpdateSubscription("expire", "expired", null, null, null, "Subscription manually expired.") },
                enabled = clinic.subscriptionStatus != "expired",
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Expire", fontSize = 12.sp, color = Color.Black)
            }

            Button(
                onClick = { showRenewDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            ) {
                Text("Renew / Change", fontSize = 12.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Subscription Audit History Timeline
        Text("Subscription Audit History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text("No subscription changes recorded.", color = Color.Gray, fontSize = 14.sp)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.action.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                Text(log.createdAt?.substring(0, 10) ?: "", fontSize = 11.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Plan: ${log.plan ?: "N/A"} (${log.startDate ?: "N/A"} to ${log.endDate ?: "N/A"})", fontSize = 13.sp)
                            log.notes?.let {
                                Text("Notes: $it", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenewDialog) {
        RenewSubscriptionDialog(
            currentPlan = clinic.subscriptionPlan ?: "Standard",
            onDismiss = { showRenewDialog = false },
            onSubmit = { plan, start, end, notes ->
                onUpdateSubscription("renew", "active", plan, start, end, notes)
                showRenewDialog = false
            }
        )
    }
}

// 4. Onboard Clinic Dialog Form
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardClinicDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, adminName: String, adminPhone: String, adminPassword: String, plan: String, start: String, end: String, status: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var adminPhone by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("Standard") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("trial") }

    // Prepopulate default dates (Today to 1 month from now)
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        start = sdf.format(cal.time)
        cal.add(Calendar.MONTH, 1)
        end = sdf.format(cal.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onboard New Clinic") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Clinic Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it },
                        label = { Text("Clinic Admin Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = adminPhone,
                        onValueChange = { adminPhone = it },
                        label = { Text("Clinic Admin Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = { Text("Clinic Admin Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = plan,
                        onValueChange = { plan = it },
                        label = { Text("Subscription Plan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status:")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = status == "trial", onClick = { status = "trial" })
                            Text("Trial")
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(selected = status == "active", onClick = { status = "active" })
                            Text("Active")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && adminName.isNotEmpty() && adminPhone.isNotEmpty() && adminPassword.isNotEmpty()) {
                        onSubmit(name, adminName, adminPhone, adminPassword, plan, start, end, status)
                    }
                }
            ) {
                Text("Onboard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// 5. Renew / Change Subscription Dialog Form
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewSubscriptionDialog(
    currentPlan: String,
    onDismiss: () -> Unit,
    onSubmit: (plan: String, start: String, end: String, notes: String) -> Unit
) {
    var plan by remember { mutableStateOf(currentPlan) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        start = sdf.format(cal.time)
        cal.add(Calendar.YEAR, 1) // Default renew for 1 year
        end = sdf.format(cal.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew / Change Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = plan,
                    onValueChange = { plan = it },
                    label = { Text("Subscription Plan") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("End Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Change Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (plan.isNotEmpty() && start.isNotEmpty() && end.isNotEmpty()) {
                        onSubmit(plan, start, end, notes)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
