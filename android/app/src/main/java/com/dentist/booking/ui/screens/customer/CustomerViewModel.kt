package com.dentist.booking.ui.screens.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.Treatment
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.BookingRepository
import com.dentist.booking.data.repository.ClinicRepository
import com.dentist.booking.data.repository.TreatmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerViewModel(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val clinicRepository: ClinicRepository,
    private val treatmentRepository: TreatmentRepository,
    private val customerId: String
) : ViewModel() {

    private val _linkedClinics = MutableStateFlow<List<Clinic>>(emptyList())
    val linkedClinics: StateFlow<List<Clinic>> = _linkedClinics.asStateFlow()

    private val _selectedClinic = MutableStateFlow<Clinic?>(null)
    val selectedClinic: StateFlow<Clinic?> = _selectedClinic.asStateFlow()

    private val _bookings = MutableStateFlow<List<Booking>>(emptyList())
    val bookings: StateFlow<List<Booking>> = _bookings.asStateFlow()

    private val _treatments = MutableStateFlow<List<Treatment>>(emptyList())
    val treatments: StateFlow<List<Treatment>> = _treatments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Fetch linked clinics
                val clinics = clinicRepository.getClinicsForCustomer(customerId)
                _linkedClinics.value = clinics
                if (clinics.isNotEmpty() && _selectedClinic.value == null) {
                    _selectedClinic.value = clinics.first()
                }

                // Fetch bookings
                val bookingList = bookingRepository.getBookingsForCustomer(customerId)
                _bookings.value = bookingList

                // Fetch treatment history
                loadTreatmentHistory()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load customer profile data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectClinic(clinic: Clinic) {
        _selectedClinic.value = clinic
        loadTreatmentHistory()
    }

    fun loadTreatmentHistory() {
        viewModelScope.launch {
            try {
                // Fetch customer treatment history across all or selected clinic
                val list = treatmentRepository.getTreatmentsForCustomer(customerId, null)
                _treatments.value = list
            } catch (e: Exception) {
                // Fetch logs silently
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    fun bookAppointment(clinicId: String, requestedDate: String, reason: String) {
        if (reason.trim().isEmpty() || requestedDate.trim().isEmpty()) {
            _errorMessage.value = "Date and reason are required fields."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                bookingRepository.createBooking(
                    clinicId = clinicId,
                    customerId = customerId,
                    requestedDate = requestedDate,
                    reason = reason.trim()
                )
                _successMessage.value = "Booking request submitted successfully."
                loadInitialData()
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to submit booking"
                _errorMessage.value = if (msg.contains("23505")) {
                    "You already have a pending booking request for this clinic."
                } else if (msg.contains("subscription")) {
                    "Booking failed. The clinic subscription is inactive."
                } else {
                    msg
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                bookingRepository.cancelBooking(bookingId)
                _successMessage.value = "Booking request cancelled."
                loadInitialData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to cancel booking"
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
