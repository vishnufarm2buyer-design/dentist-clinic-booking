package com.dentist.booking.data.repository

import com.dentist.booking.data.model.Treatment
import com.dentist.booking.data.model.TreatmentNested
import com.dentist.booking.data.remote.SupabaseApi

class TreatmentRepository(
    private val api: SupabaseApi
) {

    // Retrieve all treatments for a Customer (Customer role - see across clinics)
    suspend fun getTreatmentsForCustomer(customerId: String, clinicId: String? = null): List<Treatment> {
        val nested = if (clinicId != null) {
            api.getTreatments(customerIdFilter = "eq.$customerId", clinicIdFilter = "eq.$clinicId")
        } else {
            api.getTreatments(customerIdFilter = "eq.$customerId")
        }
        return nested.map { it.toTreatment() }
    }

    // Retrieve treatments for a patient within a Clinic (Clinic Admin and Doctor role)
    // Satisfies critical isolation rule: clinic can only view treatments of their own clinic.
    suspend fun getClinicPatientTreatments(clinicId: String, customerId: String): List<Treatment> {
        val nested = api.getTreatments(clinicIdFilter = "eq.$clinicId", customerIdFilter = "eq.$customerId")
        return nested.map { it.toTreatment() }
    }

    // Create a Treatment Record and Complete the Booking (Doctor action)
    suspend fun createTreatmentAndCompleteBooking(
        bookingId: String,
        customerId: String,
        clinicId: String,
        doctorId: String,
        notes: String,
        visitDate: String
    ): Treatment {
        // 1. Create treatment record
        val treatment = Treatment(
            bookingId = bookingId,
            customerId = customerId,
            clinicId = clinicId,
            doctorId = doctorId,
            notes = notes,
            visitDate = visitDate
        )
        val result = api.createTreatment(treatment)
        val createdTreatment = result.firstOrNull() ?: throw Exception("Failed to log treatment record")

        // 2. Mark booking completed
        val updateFields = mapOf(
            "status" to "completed"
        )
        api.updateBooking(idFilter = "eq.$bookingId", updateFields = updateFields)

        return createdTreatment
    }
}
