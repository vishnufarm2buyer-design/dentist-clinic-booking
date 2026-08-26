package com.dentist.booking

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dentist.booking.data.remote.AuthInterceptor
import com.dentist.booking.data.remote.SupabaseApi
import com.dentist.booking.data.repository.AppUpdateRepository
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.BookingRepository
import com.dentist.booking.data.repository.ClinicRepository
import com.dentist.booking.data.repository.DeviceTokenRepository
import com.dentist.booking.data.repository.TreatmentRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DentistApp : Application() {

    // Repositories
    lateinit var authRepository: AuthRepository
    lateinit var clinicRepository: ClinicRepository
    lateinit var bookingRepository: BookingRepository
    lateinit var treatmentRepository: TreatmentRepository
    lateinit var appUpdateRepository: AppUpdateRepository
    lateinit var deviceTokenRepository: DeviceTokenRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Secure Shared Preferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val securePreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 2. Setup Network Layer (Retrofit + OkHttp)
        // Note: Placeholders are used. The client will query these values.
        val supabaseUrl = securePreferences.getString("SUPABASE_URL", "https://tkgteipwfylirwzgoros.supabase.co/") ?: "https://tkgteipwfylirwzgoros.supabase.co/"
        val supabaseAnonKey = securePreferences.getString("SUPABASE_ANON_KEY", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRrZ3RlaXB3ZnlsaXJ3emdvcm9zIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc2NzM5NzksImV4cCI6MjEwMzI0OTk3OX0.owGmvxMiMFMC0jG1Xj0ehJWSkY2PDzII2fJ7_7_uEOY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRrZ3RlaXB3ZnlsaXJ3emdvcm9zIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc2NzM5NzksImV4cCI6MjEwMzI0OTk3OX0.owGmvxMiMFMC0jG1Xj0ehJWSkY2PDzII2fJ7_7_uEOY"

        val authInterceptor = AuthInterceptor(securePreferences, supabaseAnonKey)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(if (supabaseUrl.endsWith("/")) supabaseUrl else "$supabaseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(SupabaseApi::class.java)

        // 3. Initialize Repositories
        authRepository = AuthRepository(api, securePreferences)
        clinicRepository = ClinicRepository(api)
        bookingRepository = BookingRepository(api)
        treatmentRepository = TreatmentRepository(api)
        appUpdateRepository = AppUpdateRepository(api)
        deviceTokenRepository = DeviceTokenRepository(api)
    }

    // Helper to refresh network client when Supabase URL/API Keys are configured by the user
    fun updateSupabaseConfig(url: String, anonKey: String) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val securePreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        securePreferences.edit()
            .putString("SUPABASE_URL", url)
            .putString("SUPABASE_ANON_KEY", anonKey)
            .apply()

        // Re-initialize API with new URL
        val authInterceptor = AuthInterceptor(securePreferences, anonKey)
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(if (url.endsWith("/")) url else "$url/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(SupabaseApi::class.java)

        authRepository = AuthRepository(api, securePreferences)
        clinicRepository = ClinicRepository(api)
        bookingRepository = BookingRepository(api)
        treatmentRepository = TreatmentRepository(api)
        appUpdateRepository = AppUpdateRepository(api)
        deviceTokenRepository = DeviceTokenRepository(api)
    }

    companion object {
        lateinit var instance: DentistApp
            private set
    }
}
