package com.openmausbot.companion.storage

import android.content.Context
import com.openmausbot.companion.core.PushRegistrationStore

/** Digests only, never a token. The file stays out of backup and device transfer. */
class PrefsPushRegistrationStore(context: Context) : PushRegistrationStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override suspend fun accepted(connectionId: String): String? = prefs.getString(connectionId, null)

    override suspend fun save(connectionId: String, signature: String) {
        prefs.edit().putString(connectionId, signature).apply()
    }

    override suspend fun forget(connectionId: String) {
        prefs.edit().remove(connectionId).apply()
    }

    override suspend fun forgetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FILE = "openmaus.push"
    }
}
