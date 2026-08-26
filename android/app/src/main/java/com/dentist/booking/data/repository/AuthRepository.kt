package com.dentist.booking.data.repository

import android.content.SharedPreferences
import com.dentist.booking.data.model.*
import com.dentist.booking.data.remote.SupabaseApi
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val api: SupabaseApi,
    private val sharedPreferences: SharedPreferences
) {
    private val gson = Gson()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // Attempt to restore cached user session on app start
        val cachedUserJson = sharedPreferences.getString("CACHED_USER", null)
        if (!cachedUserJson.isNullOrEmpty()) {
            try {
                val cachedUser = gson.fromJson(cachedUserJson, User::class.java)
                _currentUser.value = cachedUser
            } catch (e: Exception) {
                // Parse failed, ignore cache
            }
        }
    }

    suspend fun login(phone: String, passwordPlain: String): User {
        val response = api.login(LoginRequest(p_phone = phone, p_password = passwordPlain))
        
        // Cache JWT Token and User Profile
        sharedPreferences.edit()
            .putString("JWT_TOKEN", response.token)
            .putString("CACHED_USER", gson.toJson(response.user))
            .apply()

        _currentUser.value = response.user
        return response.user
    }

    fun logout() {
        sharedPreferences.edit()
            .remove("JWT_TOKEN")
            .remove("CACHED_USER")
            .apply()
        _currentUser.value = null
    }

    suspend fun changePassword(newPasswordPlain: String): Boolean {
        return api.changePassword(mapOf("p_new_password" to newPasswordPlain))
    }

    suspend fun adminResetPassword(targetUserId: String, newPasswordPlain: String): Boolean {
        return api.adminResetPassword(
            mapOf(
                "p_user_id" to targetUserId,
                "p_new_password" to newPasswordPlain
            )
        )
    }

    // Register a Doctor (Clinic Admin action)
    suspend fun registerDoctor(
        name: String,
        phone: String,
        passwordPlain: String,
        specialization: String,
        clinicId: String
    ): String {
        // Call Postgres RPC to hash password and save doctor
        val doctorId = api.createUserWithHash(
            CreateUserWithHashRequest(
                p_phone = phone,
                p_password = passwordPlain,
                p_name = name,
                p_role = "doctor",
                p_clinic_id = clinicId
            )
        )

        // Update the doctor's specialization and created_by fields (Postgres function handles hash, direct PATCH handles meta)
        api.updateProfile(
            idFilter = "eq.$doctorId",
            request = UpdateProfileRequest(
                name = name,
                specialization = specialization
            )
        )
        return doctorId
    }

    // Register Customer (Clinic Admin action)
    suspend fun registerCustomer(
        name: String,
        phone: String,
        passwordPlain: String,
        clinicId: String
    ) {
        // 1. Search for customer by phone
        val searchResults = api.getUsers(phoneFilter = "eq.$phone")
        val existingCustomer = searchResults.firstOrNull()

        if (existingCustomer == null) {
            // Case A: Customer doesn't exist. Create customer account + create clinic relationship
            val newUserId = api.createUserWithHash(
                CreateUserWithHashRequest(
                    p_phone = phone,
                    p_password = passwordPlain,
                    p_name = name,
                    p_role = "customer",
                    p_clinic_id = null // Customers have NULL clinic_id (connected via clinic_customers)
                )
            )

            // Create relationship in clinic_customers
            api.linkClinicCustomer(
                ClinicCustomer(clinicId = clinicId, customerId = newUserId)
            )
        } else {
            // Case B: Customer already exists
            if (existingCustomer.role != "customer") {
                throw Exception("The phone number is registered to a user with a non-customer role.")
            }

            // Check if relationship already exists
            val existingLinks = api.getClinicCustomers(
                clinicIdFilter = "eq.$clinicId",
                customerIdFilter = "eq.${existingCustomer.id}"
            )

            if (existingLinks.isEmpty()) {
                // Link existing customer to current clinic
                api.linkClinicCustomer(
                    ClinicCustomer(clinicId = clinicId, customerId = existingCustomer.id!!)
                )
            } else {
                throw Exception("This customer is already registered to this clinic.")
            }
        }
    }

    // Retrieve all doctors in a specific clinic
    suspend fun getDoctors(clinicId: String): List<User> {
        return api.getUsers(roleFilter = "eq.doctor", clinicIdFilter = "eq.$clinicId")
    }

    // Retrieve all customers in a specific clinic
    suspend fun getCustomers(clinicId: String): List<User> {
        // Fetch all customer IDs linked to this clinic
        val links = api.getClinicCustomers(clinicIdFilter = "eq.$clinicId")
        if (links.isEmpty()) return emptyList()

        // Fetch users details
        val commaSeparatedIds = links.joinToString(",") { it.customerId }
        return api.getUsers(
            select = "*",
            roleFilter = "eq.customer"
        ).filter { user -> links.any { it.customerId == user.id } }
    }
}
