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
}
