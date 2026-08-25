package com.dentist.booking.data.remote

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sharedPreferences: SharedPreferences,
    private val anonKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // 1. Get dynamically configured anon key
        val currentAnonKey = sharedPreferences.getString("SUPABASE_ANON_KEY", anonKey) ?: anonKey
        requestBuilder.header("apikey", currentAnonKey)

        // 2. Fetch authenticated JWT token from secure preferences
        val token = sharedPreferences.getString("JWT_TOKEN", null)
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        } else {
            // Default to anon key if user is not logged in (e.g. for login RPC itself)
            requestBuilder.header("Authorization", "Bearer $currentAnonKey")
        }

        // 3. For POST/PATCH operations, ask Supabase to return the modified record
        if (originalRequest.method == "POST" || originalRequest.method == "PATCH") {
            requestBuilder.header("Prefer", "return=representation")
        }

        return chain.proceed(requestBuilder.build())
    }
}
