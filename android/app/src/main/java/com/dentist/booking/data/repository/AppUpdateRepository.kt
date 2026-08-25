package com.dentist.booking.data.repository

import com.dentist.booking.data.model.AppVersion
import com.dentist.booking.data.remote.SupabaseApi

class AppUpdateRepository(
    private val api: SupabaseApi
) {
    suspend fun getLatestAppVersion(): AppVersion? {
        val versions = api.getAppVersions()
        return versions.firstOrNull()
    }
}
