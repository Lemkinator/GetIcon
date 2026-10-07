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

package de.lemke.geticon.data

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import de.lemke.geticon.domain.model.RenderedIcon

/** Renders every icon as [bitmap] of [kind] labeled [label], or throws [failure] once set, and records each render. */
internal class FakeIconRenderer(
    private val bitmap: Bitmap,
) : IconRenderer {
    var kind: IconKind = IconKind.ADAPTIVE
    var label: String = "Example App"
    var failure: Throwable? = null
    val renders = mutableListOf<Render>()

    override fun render(
        applicationInfo: ApplicationInfo,
        style: IconStyle,
    ): RenderedIcon {
        renders += Render(applicationInfo, style)
        failure?.let { throw it }
        return RenderedIcon(bitmap, kind, label)
    }

    data class Render(
        val applicationInfo: ApplicationInfo,
        val style: IconStyle,
    )
}
