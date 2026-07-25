package com.bitchat.android.nyaya

import androidx.test.core.app.ApplicationProvider
import com.bitchat.android.nyaya.ai.ModelDownloadManager
import com.bitchat.android.nyaya.ai.NyayaModelCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Guards the truncated-download fix.
 *
 * A Gemma 4 bundle is 2.4-3.4 GB, so an interrupted transfer is routine. Before
 * this fix any non-empty file counted as "downloaded", which meant a partial
 * bundle was promoted to the final name and the engine could never load it —
 * with no way for the user to see why. A model is now only considered present
 * when its size matches the catalog's exact expected size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelDownloadManagerTest {

    private lateinit var manager: ModelDownloadManager
    private val model = NyayaModelCatalog.GEMMA_4_E2B

    @Before
    fun setUp() {
        manager = ModelDownloadManager(ApplicationProvider.getApplicationContext())
        manager.deleteModel(model.fileName)
    }

    private fun writeModelFile(bytes: Int): File {
        val f = manager.modelFile(model.fileName)
        f.parentFile?.mkdirs()
        f.writeBytes(ByteArray(bytes) { 0 })
        return f
    }

    @Test
    fun missingModel_isNotDownloaded() {
        assertFalse(manager.isDownloaded(model.fileName, model.downloadBytes))
    }

    @Test
    fun emptyFile_isNotDownloaded() {
        writeModelFile(0)
        assertFalse(manager.isDownloaded(model.fileName, model.downloadBytes))
    }

    @Test
    fun truncatedFile_isNotTreatedAsDownloaded() {
        // Stands in for a 2.4 GB download that died partway through.
        writeModelFile(4096)
        assertFalse(
            "a partial bundle must never count as a usable model",
            manager.isDownloaded(model.fileName, model.downloadBytes)
        )
    }

    @Test
    fun fileMatchingExpectedSize_isDownloaded() {
        val f = manager.modelFile(model.fileName)
        f.parentFile?.mkdirs()
        // Allocate sparsely: asserting on length() only, not on 2.4 GB of bytes.
        java.io.RandomAccessFile(f, "rw").use { it.setLength(model.downloadBytes) }
        assertTrue(manager.isDownloaded(model.fileName, model.downloadBytes))
    }

    @Test
    fun withoutAnExpectedSize_anyNonEmptyFileIsAccepted() {
        writeModelFile(1024)
        assertTrue(
            "a custom model URL has no known size, so size cannot be enforced",
            manager.isDownloaded(model.fileName, null)
        )
    }

    @Test
    fun deleteModel_removesBothFinalAndPartialFiles() {
        writeModelFile(1024)
        val part = File(manager.modelsDir, "${model.fileName}.part")
        part.writeBytes(ByteArray(8))
        manager.deleteModel(model.fileName)
        assertFalse(manager.modelFile(model.fileName).exists())
        assertFalse(part.exists())
    }
}
