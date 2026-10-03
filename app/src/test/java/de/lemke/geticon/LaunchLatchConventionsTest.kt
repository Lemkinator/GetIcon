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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LaunchLatchConventionsTest : ShouldSpec() {
    private val dialogImports = listOf("androidx.appcompat.app.AlertDialog")

    init {
        should("report every raw launch by name") {
            val source =
                """
                startActivity(intent)
                activity.startActivityForResult(intent, 1)
                startIntentSender(sender, null, 0, 0, 0)
                private val picker = registerForActivityResult(GetContent()) { }
                """.trimIndent()

            LaunchLatchConventions.rawLaunches(source) shouldBe
                listOf("startActivity", "startActivityForResult", "startIntentSender", "registerForActivityResult")
        }
        should("accept the launch latch helpers") {
            val source =
                """
                singleLaunchActivity(intent)
                safeStartActivity(intent)
                private val picker = registerForSingleLaunchResult(GetContent()) { }
                """.trimIndent()

            LaunchLatchConventions.rawLaunches(source).shouldBeEmpty()
        }
        should("report a show of a dialog variable") {
            LaunchLatchConventions.rawDialogShows("dialog.show()", dialogImports) shouldBe listOf("dialog")
        }
        should("report a multi-line builder show by its receiver chain") {
            val source =
                """
                AlertDialog
                    .Builder(this)
                    .setTitle(R.string.title)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .show()
                """.trimIndent()

            LaunchLatchConventions.rawDialogShows(source, dialogImports) shouldBe
                listOf("AlertDialog.Builder.setTitle.setPositiveButton")
        }
        should("report a show behind safe calls and non-null assertions") {
            LaunchLatchConventions.rawDialogShows("sheet?.dialog!!.show()", dialogImports) shouldBe listOf("sheet.dialog")
        }
        should("accept showOnce") {
            LaunchLatchConventions.rawDialogShows("dialog.showOnce(TAG)", dialogImports).shouldBeEmpty()
        }
        should("accept a show whose receiver chain names an imported allowlisted type") {
            val source =
                """
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
                Snackbar.make(view, R.string.done, Snackbar.LENGTH_SHORT).show()
                binding.sortPopupMenu.show()
                tipPopup.show(TipPopup.Direction.DEFAULT)
                """.trimIndent()
            val imports =
                dialogImports +
                    listOf(
                        "android.widget.Toast",
                        "com.google.android.material.snackbar.Snackbar",
                        "android.widget.PopupMenu",
                        "dev.oneuiproject.oneui.widget.TipPopup",
                    )

            LaunchLatchConventions.rawDialogShows(source, imports).shouldBeEmpty()
        }
        should("report an allowlisted name that the file does not import") {
            val source = "Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()"

            LaunchLatchConventions.rawDialogShows(source, dialogImports) shouldBe listOf("Toast.makeText")
        }
        should("skip a file that imports no dialog type") {
            LaunchLatchConventions.rawDialogShows("dialog.show()", listOf("android.widget.Toast")).shouldBeEmpty()
        }
        should("treat a bottom sheet import as a dialog type") {
            val imports = listOf("com.google.android.material.bottomsheet.BottomSheetDialogFragment")

            LaunchLatchConventions.rawDialogShows("sheet.show(supportFragmentManager, null)", imports) shouldBe listOf("sheet")
        }
    }
}
