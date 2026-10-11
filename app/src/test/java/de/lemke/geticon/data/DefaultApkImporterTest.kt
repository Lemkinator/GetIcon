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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.App
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
class DefaultApkImporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val importer = DefaultApkImporter(context)
    private val documents = Robolectric.setupContentProvider(DocumentProvider::class.java, AUTHORITY)
    private val documentDir = File(context.filesDir, "documents").apply { mkdirs() }

    @After
    fun tearDown() {
        documentDir.deleteRecursively()
        context.cacheDir
            .listFiles()
            .orEmpty()
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun `creates an empty APK file in the app cache`() {
        val file = importer.createCacheFile()
        file.parentFile shouldBe context.cacheDir
        file.name shouldStartWith "extractIcon"
        file.name shouldEndWith ".apk"
        file.length() shouldBe 0L
    }

    @Test
    fun `opens the picked document through its provider`() {
        val uri = documents.add(File(documentDir, "picked.apk").apply { writeText("apk bytes") })
        importer.open(uri)?.reader()?.use { it.readText() } shouldBe "apk bytes"
    }

    @Test
    fun `returns null when the provider has no content`() {
        importer.open(Uri.parse("content://$AUTHORITY/missing")) shouldBe null
    }

    @Test
    fun `reads the application of a parsed archive`() {
        val apk = File(context.applicationInfo.publicSourceDir).copyTo(File(context.cacheDir, "app.apk"))
        importer.readApplicationInfo(apk)?.packageName shouldBe context.packageName
    }

    @Test
    fun `returns null for a file that is no archive`() {
        val apk = File(context.cacheDir, "app.apk").apply { writeText("no archive") }
        importer.readApplicationInfo(apk) shouldBe null
    }

    @Test
    fun `treats a created cache file as cached`() {
        importer.isCached(importer.createCacheFile()) shouldBe true
    }

    @Test
    fun `treats a file outside the cache as not cached`() {
        importer.isCached(File(documentDir, "installed.apk")) shouldBe false
    }

    @Test
    fun `treats a path that cannot be resolved as not cached`() {
        importer.isCached(File(context.cacheDir, "invalid\u0000.apk")) shouldBe false
    }

    @Test
    fun `discards a cached APK`() {
        val apk = importer.createCacheFile()
        importer.discard(apk)
        apk.exists() shouldBe false
    }

    @Test
    fun `keeps a file outside the cache on discard`() {
        val outside = File(documentDir, "installed.apk").apply { createNewFile() }
        importer.discard(outside)
        outside.exists() shouldBe true
    }

    class DocumentProvider : ContentProvider() {
        private val files = mutableMapOf<Uri, File>()

        fun add(file: File): Uri = Uri.parse("content://$AUTHORITY/${file.name}").also { files[it] = file }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor? = files[uri]?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "de.lemke.geticon.test.documents"
    }
}
