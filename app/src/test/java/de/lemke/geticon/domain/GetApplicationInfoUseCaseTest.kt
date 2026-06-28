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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetApplicationInfoUseCaseTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        lateinit var useCase: GetApplicationInfoUseCase

        beforeEach {
            every { context.packageManager } returns packageManager
            useCase = GetApplicationInfoUseCase(context)
        }

        should("return ApplicationInfo when package exists") {
            val appInfo = ApplicationInfo().also { it.packageName = "com.example.test" }
            every { packageManager.getApplicationInfo("com.example.test", 0) } returns appInfo
            useCase("com.example.test") shouldBe appInfo
        }

        should("return null when package is not found") {
            every {
                packageManager.getApplicationInfo(any(), 0)
            } throws PackageManager.NameNotFoundException("not found")
            useCase("com.nonexistent.pkg") shouldBe null
        }
    },
)
