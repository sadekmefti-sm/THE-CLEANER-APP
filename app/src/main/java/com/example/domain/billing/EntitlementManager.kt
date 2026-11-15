package com.example.domain.billing

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.SensitivityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore by preferencesDataStore(name = "cleanvault_entitlements")

enum class PremiumFeature(val title: String, val description: String) {
    UNLIMITED_BATCH_COMPRESS(
        "Unlimited Batch Video/Photo Downscaler",
        "Shrink unlimited 4K videos and 50MP photos with hardware H.265 encoding."
    ),
    ONE_TAP_SMART_CLEAN(
        "One-Tap Batch Smart Clean",
        "Auto-keep crowned Best Shots across all clusters and stage duplicates in one tap."
    ),
    OCR_DOCUMENT_VAULT(
        "Unlimited OCR Document & Receipt Guardrail",
        "Automatic ML Kit text recognition shield preventing receipt & ID deletion."
    )
}

@Immutable
data class EntitlementState(
    val isProUnlocked: Boolean = true, // v1 Free Release Default = true
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.BALANCED
)

/**
 * Future-proof commercialization hook (v1 Free, v2 Google Play Billing ready).
 * Wraps premium actions behind a clean FeatureGate without coupling ViewModels to BillingClient.
 */
interface EntitlementManager {
    val entitlementState: Flow<EntitlementState>
    suspend fun isFeatureAllowed(feature: PremiumFeature): Boolean
    suspend fun setProUnlockedForTesting(unlocked: Boolean)
    suspend fun setSensitivityLevel(level: SensitivityLevel)
}

class LocalEntitlementManager(private val context: Context) : EntitlementManager {

    companion object {
        // Defaulting to true for v1 Free release as specified
        private val KEY_PRO_UNLOCKED = booleanPreferencesKey("is_pro_unlocked_v1")
        private val KEY_HAMMING_SENSITIVITY = intPreferencesKey("hamming_sensitivity_bits")
    }

    override val entitlementState: Flow<EntitlementState> =
        context.entitlementDataStore.data.map { prefs ->
            val unlocked = prefs[KEY_PRO_UNLOCKED] ?: true
            val bits = prefs[KEY_HAMMING_SENSITIVITY] ?: SensitivityLevel.BALANCED.maxHammingDistance
            val level = SensitivityLevel.entries.firstOrNull { it.maxHammingDistance == bits }
                ?: SensitivityLevel.BALANCED
            EntitlementState(
                isProUnlocked = unlocked,
                sensitivityLevel = level
            )
        }

    override suspend fun isFeatureAllowed(feature: PremiumFeature): Boolean {
        return context.entitlementDataStore.data.first()[KEY_PRO_UNLOCKED] ?: true
    }

    override suspend fun setProUnlockedForTesting(unlocked: Boolean) {
        context.entitlementDataStore.edit { prefs ->
            prefs[KEY_PRO_UNLOCKED] = unlocked
        }
    }

    override suspend fun setSensitivityLevel(level: SensitivityLevel) {
        context.entitlementDataStore.edit { prefs ->
            prefs[KEY_HAMMING_SENSITIVITY] = level.maxHammingDistance
        }
    }
}
