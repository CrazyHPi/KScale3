package xyz.crazyh.kscale3.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.crazyh.kscale3.di.AppContainer
import xyz.crazyh.kscale3.domain.Measurement
import xyz.crazyh.kscale3.domain.UserProfile

/**
 * 档案页（历史 + 成员）共享状态：一次取全部成员与全部测量，在内存里按成员筛选。
 * 设计稿没有"全部成员"视角——历史始终是"某位成员的历史"，未选择时默认第一位。
 */
class ProfileViewModel(container: AppContainer) : ViewModel() {

    val users: StateFlow<List<UserProfile>> =
        container.userRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allMeasurements: StateFlow<List<Measurement>> =
        container.measurementRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedUserId = MutableStateFlow<Long?>(null)

    /** 当前选中的成员；选中项被删除或尚未选择时回落到第一位。 */
    val selectedUser: StateFlow<UserProfile?> =
        combine(users, _selectedUserId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 当前成员的全部测量（measuredAt 降序）。 */
    val measurements: StateFlow<List<Measurement>> =
        combine(allMeasurements, selectedUser) { all, user ->
            if (user == null) emptyList()
            else all.filter { it.userId == user.id }.sortedByDescending { it.measuredAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 每位成员最近一次测量（成员卡"最近称重"）。 */
    val latestByUser: StateFlow<Map<Long, Measurement>> =
        allMeasurements
            .map { all ->
                all.sortedByDescending { it.measuredAt }
                    .groupBy { it.userId }
                    .mapValues { (_, list) -> list.first() }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun selectUser(userId: Long) {
        _selectedUserId.value = userId
    }
}
