package com.dentist.booking.data.repository

import com.dentist.booking.data.model.Booking
import com.dentist.booking.data.model.BookingNested
import com.dentist.booking.data.remote.SupabaseApi

class BookingRepository(
    private val api: SupabaseApi
) {

    // Retrieve bookings for a Clinic (Clinic Admin role)
    suspend fun getBookingsForClinic(clinicId: String): List<Booking> {
        val nested = api.getBookings(clinicIdFilter = "eq.$clinicId")
        return nested.map { it.toBooking() }
    }

    // Retrieve bookings for a Customer (Customer role)
    suspend fun getBookingsForCustomer(customerId: String): List<Booking> {
        val nested = api.getBookings(customerIdFilter = "eq.$customerId")
        return nested.map { it.toBooking() }
    }

    // Retrieve bookings assigned to a Doctor (Doctor role)
    suspend fun getBookingsForDoctor(doctorId: String): List<Booking> {
        val nested = api.getBookings(doctorIdFilter = "eq.$doctorId")
        return nested.map { it.toBooking() }
    }

    // Create a new Booking request (Customer booking flow)
    suspend fun createBooking(
        clinicId: String,
        customerId: String,
        requestedDate: String,
        reason: String
    ): Booking {
        val booking = Booking(
            clinicId = clinicId,
            customerId = customerId,
            requestedDate = requestedDate,
            reason = reason,
            status = "pending"
        )
        val result = api.createBooking(booking)
        return result.firstOrNull() ?: throw Exception("Failed to book appointment")
    }

    // Accept Booking and Assign Doctor (Clinic Admin action)
    suspend fun acceptBooking(bookingId: String, doctorId: String): Booking {
        val updateFields = mapOf(
            "status" to "accepted",
            "assigned_doctor_id" to doctorId
        )
        val result = api.updateBooking(idFilter = "eq.$bookingId", updateFields = updateFields)
        return result.firstOrNull() ?: throw Exception("Failed to accept booking")
    }

    // Reject Booking (Clinic Admin action)
    suspend fun rejectBooking(bookingId: String, rejectionReason: String?): Booking {
        val updateFields = mapOf(
            "status" to "rejected",
            "rejection_reason" to rejectionReason
        )
        val result = api.updateBooking(idFilter = "eq.$bookingId", updateFields = updateFields)
        return result.firstOrNull() ?: throw Exception("Failed to reject booking")
    }

    // Cancel booking (Customer action)
    suspend fun cancelBooking(bookingId: String): Booking {
        val updateFields = mapOf(
            "status" to "cancelled"
        )
        val result = api.updateBooking(idFilter = "eq.$bookingId", updateFields = updateFields)
        return result.firstOrNull() ?: throw Exception("Failed to cancel booking")
    }
}
