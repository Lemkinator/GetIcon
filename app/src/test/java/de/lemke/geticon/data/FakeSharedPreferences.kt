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

import android.content.SharedPreferences

/** Sentinel marking a pending removal in [FakeSharedPreferences.FakeEditor.pending]. */
private object Removed

/**
 * Pure-JVM analog of Robolectric's real SharedPreferences; exists only because
 * [de.lemke.geticon.ui.IconViewModelTest] deliberately trades Robolectric for JVM speed and has
 * no Context available to back a real store.
 */
class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = (map[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = map[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = map[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = map[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners += it }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners -= it }
    }

    private inner class FakeEditor : SharedPreferences.Editor {
        // Single ordered map, keyed like real SharedPreferences.EditorImpl.mModified: put/remove on the
        // same key just overwrite this map's entry, so the last call wins regardless of call order.
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(
            key: String?,
            value: String?,
        ) = apply { key?.let { pending[it] = value } }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ) = apply { key?.let { pending[it] = values?.toSet() } }

        override fun putInt(
            key: String?,
            value: Int,
        ) = apply { key?.let { pending[it] = value } }

        override fun putLong(
            key: String?,
            value: Long,
        ) = apply { key?.let { pending[it] = value } }

        override fun putFloat(
            key: String?,
            value: Float,
        ) = apply { key?.let { pending[it] = value } }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ) = apply { key?.let { pending[it] = value } }

        override fun remove(key: String?) = apply { key?.let { pending[it] = Removed } }

        // Real Editor.clear() also drops this editor's own pending puts/removes issued so far -
        // only ones issued after clear() (in call order) survive to apply().
        override fun clear() =
            apply {
                pending.clear()
                clearAll = true
            }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun applyChanges() {
            if (clearAll) map.clear()
            pending.forEach { (key, value) -> if (value === Removed) map.remove(key) else map[key] = value }
            listeners.forEach { listener ->
                pending.keys.forEach { key -> listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
            }
        }
    }
}
