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

import com.lemonappdev.konsist.api.Konsist
import de.lemke.commonutils.assertClassBodyOrder
import de.lemke.commonutils.assertHiltTestRuleConventions
import de.lemke.commonutils.assertLaunchLatchConventions
import de.lemke.commonutils.assertNoCommonUtilsTypeExtensions
import io.kotest.core.spec.style.ShouldSpec

class CodingConventionsTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
        should("launch activities and show dialogs only through the launch latch") {
            codeScope.assertLaunchLatchConventions()
        }
        should("order class bodies consistently") {
            codeScope.assertClassBodyOrder()
        }
        should("declare no extension on a common-utils type") {
            codeScope.assertNoCommonUtilsTypeExtensions()
        }
        should("declare the Hilt rule of every Hilt test through HiltTestRule") {
            Konsist.scopeFromTest().assertHiltTestRuleConventions(requireCloseRuleOnPlainHiltApplicationTests = true)
        }
    }
}
