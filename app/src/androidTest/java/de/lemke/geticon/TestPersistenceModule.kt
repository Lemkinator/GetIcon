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

package de.lemke.geticon

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import org.junit.rules.TemporaryFolder

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PersistenceModule::class])
object TestPersistenceModule {
    @Provides
    @Singleton
    fun provideTestDataStore(tmpFolder: TemporaryFolder): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            // File(tmpFolder.root, name) not tmpFolder.newFile() — newFile() creates the file
            // immediately, which conflicts with DataStore's own creation logic.
            File(tmpFolder.root, "test_user_settings.preferences_pb")
        }
}
