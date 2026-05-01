package com.example.localllm.domain.tools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.toolConsentStore: DataStore<Preferences> by preferencesDataStore(name = "tool_consent")

/**
 * Minimal surface that [ActionOrchestrator] depends on for consent decisions.
 * Extracted as an interface so the orchestrator can be unit-tested without
 * pulling in DataStore / Android Context.
 */
interface ToolConsentGate {
    /** Snapshot read of the persistent enable flag. */
    suspend fun isPersistentlyEnabledNow(toolName: String): Boolean
    /** Has the user approved this tool for the current process session? */
    fun isSessionApproved(toolName: String): Boolean
}

/**
 * Persists user consent for SENSITIVE tools across app launches and tracks
 * one-shot in-memory session approvals (consent that lasts until process death).
 *
 * Two layers:
 *  1. **Persistent enable flag** — user must opt-in to a sensitive tool in
 *     settings before it can ever be invoked (default: false).
 *  2. **Session approval** — even after opting in, the user must approve the
 *     specific invocation (or grant "always allow this session"); approval is
 *     held only in memory and is lost when the process dies.
 *
 * This double gating protects against silent PII exfiltration even if the
 * persistent flag is accidentally enabled.
 */
@Singleton
class ToolConsentStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ToolConsentGate {

    private val sessionApprovals = ConcurrentHashMap<String, Boolean>()

    /** Reactive stream: is the tool persistently enabled in settings? */
    fun isPersistentlyEnabled(toolName: String): Flow<Boolean> {
        val key = booleanPreferencesKey("tool_enabled_$toolName")
        return context.toolConsentStore.data
            .catch { e ->
                Timber.w(e, "ToolConsentStore: read error for %s", toolName)
                emit(emptyPreferences())
            }
            .map { prefs -> prefs[key] ?: false }
    }

    /** Snapshot read used by [ActionOrchestrator] before dispatching a sensitive tool. */
    override suspend fun isPersistentlyEnabledNow(toolName: String): Boolean =
        isPersistentlyEnabled(toolName).first()

    /** Persistently enable or disable a sensitive tool. */
    suspend fun setPersistentlyEnabled(toolName: String, enabled: Boolean) {
        val key = booleanPreferencesKey("tool_enabled_$toolName")
        context.toolConsentStore.edit { it[key] = enabled }
        if (!enabled) {
            // Disabling should also clear any in-memory session approval.
            sessionApprovals.remove(toolName)
        }
        Timber.i("ToolConsentStore: %s persistent=%s", toolName, enabled)
    }

    /** True if the user has approved this tool for the current process session. */
    override fun isSessionApproved(toolName: String): Boolean =
        sessionApprovals[toolName] == true

    /** Mark a tool approved for the rest of this process session. */
    fun grantSessionApproval(toolName: String) {
        sessionApprovals[toolName] = true
        Timber.i("ToolConsentStore: %s session-approved", toolName)
    }

    /** Revoke session approval (e.g. user toggled it off in a sensitive screen). */
    fun revokeSessionApproval(toolName: String) {
        sessionApprovals.remove(toolName)
        Timber.i("ToolConsentStore: %s session-revoked", toolName)
    }

    /** Clear all in-memory approvals (e.g. on logout / lock). */
    fun clearAllSessionApprovals() {
        sessionApprovals.clear()
        Timber.i("ToolConsentStore: all session approvals cleared")
    }
}
