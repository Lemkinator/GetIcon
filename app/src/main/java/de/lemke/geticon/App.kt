package de.lemke.geticon

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode

/**
 * Main entry point into the application process.
 * Registered in the AndroidManifest.xml file.
 */
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initCommonUtilsSettingsAndSetDarkMode()
        // Clean up any temp APK files leaked by a previous crash (normal cleanup is in IconViewModel.onCleared)
        cacheDir
            .listFiles { _, name -> name.startsWith("extractIcon") && name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
