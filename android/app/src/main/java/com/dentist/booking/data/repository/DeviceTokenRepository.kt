package com.dentist.booking.data.repository

import com.dentist.booking.data.model.DeviceToken
import com.dentist.booking.data.remote.SupabaseApi

class DeviceTokenRepository(
    private val api: SupabaseApi
) {
    suspend fun registerToken(userId: String, token: String) {
        val dt = DeviceToken(userId = userId, token = token)
        // Insert device token. If it already exists, standard HTTP 201 is returned
        try {
            api.registerDeviceToken(dt)
        } catch (e: Exception) {
            // Token might be duplicate or already registered, catch silently to avoid blocking login
        }
    }

    suspend fun deleteToken(userId: String, token: String) {
        try {
            api.deleteDeviceToken(userIdFilter = "eq.$userId", tokenFilter = "eq.$token")
        } catch (e: Exception) {
            // Silent catch to allow logout state cleanup to complete
        }
    }
}
