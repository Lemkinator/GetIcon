/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.geticon.ui

import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val _events = Channel<MainEvent>(BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    fun onApkPicked(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch { _events.send(MainEvent.ShowError) }
            return
        }
        viewModelScope.launch {
            val event =
                when (val result = processApk(uri)) {
                    is ApkProcessResult.Success -> MainEvent.NavigateToIcon(result.applicationInfo)
                    is ApkProcessResult.InvalidApk, is ApkProcessResult.Error -> MainEvent.ShowError
                }
            _events.send(event)
        }
    }
}
