package com.kaspercito.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.kaspercito.domain.data.entities.NotificationEntity
import com.kaspercito.domain.repository.CommonRepository
import com.kaspercito.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(
    commonRepository: CommonRepository,
) : BaseViewModel() {
    private var _listNotification: MutableStateFlow<List<NotificationEntity>?> =
        MutableStateFlow(null)
    val listNotification: StateFlow<List<NotificationEntity>?> = _listNotification

    init {
        viewModelScope.launch {
            commonRepository.getAllNotifications().collect { notificationEntities ->
                _listNotification.value =
                    notificationEntities
                        // MaKaDiMi: las entradas del blog del autor original (guardadas por versiones
                        // anteriores) son publicidad; aquí solo van los lanzamientos de tus artistas.
                        ?.filterNot { it.type == NotificationEntity.TYPE_BLOG }
                        ?.sortedByDescending {
                            it.time
                        }
            }
        }
    }
}