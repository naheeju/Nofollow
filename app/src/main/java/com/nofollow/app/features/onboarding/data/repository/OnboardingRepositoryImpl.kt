package com.nofollow.app.features.onboarding.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.nofollow.app.features.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Penanda "pengenalan sudah dilihat", satu boolean di DataStore. Sengaja bukan Room: tidak perlu migrasi database. */
class OnboardingRepositoryImpl(private val context: Context) : OnboardingRepository {

    override fun observeSeen(): Flow<Boolean> = context.onboardingDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_SEEN] ?: false }
        .distinctUntilChanged()

    override suspend fun markSeen() {
        context.onboardingDataStore.edit { it[KEY_SEEN] = true }
    }

    private companion object {
        val KEY_SEEN = booleanPreferencesKey("seen")
    }
}