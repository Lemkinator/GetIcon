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

package de.lemke.geticon.domain.model

import android.graphics.Bitmap

data class IconStyle(
    val size: Int,
    val maskEnabled: Boolean,
    val colorEnabled: Boolean,
    val foregroundColor: Int,
    val backgroundColor: Int,
)

/** What an app icon supports: a mask needs an adaptive or tray-masked icon, a tint needs adaptive layers. */
enum class IconKind(
    val canMask: Boolean,
    val canTint: Boolean,
) {
    ADAPTIVE(canMask = true, canTint = true),
    MASKABLE(canMask = true, canTint = false),
    LEGACY(canMask = false, canTint = false),
}

data class RenderedIcon(
    val bitmap: Bitmap,
    val kind: IconKind,
)
