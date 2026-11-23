package com.example.homesphere.features.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ActivityDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days")
}

data class ActivityHistoryUiState(
    val events: List<ActivityEvent> = emptyList(),
    val selectedCategory: ActivityCategory? = null,
    val selectedDateFilter: ActivityDateFilter = ActivityDateFilter.ALL
)

class ActivityHistoryViewModel(
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ActivityCategory?>(null)
    private val selectedDateFilter = MutableStateFlow(ActivityDateFilter.ALL)

    val uiState: StateFlow<ActivityHistoryUiState> = combine(
        activityRepository.allEvents,
        selectedCategory,
        selectedDateFilter
    ) { all, category, dateFilter ->
        val cutoff = when (dateFilter) {
            ActivityDateFilter.ALL -> 0L
            ActivityDateFilter.TODAY -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            ActivityDateFilter.LAST_7_DAYS -> System.currentTimeMillis() - 7L * 86_400_000L
        }
        val filtered = all.filter { event ->
            (category == null || event.category == category) && event.timestamp >= cutoff
        }
        ActivityHistoryUiState(
            events = filtered,
            selectedCategory = category,
            selectedDateFilter = dateFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityHistoryUiState())

    fun selectCategory(category: ActivityCategory?) {
        selectedCategory.value = category
    }

    fun selectDateFilter(filter: ActivityDateFilter) {
        selectedDateFilter.value = filter
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            activityRepository.clearAll()
        }
    }

    class Factory(
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActivityHistoryViewModel(activityRepository) as T
    }
}
