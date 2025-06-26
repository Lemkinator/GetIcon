package de.lemke.geticon.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.lemke.geticon.PersistenceModule // Keep this for app-specific datastore if needed elsewhere
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestAppModule {

    // Provides the DataStore for UserSettingsRepository (app's own settings)
    @Provides
    @Singleton
    fun provideUserSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("test_app_settings") }
        )
    }

    // Provides the DataStore that common-utils expects by its specific name
    @Provides
    @Singleton
    @Named("commonUtilsDataStore") // Name it so we can refer to it if needed, though common-utils finds it by file name
    fun provideCommonUtilsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        // This DataStore instance will be used by common-utils if it's written to
        // the "de.lemke.commonutils.preferences" file.
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("de.lemke.commonutils.preferences") }
        )
    }
}
