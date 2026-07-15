import os

path = "app/src/main/java/com/unitytunnel/app/viewmodel/BalanceViewModel.kt"
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    'private val _lowDataMode = MutableStateFlow(false)\n    val lowDataMode: StateFlow<Boolean> = _lowDataMode.asStateFlow()',
    'private val _lowDataMode = MutableStateFlow(false)\n    val lowDataMode: StateFlow<Boolean> = _lowDataMode.asStateFlow()\n\n    private val _onboardingCompleted = MutableStateFlow(false)\n    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()'
)

content = content.replace(
    '_lowDataMode.value = preferencesManager.lowDataMode.first()',
    '_lowDataMode.value = preferencesManager.lowDataMode.first()\n            _onboardingCompleted.value = preferencesManager.onboardingCompleted.first()'
)

content = content.replace(
    'fun setLowDataMode(enabled: Boolean) {\n        viewModelScope.launch {\n            _lowDataMode.value = enabled\n            preferencesManager.setLowDataMode(enabled)\n        }\n    }',
    'fun setLowDataMode(enabled: Boolean) {\n        viewModelScope.launch {\n            _lowDataMode.value = enabled\n            preferencesManager.setLowDataMode(enabled)\n        }\n    }\n\n    fun setOnboardingCompleted(completed: Boolean) {\n        viewModelScope.launch {\n            _onboardingCompleted.value = completed\n            preferencesManager.setOnboardingCompleted(completed)\n        }\n    }'
)

with open(path, "w") as f:
    f.write(content)
