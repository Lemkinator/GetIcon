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
import de.lemke.geticon.data.IconRenderer
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import javax.inject.Inject

data class GeneratedIcon(
    val bitmap: Bitmap,
    val kind: IconKind,
    val fileName: String,
)

class GenerateIconUseCase @Inject constructor(
    private val renderer: IconRenderer,
) {
    /** Renders the icon of [applicationInfo] in [style], with the name of the file it exports to. */
    operator fun invoke(
        applicationInfo: ApplicationInfo,
        style: IconStyle,
    ): GeneratedIcon {
        val rendered = renderer.render(applicationInfo, style)
        return GeneratedIcon(rendered.bitmap, rendered.kind, fileName(applicationInfo.packageName, style))
    }

    private fun fileName(
        packageName: String,
        style: IconStyle,
    ): String = "${packageName}_${if (style.maskEnabled) "mask" else "default"}${if (style.colorEnabled) "_mono" else ""}"
}
