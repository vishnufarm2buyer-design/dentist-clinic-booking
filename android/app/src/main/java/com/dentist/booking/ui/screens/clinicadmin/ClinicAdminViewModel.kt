package com.dentist.booking.ui.screens.clinicadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.Treatment
import com.dentist.booking.data.model.User
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.BookingRepository
import com.dentist.booking.data.repository.ClinicRepository
import com.dentist.booking.data.repository.TreatmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClinicAdminViewModel(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val clinicRepository: ClinicRepository,
    private val treatmentRepository: TreatmentRepository,
    private val clinicId: String
) : ViewModel() {

    private val _clinic = MutableStateFlow<Clinic?>(null)
    val clinic: StateFlow<Clinic?> = _clinic.asStateFlow()

    private val _bookings = MutableStateFlow<List<Booking>>(emptyList())
    val bookings: StateFlow<List<Booking>> = _bookings.asStateFlow()

    private val _doctors = MutableStateFlow<List<User>>(emptyList())
    val doctors: StateFlow<List<User>> = _doctors.asStateFlow()

    private val _customers = MutableStateFlow<List<User>>(emptyList())
    val customers: StateFlow<List<User>> = _customers.asStateFlow()

    private val _selectedPatientTreatments = MutableStateFlow<List<Treatment>>(emptyList())
    val selectedPatientTreatments: StateFlow<List<Treatment>> = _selectedPatientTreatments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Load Clinic
                val c = clinicRepository.getClinicById(clinicId)
                _clinic.value = c

                // Load Bookings
                val bList = bookingRepository.getBookingsForClinic(clinicId)
                _bookings.value = bList

                // Load Doctors
                val docList = authRepository.getDoctors(clinicId)
                _doctors.value = docList

                // Load Customers
                val custList = authRepository.getCustomers(clinicId)
                _customers.value = custList
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load clinic admin data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    fun acceptBooking(bookingId: String, doctorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.acceptBooking(bookingId, doctorId)
                _successMessage.value = "Booking accepted and doctor assigned successfully."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to accept booking"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun rejectBooking(bookingId: String, reason: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.rejectBooking(bookingId, reason)
                _successMessage.value = "Booking rejected successfully."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to reject booking"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addDoctor(name: String, phone: String, passwordPlain: String, specialization: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                authRepository.registerDoctor(name, phone, passwordPlain, specialization, clinicId)
                _successMessage.value = "Doctor registered successfully."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to register doctor"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addCustomer(name: String, phone: String, passwordPlain: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                authRepository.registerCustomer(name, phone, passwordPlain, clinicId)
                _successMessage.value = "Customer added to clinic successfully."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to add customer"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPatientTreatmentHistory(patientId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Scoped strictly to this clinic
                val treatments = treatmentRepository.getClinicPatientTreatments(clinicId, patientId)
                _selectedPatientTreatments.value = treatments
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to retrieve treatment history"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changePassword(newPasswordPlain: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = authRepository.changePassword(newPasswordPlain)
                if (success) {
                    _successMessage.value = "Password updated successfully."
                } else {
                    _errorMessage.value = "Failed to update password."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update password."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getPendingCount(): Int = _bookings.value.count { it.status == "pending" }
    
    fun getTodayBookings(): Int {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return _bookings.value.count { it.requestedDate == todayStr }
    }
}
