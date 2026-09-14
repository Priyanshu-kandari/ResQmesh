package com.example.sos.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Manages persistent user preferences, registration status, and stable ResQMesh device identifier.
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)

    var deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = "RQ-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString("device_id", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "Citizen") ?: "Citizen"
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userAge: String
        get() = prefs.getString("user_age", "") ?: ""
        set(value) = prefs.edit().putString("user_age", value).apply()

    var userPhone: String
        get() = prefs.getString("user_phone", "") ?: ""
        set(value) = prefs.edit().putString("user_phone", value).apply()

    var isRegistered: Boolean
        get() = prefs.getBoolean("is_registered", false)
        set(value) = prefs.edit().putBoolean("is_registered", value).apply()

    fun saveProfile(name: String, age: String, phone: String) {
        prefs.edit()
            .putString("user_name", name)
            .putString("user_age", age)
            .putString("user_phone", phone)
            .putBoolean("is_registered", true)
            .apply()
    }

    fun clearProfile() {
        prefs.edit().putBoolean("is_registered", false).apply()
    }
}
