package com.dentist.booking.data.remote

import com.dentist.booking.data.model.*
import retrofit2.http.*

interface SupabaseApi {

    // --- CUSTOM AUTHENTICATION & SEED RPC ---
    
    @POST("rest/v1/rpc/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("rest/v1/rpc/create_user_with_hash")
    suspend fun createUserWithHash(
        @Body request: CreateUserWithHashRequest
    ): String

    @POST("rest/v1/rpc/change_password")
    suspend fun changePassword(
        @Body request: Map<String, String>
    ): Boolean

    @POST("rest/v1/rpc/admin_reset_password")
    suspend fun adminResetPassword(
        @Body request: Map<String, String>
    ): Boolean


    // --- USERS TABLE ---

    @GET("rest/v1/users")
    suspend fun getUsers(
        @Query("select") select: String = "*",
        @Query("phone") phoneFilter: String? = null,
        @Query("role") roleFilter: String? = null,
        @Query("clinic_id") clinicIdFilter: String? = null
    ): List<User>

    @POST("rest/v1/users")
    suspend fun createUser(
        @Body user: CreateUserRequest
    ): List<User>

    @PATCH("rest/v1/users")
    suspend fun updatePassword(
        @Query("id") idFilter: String,
        @Body request: UpdatePasswordRequest
    ): List<User>

    @PATCH("rest/v1/users")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body request: UpdateProfileRequest
    ): List<User>


    // --- CLINICS TABLE ---

    @GET("rest/v1/clinics")
    suspend fun getClinics(
        @Query("select") select: String = "*",
        @Query("id") idFilter: String? = null,
        @Query("order") order: String = "name.asc"
    ): List<Clinic>

    @POST("rest/v1/clinics")
    suspend fun createClinic(
        @Body clinic: Clinic
    ): List<Clinic>

    @PATCH("rest/v1/clinics")
    suspend fun updateClinicSubscription(
        @Query("id") idFilter: String,
        @Body clinicUpdateFields: Map<String, @JvmSuppressWildcards Any?>
    ): List<Clinic>


    // --- SUBSCRIPTION LOGS TABLE ---

    @GET("rest/v1/subscription_logs")
    suspend fun getSubscriptionLogs(
        @Query("select") select: String = "*,changed_by_name:users!changed_by(name)",
        @Query("clinic_id") clinicIdFilter: String,
        @Query("order") order: String = "created_at.desc"
    ): List<SubscriptionLog>

    @POST("rest/v1/subscription_logs")
    suspend fun createSubscriptionLog(
        @Body log: SubscriptionLog
    ): List<SubscriptionLog>


    // --- CLINIC CUSTOMERS (MANY-TO-MANY LINK) ---

    @GET("rest/v1/clinic_customers")
    suspend fun getClinicCustomers(
        @Query("select") select: String = "*",
        @Query("clinic_id") clinicIdFilter: String? = null,
        @Query("customer_id") customerIdFilter: String? = null
    ): List<ClinicCustomer>

    @POST("rest/v1/clinic_customers")
    suspend fun linkClinicCustomer(
        @Body link: ClinicCustomer
    ): List<ClinicCustomer>

    @DELETE("rest/v1/clinic_customers")
    suspend fun unlinkClinicCustomer(
        @Query("clinic_id") clinicIdFilter: String,
        @Query("customer_id") customerIdFilter: String
    )


    // --- BOOKINGS TABLE ---

    @GET("rest/v1/bookings")
    suspend fun getBookings(
        @Query("select") select: String = "*,customer:users!customer_id(name,phone),doctor:users!assigned_doctor_id(name),clinic:clinics(name)",
        @Query("clinic_id") clinicIdFilter: String? = null,
        @Query("customer_id") customerIdFilter: String? = null,
        @Query("assigned_doctor_id") doctorIdFilter: String? = null,
        @Query("status") statusFilter: String? = null,
        @Query("order") order: String = "requested_date.asc"
    ): List<BookingNested>

    @POST("rest/v1/bookings")
    suspend fun createBooking(
        @Body booking: Booking
    ): List<Booking>

    @PATCH("rest/v1/bookings")
    suspend fun updateBooking(
        @Query("id") idFilter: String,
        @Body updateFields: Map<String, @JvmSuppressWildcards Any?>
    ): List<Booking>


    // --- TREATMENTS TABLE (Isolated per-clinic) ---

    @GET("rest/v1/treatments")
    suspend fun getTreatments(
        @Query("select") select: String = "*,customer:users!customer_id(name),doctor:users!doctor_id(name),clinic:clinics(name)",
        @Query("clinic_id") clinicIdFilter: String? = null,
        @Query("customer_id") customerIdFilter: String? = null,
        @Query("order") order: String = "visit_date.desc"
    ): List<TreatmentNested>

    @POST("rest/v1/treatments")
    suspend fun createTreatment(
        @Body treatment: Treatment
    ): List<Treatment>


    // --- APP UPDATE MANAGER TABLE ---

    @GET("rest/v1/app_versions")
    suspend fun getAppVersions(
        @Query("select") select: String = "*",
        @Query("order") order: String = "version_code.desc",
        @Query("limit") limit: Int = 1
    ): List<AppVersion>


    // --- DEVICE PUSH TOKENS TABLE ---

    @POST("rest/v1/device_tokens")
    suspend fun registerDeviceToken(
        @Body token: DeviceToken
    ): List<DeviceToken>

    @DELETE("rest/v1/device_tokens")
    suspend fun deleteDeviceToken(
        @Query("user_id") userIdFilter: String,
        @Query("token") tokenFilter: String
    )
}
