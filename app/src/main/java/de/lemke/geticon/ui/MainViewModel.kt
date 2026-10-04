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
import androidx.picker.model.AppInfoData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.commonutils.domain.GetApplicationInfoUseCase
import de.lemke.commonutils.domain.GetInstalledAppsUseCase
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class MainEvent {
    data class NavigateToApkIcon(val applicationInfo: ApplicationInfo) : MainEvent()

    data object ShowError : MainEvent()

    data object ShowLoadError : MainEvent()
}

/** The lookup of a picked app. The activity acts on a [Result] and then reports it handled. */
sealed interface AppLookup {
    sealed interface Result : AppLookup

    data object Idle : AppLookup

    data object Running : AppLookup

    data class Found(val applicationInfo: ApplicationInfo) : Result

    data object NotFound : Result
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val processApk: ProcessApkUseCase,
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val getApplicationInfo: GetApplicationInfoUseCase,
) : ViewModel() {
    private val _events = Channel<MainEvent>(BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    val installedApps: StateFlow<List<AppInfoData>>
        field = MutableStateFlow<List<AppInfoData>>(emptyList())

    val appLookup: StateFlow<AppLookup>
        field = MutableStateFlow<AppLookup>(AppLookup.Idle)

    init {
        viewModelScope.launch { loadInstalledApps() }
    }

    private suspend fun loadInstalledApps() {
        runCatching { installedApps.value = getInstalledApps() }.onFailure { e ->
            if (e is CancellationException) throw e
            _events.send(MainEvent.ShowLoadError)
        }
    }

    fun onAppSelected(packageName: String) {
        if (appLookup.value == AppLookup.Running) return
        appLookup.value = AppLookup.Running
        viewModelScope.launch {
            appLookup.value = getApplicationInfo(packageName)?.let(AppLookup::Found) ?: AppLookup.NotFound
        }
    }

    fun onAppLookupHandled(result: AppLookup.Result) {
        appLookup.update { if (it == result) AppLookup.Idle else it }
    }

    fun onApkPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val event =
                when (val result = processApk(uri)) {
                    is ApkProcessResult.Success -> MainEvent.NavigateToApkIcon(result.applicationInfo)
                    is ApkProcessResult.InvalidApk, is ApkProcessResult.Error -> MainEvent.ShowError
                }
            _events.send(event)
        }
    }
}
