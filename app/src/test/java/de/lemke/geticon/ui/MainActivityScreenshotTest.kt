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

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import java.net.URL
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityScreenshotTest {
    @Before
    fun setup() {
        ApplicationProvider.getApplicationContext<HiltTestApplication>().initCommonUtilsSettingsAndSetDarkMode()
        commonUtilsSettings.lastVersionCode = Int.MAX_VALUE
        commonUtilsSettings.acceptedTosVersion = Int.MAX_VALUE
    }

    @Test
    fun mainActivity_default() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/main_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun mainActivity_default_dark() {
        installFakeApps()
        ActivityScenario.launch(MainActivity::class.java).use {
            @Suppress("MagicNumber")
            Thread.sleep(500)
            onView(isRoot()).captureRoboImage("src/test/screenshots/main_default_dark.png")
        }
    }

    @Suppress("DEPRECATION")
    private fun installFakeApps() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(context.packageManager)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val fakeApps =
            listOf(
                // My apps
                Pair("Sudoku", "https://raw.githubusercontent.com/Lemkinator/Sudoku/main/img/Sudoku_squircle.png"),
                Pair("OneURL", "https://raw.githubusercontent.com/Lemkinator/OneUrl/main/img/OneURL_squircle.png"),
                Pair("NAKBuch", "https://raw.githubusercontent.com/Lemkinator/nakbuch_lite_web/main/icons/Icon-squircle-512.png"),
                // Popular open source apps — icons via F-Droid CDN
                Pair("Aegis", "https://f-droid.org/repo/com.beemdevelopment.aegis/en-US/icon.png"),
                Pair("Catima", "https://f-droid.org/repo/me.hackerchick.catima/en-US/icon.png"),
                Pair("Conversations", "https://f-droid.org/repo/eu.siacs.conversations/en-US/icon.png"),
                Pair("Element", "https://f-droid.org/repo/im.vector.app/en-US/icon.png"),
                Pair("F-Droid", "https://f-droid.org/repo/org.fdroid.fdroid/en-US/icon.png"),
                Pair("Feeder", "https://f-droid.org/repo/com.nononsenseapps.feeder/en-US/icon.png"),
                Pair("Fritter", "https://f-droid.org/repo/com.jonjomckay.fritter/en-US/icon.png"),
                Pair("Grocy", "https://f-droid.org/repo/xyz.zedler.patrick.grocy/en-US/icon.png"),
                Pair("K-9 Mail", "https://f-droid.org/repo/com.fsck.k9/en-US/icon.png"),
                Pair("Kiwix", "https://f-droid.org/repo/org.kiwix.kiwixmobile/en-US/icon.png"),
                Pair("Nextcloud", "https://f-droid.org/repo/com.nextcloud.client/en-US/icon.png"),
                Pair("NewPipe", "https://f-droid.org/repo/org.schabi.newpipe/en-US/icon.png"),
                Pair("Open Food Facts", "https://f-droid.org/repo/openfoodfacts.github.scrachx.openfood/en-US/icon.png"),
                Pair("Organic Maps", "https://f-droid.org/repo/app.organicmaps/en-US/icon.png"),
                Pair("OsmAnd", "https://f-droid.org/repo/net.osmand.plus/en-US/icon.png"),
                Pair("ReadYou", "https://f-droid.org/repo/me.ash.reader/en-US/icon.png"),
                Pair("Simple Calendar", "https://f-droid.org/repo/com.simplemobiletools.calendar.pro/en-US/icon.png"),
                Pair("Simple Gallery", "https://f-droid.org/repo/com.simplemobiletools.gallery.pro/en-US/icon.png"),
                Pair("Tasks", "https://f-droid.org/repo/org.tasks/en-US/icon.png"),
                Pair("Telegram", "https://f-droid.org/repo/org.telegram.messenger/en-US/icon.png"),
                Pair("Tutanota", "https://f-droid.org/repo/de.tutao.tutanota/en-US/icon.png"),
                Pair("Tusky", "https://f-droid.org/repo/com.keylesspalace.tusky/en-US/icon.png"),
                Pair("VLC", "https://f-droid.org/repo/org.videolan.vlc/en-US/icon.png"),
                Pair("Wire", "https://f-droid.org/repo/com.wire/en-US/icon.png"),
            )
        shadowPm.setResolveInfosForIntent(
            launcherIntent,
            fakeApps.map { (label, _) ->
                ResolveInfo().apply {
                    nonLocalizedLabel = label
                    activityInfo =
                        ActivityInfo().apply {
                            packageName = label
                            name = "$label.MainActivity"
                            applicationInfo =
                                ApplicationInfo().apply {
                                    packageName = label
                                    nonLocalizedLabel = label
                                    flags = ApplicationInfo.FLAG_INSTALLED
                                }
                        }
                }
            },
        )
        fakeApps.forEach { (label, url) ->
            val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
            shadowPm.addActivityIcon(ComponentName(label, "$label.MainActivity"), BitmapDrawable(context.resources, bitmap))
        }
    }
}
