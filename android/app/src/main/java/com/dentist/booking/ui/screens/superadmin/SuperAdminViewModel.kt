package com.dentist.booking.ui.screens.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.SubscriptionLog
import com.dentist.booking.data.repository.ClinicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SuperAdminStats(
    val total: Int = 0,
    val active: Int = 0,
    val trial: Int = 0,
    val expired: Int = 0,
    val suspended: Int = 0
)

class SuperAdminViewModel(
    private val clinicRepository: ClinicRepository,
    private val superAdminId: String
) : ViewModel() {

    private val _clinics = MutableStateFlow<List<Clinic>>(emptyList())
    val clinics: StateFlow<List<Clinic>> = _clinics.asStateFlow()

    private val _stats = MutableStateFlow(SuperAdminStats())
    val stats: StateFlow<SuperAdminStats> = _stats.asStateFlow()

    private val _selectedClinic = MutableStateFlow<Clinic?>(null)
    val selectedClinic: StateFlow<Clinic?> = _selectedClinic.asStateFlow()

    private val _subscriptionHistory = MutableStateFlow<List<SubscriptionLog>>(emptyList())
    val subscriptionHistory: StateFlow<List<SubscriptionLog>> = _subscriptionHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadClinics()
    }

    fun loadClinics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val list = clinicRepository.getAllClinics()
                _clinics.value = list
                calculateStats(list)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load clinics"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calculateStats(list: List<Clinic>) {
        var total = list.size
        var active = 0
        var trial = 0
        var expired = 0
        var suspended = 0

        list.forEach { c ->
            when (c.subscriptionStatus) {
                "active" -> active++
                "trial" -> trial++
                "expired" -> expired++
                "suspended" -> suspended++
            }
        }

        _stats.value = SuperAdminStats(total, active, trial, expired, suspended)
    }

    fun selectClinic(clinic: Clinic?) {
        _selectedClinic.value = clinic
        if (clinic != null && clinic.id != null) {
            loadSubscriptionHistory(clinic.id)
        } else {
            _subscriptionHistory.value = emptyList()
        }
    }

    private fun loadSubscriptionHistory(clinicId: String) {
        viewModelScope.launch {
            try {
                val history = clinicRepository.getSubscriptionHistory(clinicId)
                _subscriptionHistory.value = history
            } catch (e: Exception) {
                // Ignore audit loading errors silently
            }
        }
    }

    fun onboardClinic(
        name: String,
        adminName: String,
        adminPhone: String,
        adminPasswordPlain: String,
        plan: String,
        startDate: String,
        endDate: String,
        status: String
    , onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                clinicRepository.onboardClinic(
                    name = name,
                    adminName = adminName,
                    adminPhone = adminPhone,
                    adminPasswordPlain = adminPasswordPlain,
                    plan = plan,
                    startDate = startDate,
                    endDate = endDate,
                    status = status,
                    superAdminId = superAdminId
                )
                loadClinics()
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to onboard clinic"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSubscription(
        clinicId: String,
        action: String, // 'activate', 'renew', 'suspend', 'expire', 'change_plan'
        newStatus: String,
        newPlan: String?,
        newStartDate: String?,
        newEndDate: String?,
        notes: String?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val updated = clinicRepository.updateSubscription(
                    clinicId = clinicId,
                    action = action,
                    newStatus = newStatus,
                    newPlan = newPlan,
                    newStartDate = newStartDate,
                    newEndDate = newEndDate,
                    changedByUserId = superAdminId,
                    notes = notes
                )
                // Update stats and selected clinic views
                loadClinics()
                selectClinic(updated)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update subscription"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteClinic(clinicId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                clinicRepository.deleteClinic(clinicId)
                loadClinics()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete clinic"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
