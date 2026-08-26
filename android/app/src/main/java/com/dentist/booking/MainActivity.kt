package com.dentist.booking

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.sp
import com.dentist.booking.data.model.AppVersion
import com.dentist.booking.ui.screens.clinicadmin.ClinicAdminScreen
import com.dentist.booking.ui.screens.clinicadmin.ClinicAdminViewModel
import com.dentist.booking.ui.screens.customer.CustomerScreen
import com.dentist.booking.ui.screens.customer.CustomerViewModel
import com.dentist.booking.ui.screens.doctor.DoctorScreen
import com.dentist.booking.ui.screens.doctor.DoctorViewModel
import com.dentist.booking.ui.screens.login.LoginScreen
import com.dentist.booking.ui.screens.login.LoginViewModel
import com.dentist.booking.ui.screens.superadmin.SuperAdminScreen
import com.dentist.booking.ui.screens.superadmin.SuperAdminViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        val context = LocalContext.current
        val app = context.applicationContext as DentistApp

        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val securePreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 1. Request POST_NOTIFICATIONS permission for Android 13+
        var hasNotificationPermission by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasNotificationPermission = isGranted
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. APK Update Engine Check
        var updateAvailableInfo by remember { mutableStateOf<AppVersion?>(null) }
        val currentVersionCode = 1 // Matches app/build.gradle.kts versionCode

        LaunchedEffect(Unit) {
            try {
                val latest = app.appUpdateRepository.getLatestAppVersion()
                if (latest != null && latest.versionCode > currentVersionCode) {
                    updateAvailableInfo = latest
                }
            } catch (e: Exception) {
                // Fail update queries silently to avoid blocking standard app usage
            }
        }

        // Check if there is a cached user session to restore dashboard view automatically
        val currentUserState by app.authRepository.currentUser.collectAsState()
        val startDestination = if (currentUserState != null) {
            getDashboardRouteForRole(currentUserState!!.role)
        } else {
            "login"
        }

        NavHost(navController = navController, startDestination = startDestination) {
            
            composable("login") {
                val loginVm: LoginViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return LoginViewModel(app.authRepository, app.deviceTokenRepository, securePreferences) as T
                        }
                    }
                )
                LoginScreen(
                    viewModel = loginVm,
                    onLoginSuccess = { user ->
                        val route = getDashboardRouteForRole(user.role)
                        navController.navigate(route) {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable("super_admin") {
                val superAdminVm: SuperAdminViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val superAdminId = app.authRepository.currentUser.value?.id ?: ""
                            return SuperAdminViewModel(app.clinicRepository, superAdminId) as T
                        }
                    }
                )
                SuperAdminScreen(
                    viewModel = superAdminVm,
                    onLogout = {
                        app.authRepository.logout()
                        navController.navigate("login") {
                            popUpTo("super_admin") { inclusive = true }
                        }
                    }
                )
            }

            composable("clinic_admin") {
                val clinicAdminVm: ClinicAdminViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val clinicId = app.authRepository.currentUser.value?.clinicId ?: ""
                            return ClinicAdminViewModel(
                                app.authRepository,
                                app.bookingRepository,
                                app.clinicRepository,
                                app.treatmentRepository,
                                clinicId
                            ) as T
                        }
                    }
                )
                ClinicAdminScreen(
                    viewModel = clinicAdminVm,
                    onLogout = {
                        app.authRepository.logout()
                        navController.navigate("login") {
                            popUpTo("clinic_admin") { inclusive = true }
                        }
                    }
                )
            }

            composable("doctor") {
                val doctorVm: DoctorViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val doctorId = app.authRepository.currentUser.value?.id ?: ""
                            val clinicId = app.authRepository.currentUser.value?.clinicId ?: ""
                            return DoctorViewModel(
                                app.authRepository,
                                app.bookingRepository,
                                app.treatmentRepository,
                                doctorId,
                                clinicId
                            ) as T
                        }
                    }
                )
                DoctorScreen(
                    viewModel = doctorVm,
                    onLogout = {
                        app.authRepository.logout()
                        navController.navigate("login") {
                            popUpTo("doctor") { inclusive = true }
                        }
                    }
                )
            }

            composable("customer") {
                val customerVm: CustomerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val customerId = app.authRepository.currentUser.value?.id ?: ""
                            return CustomerViewModel(
                                app.authRepository,
                                app.bookingRepository,
                                app.clinicRepository,
                                app.treatmentRepository,
                                customerId
                            ) as T
                        }
                    }
                )
                CustomerScreen(
                    viewModel = customerVm,
                    onLogout = {
                        app.authRepository.logout()
                        navController.navigate("login") {
                            popUpTo("customer") { inclusive = true }
                        }
                    }
                )
            }
        }

        // Show update dialog if available
        updateAvailableInfo?.let { appVersion ->
            AlertDialog(
                onDismissRequest = {
                    if (!appVersion.forceUpdate) {
                        updateAvailableInfo = null
                    }
                },
                title = { Text("App Update Available") },
                text = {
                    Text(
                        "A new version (${appVersion.versionName}) of the application is available on GitHub Releases.\n\nRelease Notes:\n${appVersion.releaseNotes}",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appVersion.apkUrl))
                            context.startActivity(intent)
                            if (appVersion.forceUpdate) {
                                (context as Activity).finish()
                            } else {
                                updateAvailableInfo = null
                            }
                        }
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    if (!appVersion.forceUpdate) {
                        TextButton(onClick = { updateAvailableInfo = null }) {
                            Text("Later")
                        }
                    }
                }
            )
        }
    }

    private fun getDashboardRouteForRole(role: String): String {
        return when (role) {
            "super_admin" -> "super_admin"
            "clinic_admin" -> "clinic_admin"
            "doctor" -> "doctor"
            "customer" -> "customer"
            else -> "login"
        }
    }
}
