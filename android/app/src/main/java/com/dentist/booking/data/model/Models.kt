package com.dentist.booking.data.model

import com.google.gson.annotations.SerializedName

// Database Entities

data class User(
    val id: String? = null,
    val phone: String,
    val name: String,
    val role: String, // super_admin, clinic_admin, doctor, customer
    @SerializedName("clinic_id") val clinicId: String? = null,
    val specialization: String? = null,
    @SerializedName("created_by_clinic_id") val createdByClinicId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Clinic(
    val id: String? = null,
    val name: String,
    @SerializedName("subscription_status") val subscriptionStatus: String, // trial, active, expired, suspended
    @SerializedName("subscription_plan") val subscriptionPlan: String? = null,
    @SerializedName("subscription_start_date") val subscriptionStartDate: String? = null,
    @SerializedName("subscription_end_date") val subscriptionEndDate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SubscriptionLog(
    val id: String? = null,
    @SerializedName("clinic_id") val clinicId: String,
    val action: String,
    val plan: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("changed_by") val changedBy: String,
    val notes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("changed_by_name") val changedByName: String? = null
)

data class ClinicCustomer(
    val id: String? = null,
    @SerializedName("clinic_id") val clinicId: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("joined_at") val joinedAt: String? = null
)

data class Booking(
    val id: String? = null,
    @SerializedName("clinic_id") val clinicId: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("requested_date") val requestedDate: String, // yyyy-MM-dd
    val reason: String,
    val status: String, // pending, accepted, rejected, completed, cancelled
    @SerializedName("assigned_doctor_id") val assignedDoctorId: String? = null,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    
    // Join fields populated on-demand
    val customerName: String? = null,
    val customerPhone: String? = null,
    val doctorName: String? = null,
    val clinicName: String? = null
)

data class Treatment(
    val id: String? = null,
    @SerializedName("booking_id") val bookingId: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("clinic_id") val clinicId: String,
    @SerializedName("doctor_id") val doctorId: String,
    val notes: String,
    @SerializedName("visit_date") val visitDate: String, // yyyy-MM-dd
    @SerializedName("created_at") val createdAt: String? = null,
    
    // Join fields populated on-demand
    val customerName: String? = null,
    val doctorName: String? = null,
    val clinicName: String? = null
)

data class AppVersion(
    val id: String? = null,
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("release_notes") val releaseNotes: String,
    @SerializedName("force_update") val forceUpdate: Boolean,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DeviceToken(
    val id: String? = null,
    @SerializedName("user_id") val userId: String,
    val token: String,
    @SerializedName("created_at") val createdAt: String? = null
)


// PostgREST Nested Profiles

data class UserProfile(
    val name: String,
    val phone: String? = null
)

data class ClinicProfile(
    val name: String
)

data class BookingNested(
    val id: String,
    @SerializedName("clinic_id") val clinicId: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("requested_date") val requestedDate: String,
    val reason: String,
    val status: String,
    @SerializedName("assigned_doctor_id") val assignedDoctorId: String?,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val customer: UserProfile?,
    val doctor: UserProfile?,
    val clinic: ClinicProfile?
) {
    fun toBooking() = Booking(
        id = id,
        clinicId = clinicId,
        customerId = customerId,
        requestedDate = requestedDate,
        reason = reason,
        status = status,
        assignedDoctorId = assignedDoctorId,
        rejectionReason = rejectionReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
        customerName = customer?.name,
        customerPhone = customer?.phone,
        doctorName = doctor?.name,
        clinicName = clinic?.name
    )
}

data class TreatmentNested(
    val id: String,
    @SerializedName("booking_id") val bookingId: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("clinic_id") val clinicId: String,
    @SerializedName("doctor_id") val doctorId: String,
    val notes: String,
    @SerializedName("visit_date") val visitDate: String,
    @SerializedName("created_at") val createdAt: String,
    val customer: UserProfile?,
    val doctor: UserProfile?,
    val clinic: ClinicProfile?
) {
    fun toTreatment() = Treatment(
        id = id,
        bookingId = bookingId,
        customerId = customerId,
        clinicId = clinicId,
        doctorId = doctorId,
        notes = notes,
        visitDate = visitDate,
        createdAt = createdAt,
        customerName = customer?.name,
        doctorName = doctor?.name,
        clinicName = clinic?.name
    )
}

// Network Request / Response Payloads

data class LoginRequest(
    val p_phone: String,
    val p_password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class CreateUserWithHashRequest(
    val p_phone: String,
    val p_password: String,
    val p_name: String,
    val p_role: String,
    val p_clinic_id: String?
)

data class CreateUserRequest(
    val phone: String,
    @SerializedName("password_hash") val passwordHash: String,
    val name: String,
    val role: String,
    @SerializedName("clinic_id") val clinicId: String?,
    @SerializedName("specialization") val specialization: String? = null,
    @SerializedName("created_by_clinic_id") val createdByClinicId: String?
)

data class UpdatePasswordRequest(
    @SerializedName("password_hash") val passwordHash: String
)

data class UpdateProfileRequest(
    val name: String,
    val specialization: String? = null
)
