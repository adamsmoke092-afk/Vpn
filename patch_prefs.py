import os

path = "app/src/main/java/com/unitytunnel/app/data/PreferencesManager.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    'val KEY_LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")',
    'val KEY_LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")\n        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")'
)

content = content.replace(
    'val lowDataMode: Flow<Boolean> = context.dataStore.data\n        .catch { exception ->\n            if (exception is IOException) emit(emptyPreferences()) else throw exception\n        }.map { prefs ->\n            prefs[KEY_LOW_DATA_MODE] ?: false\n        }',
    'val lowDataMode: Flow<Boolean> = context.dataStore.data\n        .catch { exception ->\n            if (exception is IOException) emit(emptyPreferences()) else throw exception\n        }.map { prefs ->\n            prefs[KEY_LOW_DATA_MODE] ?: false\n        }\n\n    val onboardingCompleted: Flow<Boolean> = context.dataStore.data\n        .catch { exception ->\n            if (exception is IOException) emit(emptyPreferences()) else throw exception\n        }.map { prefs ->\n            prefs[KEY_ONBOARDING_COMPLETED] ?: false\n        }'
)

content = content.replace(
    'suspend fun setLowDataMode(enabled: Boolean) {\n        context.dataStore.edit { prefs ->\n            prefs[KEY_LOW_DATA_MODE] = enabled\n        }\n    }',
    'suspend fun setLowDataMode(enabled: Boolean) {\n        context.dataStore.edit { prefs ->\n            prefs[KEY_LOW_DATA_MODE] = enabled\n        }\n    }\n\n    suspend fun setOnboardingCompleted(completed: Boolean) {\n        context.dataStore.edit { prefs ->\n            prefs[KEY_ONBOARDING_COMPLETED] = completed\n        }\n    }'
)

with open(path, "w") as f:
    f.write(content)
