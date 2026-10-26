package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.UserProfile

/** 历史屏：按用户筛选 + 测量列表（趋势图直接用升序序列画）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(container: AppContainer) : ViewModel() {

    val users: StateFlow<List<UserProfile>> =
        container.userRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null = 全部用户。 */
    private val _selectedUserId = MutableStateFlow<Long?>(null)
    val selectedUserId: StateFlow<Long?> = _selectedUserId.asStateFlow()

    /** measured_at 降序（列表用）；趋势图另行按时间升序排列。 */
    val measurements: StateFlow<List<Measurement>> =
        _selectedUserId.flatMapLatest { id ->
            if (id == null) container.measurementRepository.getAll()
            else container.measurementRepository.forUser(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectUser(userId: Long?) {
        _selectedUserId.value = userId
    }
}

/** 趋势图数据：按时间升序（Canvas 折线自左向右）。 */
fun List<Measurement>.toTrendSeries(): List<Measurement> = sortedBy { it.measuredAt }
