package com.example

import com.example.data.remote.TelegramRepository
import com.example.domain.ChecksumUtil
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun sha256_computesCorrectly() {
    val input = "Hello, TeleVault!".toByteArray()
    val hash = ChecksumUtil.computeSha256(input)
    assertNotNull(hash)
    assertEquals(64, hash.length)
  }

  @Test
  fun formatFileSize_returnsReadableUnits() {
    assertEquals("0 B", ChecksumUtil.formatFileSize(0))
    assertEquals("500 B", ChecksumUtil.formatFileSize(500))
    assertEquals("1.0 KB", ChecksumUtil.formatFileSize(1024))
    assertEquals("1.0 MB", ChecksumUtil.formatFileSize(1024 * 1024))
    assertEquals("1.0 GB", ChecksumUtil.formatFileSize(1024L * 1024L * 1024L))
  }

  @Test
  fun telegramApiUrls_areAbsoluteAndValidWithTokenColon() {
    val sampleToken = "8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE"
    val getMeUrl = TelegramRepository.botUrl(sampleToken, "getMe")
    val sendMessageUrl = TelegramRepository.botUrl(sampleToken, "sendMessage")
    val sendDocUrl = TelegramRepository.botUrl(sampleToken, "sendDocument")
    val getFileUrl = TelegramRepository.botUrl(sampleToken, "getFile")
    val downloadUrl = TelegramRepository.fileDownloadUrl(sampleToken, "documents/file_0.tpart")

    assertEquals("https://api.telegram.org/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/getMe", getMeUrl)
    assertEquals("https://api.telegram.org/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/sendMessage", sendMessageUrl)
    assertEquals("https://api.telegram.org/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/sendDocument", sendDocUrl)
    assertEquals("https://api.telegram.org/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/getFile", getFileUrl)
    assertEquals("https://api.telegram.org/file/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/documents/file_0.tpart", downloadUrl)

    // Verify java.net.URI parses them without Malformed URL / scheme misinterpretation exceptions
    val uri = java.net.URI(getMeUrl)
    assertEquals("https", uri.scheme)
    assertEquals("api.telegram.org", uri.host)
    assertEquals("/bot8869367272:AAHxEkuNC8Z2JLQOkj3g34xZcE6VFg6-kUE/getMe", uri.path)
  }

  @Test
  fun telegramOkHttpClient_hasRequiredTimeouts() {
    val client = TelegramRepository.createDefaultOkHttpClient()
    assertEquals(15_000, client.connectTimeoutMillis)
    assertEquals(60_000, client.writeTimeoutMillis)
    assertEquals(60_000, client.readTimeoutMillis)
    assertEquals(90_000, client.callTimeoutMillis)
  }

  @Test
  fun redactToken_masksBotTokenCorrectly() {
    val sampleUrl = "https://api.telegram.org/bot123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11/sendDocument"
    val redacted = TelegramRepository.redactToken(sampleUrl)
    assertEquals("https://api.telegram.org/bot<REDACTED>/sendDocument", redacted)
  }

  @Test
  fun chunkSplitting_for55MBFile_with18MBChunkSize_generatesSafeChunksUnder20MBDownloadLimit() {
    val fileSizeBytes = 57_776_537L // ~55.1 MB
    val maxChunkSize = com.example.data.transfer.TransferManager.CHUNK_SIZE_BYTES // 18 MB global safe limit
    val totalChunks = ((fileSizeBytes + maxChunkSize - 1) / maxChunkSize).toInt().coerceAtLeast(1)
    val targetChunkSize = ((fileSizeBytes + totalChunks - 1) / totalChunks).coerceAtLeast(1L)

    // With 18MB max chunk size, a 55.1MB file is split into 4 balanced chunks of ~13.77MB each
    assertEquals(4, totalChunks)
    assertEquals(14_444_135L, targetChunkSize) // ~13.77 MB

    val telegram20MbDownloadLimit = 20L * 1024 * 1024 // 20 MB Telegram getFile limit
    val safe18MbLimit = 18L * 1024 * 1024 // 18 MB headroom limit

    var accumulatedBytes = 0L
    for (i in 0 until totalChunks) {
      val offset = i * targetChunkSize
      val length = minOf(targetChunkSize, fileSizeBytes - offset)
      accumulatedBytes += length
      assertTrue("Chunk $i ($length bytes) must be <= 18MB safe limit", length <= safe18MbLimit)
      assertTrue("Chunk $i ($length bytes) must be <= 20MB Telegram getFile download limit", length <= telegram20MbDownloadLimit)
    }

    assertEquals(fileSizeBytes, accumulatedBytes)
  }

  @Test
  fun chunkFileWriting_doesNotAppendAndStopsAtExactTargetSize() {
    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "chunk_test_" + System.currentTimeMillis())
    tempDir.mkdirs()
    try {
      val stagingFile = java.io.File(tempDir, "staging.bin")
      val totalSize = 250_000 // 250 KB
      val testBytes = ByteArray(totalSize) { (it % 256).toByte() }
      stagingFile.writeBytes(testBytes)

      val targetChunkSize = 100_000L
      val chunksDir = java.io.File(tempDir, "chunks")
      chunksDir.mkdirs()

      // 1. Write chunk 0 (100,000 bytes)
      val chunk0File = java.io.File(chunksDir, "chunk_0.tpart")
      // Pre-populate with dummy stale data to verify it doesn't append
      chunk0File.writeBytes(ByteArray(50_000) { 1 })

      // Simulating writeChunkFileOnDisk logic
      if (chunk0File.exists()) chunk0File.delete()
      java.io.RandomAccessFile(stagingFile, "r").use { raf ->
        raf.seek(0L)
        java.io.FileOutputStream(chunk0File, false).use { out ->
          var remaining = targetChunkSize
          val buf = ByteArray(8192)
          while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = raf.read(buf, 0, toRead)
            if (r == -1) break
            out.write(buf, 0, r)
            remaining -= r
          }
        }
      }

      assertEquals(100_000L, chunk0File.length())

      // 2. Write chunk 1 (100,000 bytes)
      val chunk1File = java.io.File(chunksDir, "chunk_1.tpart")
      if (chunk1File.exists()) chunk1File.delete()
      java.io.RandomAccessFile(stagingFile, "r").use { raf ->
        raf.seek(100_000L)
        java.io.FileOutputStream(chunk1File, false).use { out ->
          var remaining = targetChunkSize
          val buf = ByteArray(8192)
          while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = raf.read(buf, 0, toRead)
            if (r == -1) break
            out.write(buf, 0, r)
            remaining -= r
          }
        }
      }

      assertEquals(100_000L, chunk1File.length())

      // 3. Write chunk 2 (remaining 50,000 bytes)
      val chunk2File = java.io.File(chunksDir, "chunk_2.tpart")
      if (chunk2File.exists()) chunk2File.delete()
      java.io.RandomAccessFile(stagingFile, "r").use { raf ->
        raf.seek(200_000L)
        java.io.FileOutputStream(chunk2File, false).use { out ->
          var remaining = 50_000L
          val buf = ByteArray(8192)
          while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = raf.read(buf, 0, toRead)
            if (r == -1) break
            out.write(buf, 0, r)
            remaining -= r
          }
        }
      }

      assertEquals(50_000L, chunk2File.length())
      assertEquals(totalSize.toLong(), chunk0File.length() + chunk1File.length() + chunk2File.length())
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun downloadStorageManager_relativePathAndFolderMatchRequirements() {
    assertEquals("TGC", com.example.domain.DownloadStorageManager.TGC_FOLDER_NAME)
    assertEquals("Download/TGC", com.example.domain.DownloadStorageManager.RELATIVE_DOWNLOAD_PATH)
  }

  @Test
  fun downloadStorageManager_getUniqueFile_appendsNumericSuffixForDuplicates() {
    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "tgc_test_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
      val file1 = com.example.domain.DownloadStorageManager.getUniqueFile(tempDir, "document.pdf")
      assertEquals("document.pdf", file1.name)
      file1.createNewFile()

      val file2 = com.example.domain.DownloadStorageManager.getUniqueFile(tempDir, "document.pdf")
      assertEquals("document (1).pdf", file2.name)
      file2.createNewFile()

      val file3 = com.example.domain.DownloadStorageManager.getUniqueFile(tempDir, "document.pdf")
      assertEquals("document (2).pdf", file3.name)
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
