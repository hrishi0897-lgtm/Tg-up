package com.example

import com.example.data.remote.FileManifest
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LargeFileManifestTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(FileManifest::class.java)

    @Test
    fun testLargeManifestExceedsTelegramMessageLimitButFitsDocument() {
        // Create 35 chunks simulating a 565.9MB file upload
        val chunkCount = 35
        val chunks = (0 until chunkCount).map { index ->
            ManifestChunk(
                index = index,
                messageId = 100000L + index,
                telegramFileId = "BAACAgIAAxkDAAICW2examplefileid$index",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                size = 18 * 1024 * 1024L
            )
        }

        val fileId = "large-file-uuid-565mb"
        val manifest = FileManifest(
            fileId = fileId,
            name = "huge_dataset_archive_565mb.zip",
            size = 565_900_000L,
            mimeType = "application/zip",
            overallSha256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            folderId = null,
            uploadDate = System.currentTimeMillis(),
            chunks = chunks
        )

        val manifestJson = manifestAdapter.toJson(manifest)

        // Telegram sendMessage limit is 4096 chars
        val telegramMessageLimit = 4096
        assertTrue(
            "Large manifest ($chunkCount chunks) length (${manifestJson.length}) must exceed sendMessage limit ($telegramMessageLimit)",
            manifestJson.length > telegramMessageLimit
        )

        // Caption used for document upload must be small (< 100 chars)
        val caption = "${TelegramRepository.MANIFEST_CAPTION_PREFIX}${manifest.fileId}"
        assertEquals("MANIFEST|large-file-uuid-565mb", caption)
        assertTrue("Caption must be safely under Telegram 1024 caption limit", caption.length < 100)

        // Test writing to local manifest file
        val tempDir = File(System.getProperty("java.io.tmpdir"), "tele_manifest_test").apply { mkdirs() }
        val manifestFile = File(tempDir, "$fileId.manifest.json")
        try {
            manifestFile.writeText(manifestJson)
            assertTrue("Manifest file exists", manifestFile.exists())
            assertEquals("File size matches JSON length", manifestJson.toByteArray(Charsets.UTF_8).size.toLong(), manifestFile.length())

            // Test deserializing from file content
            val readJson = manifestFile.readText()
            val parsedManifest = manifestAdapter.fromJson(readJson)
            assertNotNull(parsedManifest)
            assertEquals(fileId, parsedManifest!!.fileId)
            assertEquals(35, parsedManifest.chunks.size)
            assertEquals(565_900_000L, parsedManifest.size)
        } finally {
            manifestFile.delete()
            tempDir.delete()
        }
    }
}
