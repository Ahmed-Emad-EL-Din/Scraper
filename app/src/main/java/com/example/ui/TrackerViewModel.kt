package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TrackingRule
import com.example.data.TrackingRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackingRuleRepository

    // Modern StateFlow collection matching the Room Database integration guidelines
    val activeRules: StateFlow<List<TrackingRule>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TrackingRuleRepository(database.trackingRuleDao())
        activeRules = repository.allRules
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Add tracking rule to the database (coroutine scope handled)
     */
    fun addRule(url: String, cssSelector: String?, isPremium: Boolean, aiPrompt: String? = null) {
        viewModelScope.launch {
            repository.insertRule(
                TrackingRule(
                    url = url,
                    cssSelector = cssSelector,
                    lastKnownText = "Waiting for check",
                    isPremiumRule = isPremium,
                    aiPrompt = aiPrompt
                )
            )
        }
    }

    /**
     * Delete tracking rule by its database primary ID
     */
    fun deleteRule(ruleId: Int) {
        viewModelScope.launch {
            repository.deleteRuleById(ruleId)
        }
    }

    // Factory pattern to simplify construction in compose views
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
                return TrackerViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
