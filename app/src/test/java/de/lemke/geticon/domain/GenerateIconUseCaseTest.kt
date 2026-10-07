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

package de.lemke.geticon.domain

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import de.lemke.geticon.data.FakeIconRenderer
import de.lemke.geticon.data.FakeIconRenderer.Render
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.io.IOException

class GenerateIconUseCaseTest : ShouldSpec(
    {
        val bitmap = mockk<Bitmap>()
        val app = ApplicationInfo().apply { packageName = "com.example.app" }
        val style = IconStyle(size = 256, maskEnabled = true, colorEnabled = false, foregroundColor = -1, backgroundColor = -16547330)

        should("return the rendered icon with its kind, label and export file name") {
            val renderer =
                FakeIconRenderer(bitmap).apply {
                    kind = IconKind.MASKABLE
                    label = "Example"
                }
            GenerateIconUseCase(renderer)(app, style) shouldBe GeneratedIcon(bitmap, IconKind.MASKABLE, "Example", "com.example.app_mask")
            renderer.renders shouldBe listOf(Render(app, style))
        }

        listOf(
            Triple(true, false, "com.example.app_mask"),
            Triple(false, false, "com.example.app_default"),
            Triple(true, true, "com.example.app_mask_mono"),
            Triple(false, true, "com.example.app_default_mono"),
        ).forEach { (mask, color, fileName) ->
            should("name the export file $fileName for mask=$mask color=$color") {
                val icon = GenerateIconUseCase(FakeIconRenderer(bitmap))(app, style.copy(maskEnabled = mask, colorEnabled = color))
                icon.fileName shouldBe fileName
            }
        }

        should("propagate a render failure") {
            val renderer = FakeIconRenderer(bitmap).apply { failure = IOException("render failed") }
            shouldThrow<IOException> { GenerateIconUseCase(renderer)(app, style) }.message shouldBe "render failed"
        }
    },
)
