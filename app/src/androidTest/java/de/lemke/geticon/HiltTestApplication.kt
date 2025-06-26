package de.lemke.geticon

import android.app.Application
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication

@HiltAndroidTest
class HiltTestApplication : Application() {
    // This class is intentionally empty.
    // Hilt will generate the necessary code for testing.
}
