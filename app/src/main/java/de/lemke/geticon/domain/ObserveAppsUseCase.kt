package de.lemke.geticon.domain

import de.lemke.geticon.data.AppsRepository
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveAppsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val apps: AppsRepository,
) {
    operator fun invoke(searchQuery: Flow<String?>): Flow<List<String>> =
        combine(searchQuery, userSettingsRepository.observeShowSystemApps()) { query, showSystemApps ->
            if (query != null) {
                if (query.isBlank()) apps.get()
                else apps.get().filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            } else {
                if (showSystemApps) apps.get()
                else apps.get().filterNot { it.isSystemApp }
            }.map { it.packageName }
        }.flowOn(Dispatchers.Default)
}

