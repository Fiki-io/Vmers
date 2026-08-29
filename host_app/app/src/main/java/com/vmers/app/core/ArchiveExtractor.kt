package com.vmers.app.core

import android.util.Log
import com.vmers.app.debug.LogcatManager
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ArchiveExtractor {
    private const val TAG = "Vmers-Extractor"

    fun extractArchive(
        archiveFile: File,
        targetDir: File,
        onProgress: (progressPercent: Int, statusText: String) -> Unit
    ): Boolean {
        LogcatManager.logInfo(TAG, "Starting extraction: ${archiveFile.name} (${archiveFile.length() / 1024 / 1024} MB) -> ${targetDir.absolutePath}")
        targetDir.mkdirs()

        return try {
            val name = archiveFile.name.lowercase()
            val success = when {
                name.endsWith(".zip") -> extractZip(archiveFile, targetDir, onProgress)
                name.endsWith(".7z") -> {
                    val ok7z = extract7z(archiveFile, targetDir, onProgress)
                    if (!ok7z) {
                        LogcatManager.logWarn(TAG, "7z decompression failed, attempting ZIP fallback...")
                        extractZip(archiveFile, targetDir, onProgress)
                    } else true
                }
                else -> {
                    extractZip(archiveFile, targetDir, onProgress) || extract7z(archiveFile, targetDir, onProgress)
                }
            }

            if (success) {
                // Post-extraction: Fix executable permissions on system binaries
                fixPermissions(targetDir)
                
                // Write installation marker
                val marker = File(targetDir, ".vmers_installed")
                marker.writeText("INSTALLED_AT=${System.currentTimeMillis()}\nROM_ARCHIVE=${archiveFile.name}\n")
                LogcatManager.logInfo(TAG, "Extraction completed successfully. Marker created at ${marker.absolutePath}")
                true
            } else {
                LogcatManager.logError(TAG, "Archive extraction failed.")
                false
            }
        } catch (e: Exception) {
            LogcatManager.logError(TAG, "Extraction exception: ${e.message}", e)
            false
        }
    }

    private fun extract7z(
        archiveFile: File,
        targetDir: File,
        onProgress: (Int, String) -> Unit
    ): Boolean {
        try {
            SevenZFile(archiveFile).use { sevenZFile ->
                var entry: SevenZArchiveEntry?
                var extractedCount = 0
                val buffer = ByteArray(65536)

                while (sevenZFile.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    val outFile = File(targetDir, currentEntry.name)

                    if (currentEntry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var len: Int
                            while (sevenZFile.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }

                    extractedCount++
                    if (extractedCount % 20 == 0) {
                        onProgress(-1, "Extracting: ${currentEntry.name}")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            LogcatManager.logError(TAG, "7z extraction error: ${e.message}", e)
            return false
        }
    }

    private fun extractZip(
        archiveFile: File,
        targetDir: File,
        onProgress: (Int, String) -> Unit
    ): Boolean {
        try {
            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(65536)
                var count = 0

                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    val outFile = File(targetDir, currentEntry.name)

                    if (currentEntry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    count++
                    if (count % 20 == 0) {
                        onProgress(-1, "Extracting: ${currentEntry.name}")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            LogcatManager.logError(TAG, "ZIP extraction error: ${e.message}", e)
            return false
        }
    }

    private fun fixPermissions(rootfsDir: File) {
        val binDirs = listOf(
            File(rootfsDir, "system/bin"),
            File(rootfsDir, "system/xbin"),
            File(rootfsDir, "apex/com.android.runtime/bin")
        )

        for (dir in binDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                }
            }
        }
    }
}
