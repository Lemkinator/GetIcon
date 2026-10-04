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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The installed apps the picker lists. The activity reports a [Failed] load once and then marks it handled. */
sealed interface InstalledApps {
    data object Loading : InstalledApps

    data class Loaded(val apps: List<AppInfoData>) : InstalledApps

    data class Failed(val handled: Boolean = false) : InstalledApps
}

/** The import of a picked APK file. The activity acts on a [Result] and then reports it handled. */
sealed interface ApkImport {
    sealed interface Result : ApkImport

    data object Idle : ApkImport

    data class Imported(val applicationInfo: ApplicationInfo) : Result

    data object Invalid : Result
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
    val installedApps: StateFlow<InstalledApps>
        field = MutableStateFlow<InstalledApps>(InstalledApps.Loading)

    val apkImport: StateFlow<ApkImport>
        field = MutableStateFlow<ApkImport>(ApkImport.Idle)

    val appLookup: StateFlow<AppLookup>
        field = MutableStateFlow<AppLookup>(AppLookup.Idle)

    init {
        viewModelScope.launch { loadInstalledApps() }
    }

    private suspend fun loadInstalledApps() {
        runCatching { installedApps.value = InstalledApps.Loaded(getInstalledApps()) }.onFailure { e ->
            if (e is CancellationException) throw e
            installedApps.value = InstalledApps.Failed()
        }
    }

    fun onInstalledAppsFailureHandled() {
        installedApps.update { if (it is InstalledApps.Failed) InstalledApps.Failed(handled = true) else it }
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
            apkImport.value =
                when (val result = processApk(uri)) {
                    is ApkProcessResult.Success -> ApkImport.Imported(result.applicationInfo)
                    is ApkProcessResult.InvalidApk, is ApkProcessResult.Error -> ApkImport.Invalid
                }
        }
    }

    fun onApkImportHandled(result: ApkImport.Result) {
        apkImport.update { if (it == result) ApkImport.Idle else it }
    }
}
