package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.transfer.BotWorkerMetric
import com.example.domain.ChecksumUtil
import com.example.domain.model.StorageCategory
import com.example.domain.model.StorageStats
import com.example.domain.model.classifyFileCategory
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.CategoryAmber
import com.example.ui.theme.CategoryTeal
import com.example.ui.theme.CategoryViolet
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont

data class DuplicateFileGroup(
    val checksum: String,
    val primaryFile: FileEntity,
    val duplicateFiles: List<FileEntity>,
    val wastedBytes: Long
)

@Composable
fun StorageAnalyticsScreen(
    stats: StorageStats,
    allFiles: List<FileEntity>,
    botMetrics: List<BotWorkerMetric>,
    onBack: () -> Unit,
    onInspectFile: (FileEntity) -> Unit,
    onCleanupDuplicates: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val completedFiles = remember(allFiles) {
        allFiles.filter { it.status == FileStatus.COMPLETED && it.deletedAt == null }
    }

    val encryptedFiles = remember(completedFiles) { completedFiles.filter { it.isEncrypted } }
    val encryptedBytes = remember(encryptedFiles) { encryptedFiles.sumOf { it.size } }
    val totalBytes = stats.totalBytesStored.coerceAtLeast(1L)
    val encryptedRatio = if (completedFiles.isNotEmpty()) {
        (encryptedFiles.size * 100) / completedFiles.size
    } else 0

    // Compute duplicate groups by SHA-256 checksum
    val duplicateGroups = remember(completedFiles) {
        completedFiles
            .filter { it.checksum.isNotBlank() }
            .groupBy { it.checksum }
            .values
            .filter { it.size > 1 }
            .map { group ->
                val sorted = group.sortedBy { it.uploadDate }
                val primary = sorted.first()
                val dupes = sorted.drop(1)
                DuplicateFileGroup(
                    checksum = primary.checksum,
                    primaryFile = primary,
                    duplicateFiles = dupes,
                    wastedBytes = dupes.sumOf { it.size }
                )
            }
            .sortedByDescending { it.wastedBytes }
    }

    val totalWastedBytes = remember(duplicateGroups) { duplicateGroups.sumOf { it.wastedBytes } }
    val allDuplicateIds = remember(duplicateGroups) { duplicateGroups.flatMap { g -> g.duplicateFiles.map { it.id } } }

    // Top 8 largest files
    val largestFiles = remember(completedFiles) {
        completedFiles.sortedByDescending { it.size }.take(8)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .testTag("analytics_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage Analytics",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.text
                )
                Text(
                    text = "Category breakdown, encryption telemetry & duplicate finder",
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    color = colors.textDim
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Multi-Segment Visual Storage Distribution Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLOUD FOOTPRINT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textDim,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "${completedFiles.size} files • ${stats.folderCount} folders",
                            fontSize = 12.sp,
                            fontFamily = NumericMonoFont,
                            color = colors.teal
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = ChecksumUtil.formatBytes(stats.totalBytesStored),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NumericMonoFont,
                        color = colors.text
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-segment bar
                    val docFrac = (stats.breakdown.documentsBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 1f)
                    val mediaFrac = (stats.breakdown.mediaBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 1f)
                    val otherFrac = (stats.breakdown.otherBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 1f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(colors.surfaceHi),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (stats.breakdown.documentsBytes > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(docFrac)
                                    .fillMaxHeight()
                                    .background(CategoryViolet)
                            )
                        }
                        if (stats.breakdown.mediaBytes > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(mediaFrac)
                                    .fillMaxHeight()
                                    .background(CategoryTeal)
                            )
                        }
                        if (stats.breakdown.otherBytes > 0 || stats.totalBytesStored == 0L) {
                            Box(
                                modifier = Modifier
                                    .weight(otherFrac)
                                    .fillMaxHeight()
                                    .background(CategoryAmber)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CategoryLegendColumn("Documents", stats.breakdown.documentsBytes, CategoryViolet)
                        CategoryLegendColumn("Media", stats.breakdown.mediaBytes, CategoryTeal)
                        CategoryLegendColumn("Archives / Other", stats.breakdown.otherBytes, CategoryAmber)
                    }
                }
            }

            // 2. Zero-Knowledge AES-256 Encryption Telemetry Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ZERO-KNOWLEDGE ENCRYPTION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textDim,
                                letterSpacing = 1.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(colors.teal.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$encryptedRatio% AES-256-GCM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NumericMonoFont,
                                color = colors.teal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceHi)
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Encrypted",
                                    fontSize = 12.sp,
                                    color = colors.textDim
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${encryptedFiles.size} files",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NumericMonoFont,
                                color = colors.text
                            )
                            Text(
                                text = ChecksumUtil.formatBytes(encryptedBytes),
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                color = colors.teal
                            )
                        }

                        val unencCount = (completedFiles.size - encryptedFiles.size).coerceAtLeast(0)
                        val unencBytes = (stats.totalBytesStored - encryptedBytes).coerceAtLeast(0L)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceHi)
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Standard Cloud",
                                    fontSize = 12.sp,
                                    color = colors.textDim
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$unencCount files",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NumericMonoFont,
                                color = colors.text
                            )
                            Text(
                                text = ChecksumUtil.formatBytes(unencBytes),
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                color = colors.textDim
                            )
                        }
                    }
                }
            }

            // 3. Multi-Bot Load Balancer Pool Telemetry
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MULTI-BOT LOAD BALANCER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textDim,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "${botMetrics.size.coerceAtLeast(1)} active worker${if (botMetrics.size == 1) "" else "s"}",
                            fontSize = 11.sp,
                            fontFamily = NumericMonoFont,
                            color = colors.violet
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (botMetrics.isEmpty()) {
                        Text(
                            text = "Primary bot active. Add verified standby bots in Settings to multiply parallel chunk upload bandwidth.",
                            fontSize = 12.sp,
                            color = colors.textDim
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            botMetrics.forEach { metric ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceHi)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (metric.isCoolingDown) colors.amber else colors.mint)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = metric.label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.text
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = metric.tokenPreview,
                                                fontSize = 11.sp,
                                                fontFamily = NumericMonoFont,
                                                color = colors.textFaint
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${metric.completedChunks} chunks • ${ChecksumUtil.formatBytes(metric.bytesDispatched)} dispatched",
                                            fontSize = 11.sp,
                                            fontFamily = NumericMonoFont,
                                            color = colors.textDim
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (metric.activeChunks > 0) colors.teal.copy(alpha = 0.16f)
                                                else colors.bg
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (metric.activeChunks > 0) "${metric.activeChunks} live" else "Ready",
                                            fontSize = 11.sp,
                                            fontFamily = NumericMonoFont,
                                            color = if (metric.activeChunks > 0) colors.teal else colors.textDim
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. SHA-256 Duplicate File Finder & One-Tap Cleanup
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (duplicateGroups.isNotEmpty()) colors.amber else colors.mint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHA-256 DUPLICATE FINDER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textDim,
                                letterSpacing = 1.sp
                            )
                        }
                        if (duplicateGroups.isNotEmpty()) {
                            Text(
                                text = "${ChecksumUtil.formatBytes(totalWastedBytes)} redundant",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NumericMonoFont,
                                color = colors.amber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (duplicateGroups.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceHi)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.mint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Zero duplicate files detected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text
                                )
                                Text(
                                    text = "Every file in your vault has a unique SHA-256 cryptographic hash.",
                                    fontSize = 11.5.sp,
                                    color = colors.textDim
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            duplicateGroups.forEach { group ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceHi)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group.primaryFile.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${group.duplicateFiles.size + 1} copies • ${ChecksumUtil.formatBytes(group.wastedBytes)}",
                                            fontSize = 11.sp,
                                            fontFamily = NumericMonoFont,
                                            color = colors.amber
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "SHA-256: ${group.checksum.take(12)}…",
                                        fontSize = 11.sp,
                                        fontFamily = NumericMonoFont,
                                        color = colors.textFaint
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { onCleanupDuplicates(allDuplicateIds) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.amber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cleanup_duplicates_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Move ${allDuplicateIds.size} Duplicate${if (allDuplicateIds.size > 1) "s" else ""} to Trash (${ChecksumUtil.formatBytes(totalWastedBytes)})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. Top Largest Files List
            if (largestFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "LARGEST FILES IN VAULT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodySansFont,
                        color = colors.textDim,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(largestFiles, key = { it.id }) { file ->
                    val cat = classifyFileCategory(file.mimeType, file.name)
                    val catColor = when (cat) {
                        StorageCategory.DOCUMENTS -> CategoryViolet
                        StorageCategory.MEDIA -> CategoryTeal
                        StorageCategory.OTHER -> CategoryAmber
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .clickable { onInspectFile(file) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(catColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (cat) {
                                    StorageCategory.DOCUMENTS -> Icons.Default.Description
                                    StorageCategory.MEDIA -> Icons.Default.Movie
                                    StorageCategory.OTHER -> Icons.Default.FolderZip
                                },
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = file.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (file.isEncrypted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "AES-256 Encrypted",
                                        tint = colors.teal,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${file.totalChunks} chunk${if (file.totalChunks > 1) "s" else ""} • ${ChecksumUtil.formatDate(file.uploadDate)}",
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                color = colors.textDim
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = ChecksumUtil.formatBytes(file.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NumericMonoFont,
                            color = catColor
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CategoryLegendColumn(
    label: String,
    bytes: Long,
    color: Color
) {
    val colors = LocalTeleVaultColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = colors.textDim
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = ChecksumUtil.formatBytes(bytes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NumericMonoFont,
            color = colors.text
        )
    }
}
