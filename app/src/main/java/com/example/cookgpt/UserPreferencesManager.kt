package com.example.cookgpt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesManager(private val context: Context) {

    companion object {
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val CURRENT_ONBOARDING_STEP = intPreferencesKey("current_onboarding_step")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_WEIGHT = floatPreferencesKey("user_weight")
        val USER_HEIGHT = floatPreferencesKey("user_height")
        val USER_FITNESS_GOAL = stringPreferencesKey("user_fitness_goal")
        val USER_ALLERGIES = stringSetPreferencesKey("user_allergies")
        val USER_RESTRICTIONS = stringSetPreferencesKey("user_restrictions")
        val USER_PHONE_NUMBER = stringPreferencesKey("user_phone_number")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[IS_ONBOARDING_COMPLETED] ?: false }
    val currentStep: Flow<Int> = context.dataStore.data.map { it[CURRENT_ONBOARDING_STEP] ?: 0 }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userId: Flow<String> = context.dataStore.data.map { it[USER_ID] ?: "" }

    suspend fun getOrGenerateUserId(): String {
        val currentId = userId.first()
        return if (currentId.isEmpty()) {
            val newId = UUID.randomUUID().toString()
            context.dataStore.edit { it[USER_ID] = newId }
            newId
        } else {
            currentId
        }
    }

    suspend fun saveOnboardingStatus(completed: Boolean) {
        context.dataStore.edit { it[IS_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun saveCurrentStep(step: Int) {
        context.dataStore.edit { it[CURRENT_ONBOARDING_STEP] = step }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME] = name }
    }

    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { it[USER_EMAIL] = email }
    }

    suspend fun saveHealthProfile(gender: String) {
        context.dataStore.edit { it[USER_GENDER] = gender }
    }

    suspend fun saveBodyMetrics(age: Int, weight: Float, height: Float) {
        context.dataStore.edit {
            it[USER_AGE] = age
            it[USER_WEIGHT] = weight
            it[USER_HEIGHT] = height
        }
    }

    suspend fun saveFitnessGoal(goal: String) {
        context.dataStore.edit { it[USER_FITNESS_GOAL] = goal }
    }

    suspend fun saveDietaryInfo(allergies: Set<String>, restrictions: Set<String>) {
        context.dataStore.edit {
            it[USER_ALLERGIES] = allergies
            it[USER_RESTRICTIONS] = restrictions
        }
    }

    suspend fun savePhoneNumber(phone: String) {
        context.dataStore.edit { it[USER_PHONE_NUMBER] = phone }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { it.clear() }
    }
    
    val userData: Flow<UserData> = context.dataStore.data.map { prefs ->
        UserData(
            name = prefs[USER_NAME] ?: "",
            email = prefs[USER_EMAIL] ?: "",
            gender = prefs[USER_GENDER] ?: "",
            age = prefs[USER_AGE] ?: 0,
            weight = prefs[USER_WEIGHT] ?: 0f,
            height = prefs[USER_HEIGHT] ?: 0f,
            fitnessGoal = prefs[USER_FITNESS_GOAL] ?: "",
            allergies = prefs[USER_ALLERGIES] ?: emptySet(),
            restrictions = prefs[USER_RESTRICTIONS] ?: emptySet(),
            phoneNumber = prefs[USER_PHONE_NUMBER] ?: ""
        )
    }

    suspend fun getCurrentUserData(): UserData = userData.first()
}

data class UserData(
    val name: String,
    val email: String,
    val gender: String,
    val age: Int,
    val weight: Float,
    val height: Float,
    val fitnessGoal: String,
    val allergies: Set<String>,
    val restrictions: Set<String>,
    val phoneNumber: String = ""
)
