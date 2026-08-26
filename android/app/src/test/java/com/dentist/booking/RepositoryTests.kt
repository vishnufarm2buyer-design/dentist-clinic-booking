package com.dentist.booking

import android.content.SharedPreferences
import com.dentist.booking.data.model.*
import com.dentist.booking.data.remote.SupabaseApi
import com.dentist.booking.data.repository.AppUpdateRepository
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.BookingRepository
import com.dentist.booking.data.repository.TreatmentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class RepositoryTests {

    // --- Mock implementation of SharedPreferences for JVM tests ---
    class MockSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        class Editor(private val prefs: MockSharedPreferences) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value != null) tempMap[key] = value else tempMap.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                if (values != null) tempMap[key] = values else tempMap.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                tempMap.remove(key)
                prefs.map.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                tempMap.clear()
                prefs.map.clear()
                return this
            }
            override fun commit(): Boolean {
                prefs.map.putAll(tempMap)
                return true
            }
            override fun apply() {
                prefs.map.putAll(tempMap)
            }
        }
    }

    // --- Mock implementation of SupabaseApi to assert client calls ---
    class MockSupabaseApi : SupabaseApi {
        var registeredUsers = mutableListOf<User>()
        var linkedCustomers = mutableListOf<ClinicCustomer>()
        var bookings = mutableListOf<Booking>()
        var treatments = mutableListOf<Treatment>()
        
        override suspend fun login(request: LoginRequest): LoginResponse {
            if (request.p_phone == "+1111111111" && request.p_password == "SecureAdminPassword123!") {
                return LoginResponse("mock-jwt-token", User("u1", "+1111111111", "Admin", "super_admin"))
            }
            throw Exception("28000: Invalid phone number or password")
        }

        override suspend fun createUserWithHash(request: CreateUserWithHashRequest): String {
            val id = "user-${request.p_phone}"
            registeredUsers.add(User(id, request.p_phone, request.p_name, request.p_role, request.p_clinic_id))
            return id
        }

        override suspend fun getUsers(select: String, phoneFilter: String?, roleFilter: String?, clinicIdFilter: String?): List<User> {
            var filtered = registeredUsers.toList()
            phoneFilter?.let {
                val phone = it.replace("eq.", "")
                filtered = filtered.filter { u -> u.phone == phone }
            }
            roleFilter?.let {
                val role = it.replace("eq.", "")
                filtered = filtered.filter { u -> u.role == role }
            }
            return filtered
        }

        override suspend fun createUser(user: CreateUserRequest): List<User> {
            val u = User("u-direct", user.phone, user.name, user.role, user.clinicId, user.specialization)
            registeredUsers.add(u)
            return listOf(u)
        }

        override suspend fun updatePassword(idFilter: String, request: UpdatePasswordRequest): List<User> = emptyList()

        override suspend fun changePassword(request: Map<String, String>): Boolean = true

        override suspend fun adminResetPassword(request: Map<String, String>): Boolean = true
        
        override suspend fun updateProfile(idFilter: String, request: UpdateProfileRequest): List<User> {
            val id = idFilter.replace("eq.", "")
            val index = registeredUsers.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = registeredUsers[index]
                val updated = old.copy(name = request.name, specialization = request.specialization)
                registeredUsers[index] = updated
                return listOf(updated)
            }
            return emptyList()
        }

        override suspend fun getClinics(select: String, idFilter: String?, order: String): List<Clinic> = emptyList()
        override suspend fun createClinic(clinic: Clinic): List<Clinic> = listOf(clinic.copy(id = "c1"))
        override suspend fun updateClinicSubscription(idFilter: String, clinicUpdateFields: Map<String, Any?>): List<Clinic> = emptyList()
        override suspend fun getSubscriptionLogs(select: String, clinicIdFilter: String, order: String): List<SubscriptionLog> = emptyList()
        override suspend fun createSubscriptionLog(log: SubscriptionLog): List<SubscriptionLog> = listOf(log)

        override suspend fun getClinicCustomers(select: String, clinicIdFilter: String?, customerIdFilter: String?): List<ClinicCustomer> {
            var filtered = linkedCustomers.toList()
            clinicIdFilter?.let {
                val id = it.replace("eq.", "")
                filtered = filtered.filter { c -> c.clinicId == id }
            }
            customerIdFilter?.let {
                val id = it.replace("eq.", "")
                filtered = filtered.filter { c -> c.customerId == id }
            }
            return filtered
        }

        override suspend fun linkClinicCustomer(link: ClinicCustomer): List<ClinicCustomer> {
            linkedCustomers.add(link)
            return listOf(link)
        }

        override suspend fun unlinkClinicCustomer(clinicIdFilter: String, customerIdFilter: String) {}

        override suspend fun getBookings(
            select: String,
            clinicIdFilter: String?,
            customerIdFilter: String?,
            doctorIdFilter: String?,
            statusFilter: String?,
            order: String
        ): List<BookingNested> = emptyList()

        override suspend fun createBooking(booking: Booking): List<Booking> {
            val b = booking.copy(id = "b-${bookings.size}")
            bookings.add(b)
            return listOf(b)
        }

        override suspend fun updateBooking(idFilter: String, updateFields: Map<String, Any?>): List<Booking> {
            val id = idFilter.replace("eq.", "")
            val idx = bookings.indexOfFirst { it.id == id }
            if (idx != -1) {
                var booking = bookings[idx]
                updateFields["status"]?.let { booking = booking.copy(status = it as String) }
                updateFields["assigned_doctor_id"]?.let { booking = booking.copy(assignedDoctorId = it as? String) }
                bookings[idx] = booking
                return listOf(booking)
            }
            return emptyList()
        }

        override suspend fun getTreatments(
            select: String,
            clinicIdFilter: String?,
            customerIdFilter: String?,
            order: String
        ): List<TreatmentNested> = emptyList()

        override suspend fun createTreatment(treatment: Treatment): List<Treatment> {
            val t = treatment.copy(id = "t-${treatments.size}")
            treatments.add(t)
            return listOf(t)
        }

        override suspend fun getAppVersions(select: String, order: String, limit: Int): List<AppVersion> {
            return listOf(AppVersion("v1", 2, "1.1.0", "http://apk.url", "Notes", false))
        }

        override suspend fun registerDeviceToken(token: DeviceToken): List<DeviceToken> = emptyList()
        override suspend fun deleteDeviceToken(userIdFilter: String, tokenFilter: String) {}
    }


    // --- TESTS ---

    @Test
    fun testLogin_Success() = runBlocking {
        val mockApi = MockSupabaseApi()
        val mockPrefs = MockSharedPreferences()
        val repository = AuthRepository(mockApi, mockPrefs)

        val user = repository.login("+1111111111", "SecureAdminPassword123!")

        assertEquals("u1", user.id)
        assertEquals("super_admin", user.role)
        assertEquals("mock-jwt-token", mockPrefs.getString("JWT_TOKEN", null))
        assertNotNull(repository.currentUser.value)
    }

    @Test
    fun testLogin_InvalidCredentials() = runBlocking {
        val mockApi = MockSupabaseApi()
        val mockPrefs = MockSharedPreferences()
        val repository = AuthRepository(mockApi, mockPrefs)

        try {
            repository.login("+1111111111", "WrongPassword")
            fail("Expected exception not thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("28000") || e.message!!.contains("password"))
        }
        assertNull(repository.currentUser.value)
    }

    @Test
    fun testRegisterCustomer_NewUser() = runBlocking {
        val mockApi = MockSupabaseApi()
        val mockPrefs = MockSharedPreferences()
        val repository = AuthRepository(mockApi, mockPrefs)

        repository.registerCustomer("New Customer", "+19998887777", "Password123", "clinic-a")

        // Assert customer registered in database
        assertEquals(1, mockApi.registeredUsers.size)
        assertEquals("+19998887777", mockApi.registeredUsers.first().phone)
        assertEquals("customer", mockApi.registeredUsers.first().role)

        // Assert relationship created
        assertEquals(1, mockApi.linkedCustomers.size)
        assertEquals("clinic-a", mockApi.linkedCustomers.first().clinicId)
        assertEquals("user-+19998887777", mockApi.linkedCustomers.first().customerId)
    }

    @Test
    fun testRegisterCustomer_ExistingUser_CreatesRelationOnly() = runBlocking {
        val mockApi = MockSupabaseApi()
        val mockPrefs = MockSharedPreferences()
        val repository = AuthRepository(mockApi, mockPrefs)

        // Pre-register user in DB (Clinic A)
        mockApi.registeredUsers.add(User("c-existing", "+19998887777", "Existing Customer", "customer", null))
        mockApi.linkedCustomers.add(ClinicCustomer("cc1", "clinic-a", "c-existing"))

        // Register same phone number for Clinic B
        repository.registerCustomer("Existing Customer", "+19998887777", "SomePwd", "clinic-b")

        // Assert user was NOT duplicated
        assertEquals(1, mockApi.registeredUsers.size)

        // Assert new relation created for Clinic B
        assertEquals(2, mockApi.linkedCustomers.size)
        assertTrue(mockApi.linkedCustomers.any { it.clinicId == "clinic-b" && it.customerId == "c-existing" })
    }

    @Test
    fun testRegisterCustomer_ExistingRelation_ThrowsException() = runBlocking {
        val mockApi = MockSupabaseApi()
        val mockPrefs = MockSharedPreferences()
        val repository = AuthRepository(mockApi, mockPrefs)

        // Pre-register user and link to Clinic A
        mockApi.registeredUsers.add(User("c-existing", "+19998887777", "Existing Customer", "customer", null))
        mockApi.linkedCustomers.add(ClinicCustomer("cc1", "clinic-a", "c-existing"))

        // Attempt to register again for Clinic A
        try {
            repository.registerCustomer("Existing Customer", "+19998887777", "Pwd", "clinic-a")
            fail("Expected exception for duplicate linking not thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("already registered"))
        }
    }

    @Test
    fun testCreateTreatmentAndCompleteBooking() = runBlocking {
        val mockApi = MockSupabaseApi()
        val bookingRepo = BookingRepository(mockApi)
        val treatmentRepo = TreatmentRepository(mockApi)

        // Create booking in mock db
        mockApi.bookings.add(Booking("b1", "clinic-1", "customer-1", "2026-08-25", "Toothache", "accepted", "doctor-1"))

        treatmentRepo.createTreatmentAndCompleteBooking(
            bookingId = "b1",
            customerId = "customer-1",
            clinicId = "clinic-1",
            doctorId = "doctor-1",
            notes = "Done dental filling",
            visitDate = "2026-08-25"
        )

        // Assert treatment log was created
        assertEquals(1, mockApi.treatments.size)
        assertEquals("Done dental filling", mockApi.treatments.first().notes)
        assertEquals("doctor-1", mockApi.treatments.first().doctorId)

        // Assert booking status updated to completed
        assertEquals("completed", mockApi.bookings.first().status)
    }

    @Test
    fun testAppUpdateVersionComparison() = runBlocking {
        val mockApi = MockSupabaseApi()
        val updateRepo = AppUpdateRepository(mockApi)

        val latest = updateRepo.getLatestAppVersion()
        assertNotNull(latest)
        assertEquals(2, latest!!.versionCode) // Mock returns 2, current is 1
        assertEquals("1.1.0", latest.versionName)
    }
}
