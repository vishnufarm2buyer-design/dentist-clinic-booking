package com.dentist.booking.data.repository

import com.dentist.booking.data.model.Clinic
import com.dentist.booking.data.model.SubscriptionLog
import com.dentist.booking.data.remote.SupabaseApi

class ClinicRepository(
    private val api: SupabaseApi
) {

    // Retrieve all clinics (Super Admin overview)
    suspend fun getAllClinics(): List<Clinic> {
        return api.getClinics()
    }

    // Retrieve a single clinic details
    suspend fun getClinicById(id: String): Clinic {
        val clinics = api.getClinics(idFilter = "eq.$id")
        return clinics.firstOrNull() ?: throw Exception("Clinic not found")
    }

    // Retrieve only clinics linked to a specific customer
    suspend fun getClinicsForCustomer(customerId: String): List<Clinic> {
        // Fetch clinic links
        val links = api.getClinicCustomers(customerIdFilter = "eq.$customerId")
        if (links.isEmpty()) return emptyList()

        // Fetch details of all clinics
        val clinics = api.getClinics()
        return clinics.filter { clinic -> links.any { it.clinicId == clinic.id } }
    }

    // Onboard a new clinic (Super Admin action)
    suspend fun onboardClinic(
        name: String,
        adminName: String,
        adminPhone: String,
        adminPasswordPlain: String,
        plan: String,
        startDate: String,
        endDate: String,
        status: String,
        superAdminId: String
    ): Clinic {
        // 1. Create the Clinic
        val newClinic = Clinic(
            name = name,
            subscriptionStatus = status,
            subscriptionPlan = plan,
            subscriptionStartDate = startDate,
            subscriptionEndDate = endDate
        )
        val clinicResult = api.createClinic(newClinic)
        val createdClinic = clinicResult.firstOrNull() ?: throw Exception("Failed to onboard clinic")

        // 2. Create the Clinic Admin User linked to this clinic
        // The custom helper `createUserWithHash` hashes the password and registers the user
        api.createUserWithHash(
            com.dentist.booking.data.model.CreateUserWithHashRequest(
                p_phone = adminPhone,
                p_password = adminPasswordPlain,
                p_name = adminName,
                p_role = "clinic_admin",
                p_clinic_id = createdClinic.id
            )
        )

        // 3. Create Subscription Log Audit Trail
        val auditLog = SubscriptionLog(
            clinicId = createdClinic.id!!,
            action = "create",
            plan = plan,
            startDate = startDate,
            endDate = endDate,
            changedBy = superAdminId,
            notes = "Initial onboarding subscription set to status: $status"
        )
        api.createSubscriptionLog(auditLog)

        return createdClinic
    }

    // Update Clinic Subscription (Super Admin action)
    suspend fun updateSubscription(
        clinicId: String,
        action: String, // 'activate', 'renew', 'suspend', 'expire', 'change_plan'
        newStatus: String,
        newPlan: String?,
        newStartDate: String?,
        newEndDate: String?,
        changedByUserId: String,
        notes: String?
    ): Clinic {
        // 1. Map fields to update
        val updateFields = mutableMapOf<String, Any?>()
        updateFields["subscription_status"] = newStatus
        if (newPlan != null) updateFields["subscription_plan"] = newPlan
        if (newStartDate != null) updateFields["subscription_start_date"] = newStartDate
        if (newEndDate != null) updateFields["subscription_end_date"] = newEndDate

        // 2. Perform database update
        val result = api.updateClinicSubscription(idFilter = "eq.$clinicId", clinicUpdateFields = updateFields)
        val updatedClinic = result.firstOrNull() ?: throw Exception("Failed to update clinic subscription")

        // 3. Log the change to subscription audit trails
        val auditLog = SubscriptionLog(
            clinicId = clinicId,
            action = action,
            plan = newPlan ?: updatedClinic.subscriptionPlan,
            startDate = newStartDate ?: updatedClinic.subscriptionStartDate,
            endDate = newEndDate ?: updatedClinic.subscriptionEndDate,
            changedBy = changedByUserId,
            notes = notes ?: "Subscription status changed to: $newStatus"
        )
        api.createSubscriptionLog(auditLog)

        return updatedClinic
    }

    // Retrieve subscription change logs for a clinic
    suspend fun getSubscriptionHistory(clinicId: String): List<SubscriptionLog> {
        return api.getSubscriptionLogs(clinicIdFilter = "eq.$clinicId")
    }
}
