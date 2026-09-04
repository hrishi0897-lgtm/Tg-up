package com.example

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
}
