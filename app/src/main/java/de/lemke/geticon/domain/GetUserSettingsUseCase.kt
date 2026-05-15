package de.lemke.geticon.domain

import de.lemke.geticon.data.UserSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) {
    suspend operator fun invoke() =
        withContext(Dispatchers.Default) {
            userSettingsRepository.getSettings()
        }
}
