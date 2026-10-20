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
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

private val mockedType =
    Regex("""\b(?:mockk|spyk|mockkClass|mockkStatic|mockkObject|mockkConstructor)\s*(?:<\s*|\(\s*)([A-Za-z_][\w.]*)""")
private val annotatedMockType =
    Regex("""@(?:MockK|RelaxedMockK|SpyK)\b[^\n]*?\bva[lr]\s+\w+\s*:\s*([A-Za-z_][\w.]*)""")

/**
 * Fails on a test that mocks a type declared in this project. MockK belongs at external boundaries only; own types
 * get a fake.
 *
 * It covers `mockk`, `spyk`, `mockkClass`, `mockkStatic`, `mockkObject` and `mockkConstructor` calls and `@MockK`,
 * `@RelaxedMockK` and `@SpyK` properties. Konsist has no type resolution, so the mocked name resolves through the
 * file's explicit imports, its package and its wildcard imports against the classes, interfaces and objects the
 * whole project declares. Not detected: a type alias of an own type and a mocked type taken from a variable.
 */
fun KoScope.assertNoMockedOwnTypes(projectScope: KoScope = Konsist.scopeFromProject()) {
    val ownTypes = projectScope.files.flatMap { it.declaredTypeNames() }.toSet()
    val hits =
        files
            .flatMap { file ->
                file.mockedTypes().filter { (_, name) -> file.resolveNames(name).any { it in ownTypes } }.map { (line, name) ->
                    "${file.projectPath.replace('\\', '/')}:$line: mocks own type $name"
                }
            }.sorted()
    if (hits.isNotEmpty()) throw AssertionError(hits.joinToString("\n", prefix = "${hits.size} mocked own type(s), use a fake:\n"))
}

private fun KoFileDeclaration.declaredTypeNames(): List<String> =
    classes(includeNested = true).mapNotNull { it.fullyQualifiedName } +
        interfaces(includeNested = true).mapNotNull { it.fullyQualifiedName } +
        objects(includeNested = true).mapNotNull { it.fullyQualifiedName }

private fun KoFileDeclaration.mockedTypes(): List<Pair<Int, String>> =
    (mockedType.findAll(text) + annotatedMockType.findAll(text))
        .map { match ->
            1 + text.substring(0, match.range.first).count { it == '\n' } to match.groupValues[1]
        }.toList()

private fun KoFileDeclaration.resolveNames(name: String): List<String> {
    val head = name.substringBefore('.')
    val tail = name.removePrefix(head)
    val named = imports.filter { !it.isWildcard && (it.alias?.name ?: it.name.substringAfterLast('.')) == head }
    return when {
        named.isNotEmpty() -> {
            named.map { it.name + tail }
        }

        else -> {
            listOf(name, "${packagee?.name.orEmpty()}.$name") +
                imports.filter { it.isWildcard }.map { "${it.name.removeSuffix(".*").removeSuffix(".")}.$name" }
        }
    }
}
