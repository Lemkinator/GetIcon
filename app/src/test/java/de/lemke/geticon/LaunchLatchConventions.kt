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

/**
 * Finds launches and dialog shows in Kotlin source that bypass the common-utils launch latch.
 *
 * Konsist has no type resolution, so both rules match names: a raw launch by its function name, a dialog show by the
 * names in its receiver chain.
 */
internal object LaunchLatchConventions {
    private val rawLaunch = Regex("""\b(startActivity|startActivityForResult|startIntentSender|registerForActivityResult)\(""")
    private val showCall = Regex("""\.show\(""")
    private val dialogTypeName = Regex("""Dialog|BottomSheet""")
    private val showReceiverAllowlist = setOf("Snackbar", "Toast", "PopupMenu", "TipPopup")
    private val closingBrackets = mapOf(')' to '(', ']' to '[', '}' to '{', '>' to '<')

    /** The raw launch calls in [source], by function name. */
    fun rawLaunches(source: String): List<String> = rawLaunch.findAll(source).map { it.groupValues[1] }.toList()

    /**
     * The receiver chains of the `.show(` calls in [source], a file with [imports], that do not go through `showOnce`.
     *
     * A file that imports no dialog type is skipped. A call passes if a segment of its receiver chain names an
     * allowlisted type the file imports: `Toast.makeText(…).show()`, `popupMenu.show()`.
     */
    fun rawDialogShows(
        source: String,
        imports: Collection<String>,
    ): List<String> {
        val importedNames = imports.map { it.substringAfterLast('.') }
        if (importedNames.none(dialogTypeName::containsMatchIn)) return emptyList()
        val allowedReceivers = showReceiverAllowlist.intersect(importedNames.toSet())
        return showCall
            .findAll(source)
            .map { receiverChain(source, it.range.first) }
            .filterNot { chain -> chain.any { segment -> allowedReceivers.any(segment.replaceFirstChar(Char::uppercaseChar)::endsWith) } }
            .map { it.joinToString(".") }
            .toList()
    }

    /** The identifiers of the call chain that ends at the `.` at [dotIndex], from its root; arguments and `!!` are skipped. */
    private fun receiverChain(
        source: String,
        dotIndex: Int,
    ): List<String> {
        val end = source.skipCallSuffixes(dotIndex)
        val start = source.identifierStart(end)
        if (start == end) return emptyList()
        val segment = source.substring(start, end)
        val beforeAccess = source.skipWhitespaceBackward(start)
        val access = source.memberAccessLength(beforeAccess)
        return if (access == 0) listOf(segment) else receiverChain(source, beforeAccess - access) + segment
    }

    private fun String.skipWhitespaceBackward(end: Int): Int {
        var index = end
        while (index > 0 && this[index - 1].isWhitespace()) index--
        return index
    }

    /** Skips whitespace, `!!` and bracketed groups (arguments, lambdas, type arguments) that end at [end]. */
    private fun String.skipCallSuffixes(end: Int): Int {
        var index = skipWhitespaceBackward(end)
        while (index > 0 && (this[index - 1] == '!' || this[index - 1] in closingBrackets)) {
            index = if (this[index - 1] == '!') index - 1 else openingBracketIndex(index - 1)
            index = skipWhitespaceBackward(index)
        }
        return index
    }

    private fun String.openingBracketIndex(closeIndex: Int): Int {
        val close = this[closeIndex]
        val open = closingBrackets.getValue(close)
        var depth = 0
        var index = closeIndex
        while (index >= 0) {
            when (this[index]) {
                close -> depth++
                open -> if (--depth == 0) return index
            }
            index--
        }
        return 0
    }

    private fun String.identifierStart(end: Int): Int {
        var index = end
        while (index > 0 && (this[index - 1].isLetterOrDigit() || this[index - 1] == '_')) index--
        return index
    }

    /** The length of the `.` or `?.` that ends at [end], or 0. */
    private fun String.memberAccessLength(end: Int): Int =
        when {
            end >= 2 && substring(end - 2, end) == "?." -> 2
            end >= 1 && this[end - 1] == '.' -> 1
            else -> 0
        }
}
