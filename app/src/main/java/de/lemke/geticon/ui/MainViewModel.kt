package de.lemke.geticon.ui

import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.geticon.domain.ProcessApkUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.launch

sealed class MainEvent {
    data class NavigateToIcon(
        val applicationInfo: ApplicationInfo,
    ) : MainEvent()

    data object ShowError : MainEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val processApk: ProcessApkUseCase,
) : ViewModel() {
    val events = Channel<MainEvent>(BUFFERED)

    fun onApkPicked(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch { events.send(MainEvent.ShowError) }
            return
        }
        viewModelScope.launch {
            val applicationInfo = processApk(uri)
            events.send(if (applicationInfo != null) MainEvent.NavigateToIcon(applicationInfo) else MainEvent.ShowError)
        }
    }
}
