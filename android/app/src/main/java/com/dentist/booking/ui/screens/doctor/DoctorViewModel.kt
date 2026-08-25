package com.dentist.booking.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.Treatment
import com.dentist.booking.data.model.User
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.BookingRepository
import com.dentist.booking.data.repository.TreatmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DoctorViewModel(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val treatmentRepository: TreatmentRepository,
    private val doctorId: String,
    private val clinicId: String
) : ViewModel() {

    private val _assignedBookings = MutableStateFlow<List<Booking>>(emptyList())
    val assignedBookings: StateFlow<List<Booking>> = _assignedBookings.asStateFlow()

    private val _patients = MutableStateFlow<List<User>>(emptyList())
    val patients: StateFlow<List<User>> = _patients.asStateFlow()

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
                // Fetch assigned bookings
                val bookings = bookingRepository.getBookingsForDoctor(doctorId)
                _assignedBookings.value = bookings.sortedBy { it.requestedDate }

                // Fetch clinic patients (for search and history lookup)
                val patientsList = authRepository.getCustomers(clinicId)
                _patients.value = patientsList
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load doctor dashboard data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    fun completeVisit(bookingId: String, customerId: String, notes: String) {
        if (notes.trim().isEmpty()) {
            _errorMessage.value = "Treatment notes are required to complete a visit"
            return
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                treatmentRepository.createTreatmentAndCompleteBooking(
                    bookingId = bookingId,
                    customerId = customerId,
                    clinicId = clinicId,
                    doctorId = doctorId,
                    notes = notes,
                    visitDate = todayStr
                )
                _successMessage.value = "Visit completed. Treatment notes saved successfully."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to complete visit"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPatientTreatmentHistory(patientId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Clinic scoped (Doctor cannot access other clinics' logs)
                val list = treatmentRepository.getClinicPatientTreatments(clinicId, patientId)
                _selectedPatientTreatments.value = list
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
}
