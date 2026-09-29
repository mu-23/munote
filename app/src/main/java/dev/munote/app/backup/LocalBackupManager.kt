package dev.munote.app.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class LocalBackupInfo(
    val fileName: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val automatic: Boolean,
    val imported: Boolean = false,
)

/**
 * Local-only MuNote backups.
 *
 * The live data already lives under Context.filesDir. Backup archives are kept under
 * files/backups/ as well, so MuNote never creates a top-level folder in shared Android storage.
 * Automatic backups are opportunistic: one is created on app launch when the newest automatic
 * snapshot is older than 24 hours. Only the newest seven automatic snapshots are retained.
 */
class LocalBackupManager(private val context: Context) {
    private val root = context.filesDir
    private val backupsDir = File(root, "backups").apply { mkdirs() }

    fun backups(): List<LocalBackupInfo> =
        backupsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                LocalBackupInfo(
                    fileName = file.name,
                    modifiedAt = file.lastModified(),
                    sizeBytes = file.length(),
                    automatic = file.name.startsWith(AUTO_PREFIX),
                    imported = file.name.startsWith(IMPORTED_PREFIX),
                )
            }

    suspend fun createManualBackup(): LocalBackupInfo = withContext(Dispatchers.IO) {
        createBackup(MANUAL_PREFIX)
    }

    suspend fun exportBackup(
        fileName: String,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        val archive = File(backupsDir, fileName)
        require(
            archive.exists() &&
                archive.isFile &&
                archive.parentFile?.canonicalFile == backupsDir.canonicalFile
        ) { "Backup not found" }

        context.contentResolver.openOutputStream(destination, "w").use { output ->
            requireNotNull(output) { "Unable to open export destination" }
            BufferedInputStream(FileInputStream(archive)).use { input ->
                BufferedOutputStream(output).use { buffered ->
                    input.copyTo(buffered, BUFFER_SIZE)
                }
            }
        }
    }

    suspend fun importBackup(source: Uri): LocalBackupInfo = withContext(Dispatchers.IO) {
        backupsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = UUID.randomUUID().toString().take(8)
        val target = File(backupsDir, "$IMPORTED_PREFIX-$stamp-$suffix.zip")
        val temp = File(backupsDir, target.name + ".tmp")

        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Unable to open backup file" }
                BufferedInputStream(input).use { buffered ->
                    BufferedOutputStream(FileOutputStream(temp)).use { output ->
                        buffered.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
            require(temp.length() > 0L) { "Backup file is empty" }

            val validationDir = File(
                context.cacheDir,
                "munote-backup-check-${UUID.randomUUID()}"
            ).apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                unzipSafely(temp, validationDir)
                require(validationDir.listFiles().orEmpty().isNotEmpty()) {
                    "Backup archive contains no MuNote data"
                }
            } finally {
                validationDir.deleteRecursively()
            }

            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Unable to import backup" }
        } catch (error: Throwable) {
            temp.delete()
            target.delete()
            throw error
        }

        LocalBackupInfo(
            fileName = target.name,
            modifiedAt = target.lastModified(),
            sizeBytes = target.length(),
            automatic = false,
            imported = true,
        )
    }

    suspend fun autoBackupIfDue(): LocalBackupInfo? = withContext(Dispatchers.IO) {
        val newestAuto = backupsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(AUTO_PREFIX) && it.extension == "zip" }
            .maxByOrNull { it.lastModified() }

        val now = System.currentTimeMillis()
        if (newestAuto != null && now - newestAuto.lastModified() < AUTO_INTERVAL_MS) {
            return@withContext null
        }

        val created = createBackup(AUTO_PREFIX)
        pruneAutomaticBackups()
        created
    }

    suspend fun restore(fileName: String) = withContext(Dispatchers.IO) {
        val archive = File(backupsDir, fileName)
        require(archive.exists() && archive.isFile) { "Backup not found" }

        val stage = File(context.cacheDir, "munote-restore-${UUID.randomUUID()}").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            unzipSafely(archive, stage)

            root.listFiles().orEmpty().forEach { child ->
                if (child.canonicalFile == backupsDir.canonicalFile) return@forEach
                child.deleteRecursively()
            }

            stage.listFiles().orEmpty().forEach { child ->
                val target = File(root, child.name)
                copyRecursively(child, target)
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    suspend fun deleteBackup(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(backupsDir, fileName)
        if (file.parentFile?.canonicalFile == backupsDir.canonicalFile) {
            file.delete()
        }
    }

    private fun createBackup(prefix: String): LocalBackupInfo {
        backupsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(backupsDir, "$prefix-$stamp.zip")
        val temp = File(backupsDir, target.name + ".tmp")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
            root.listFiles().orEmpty()
                .filterNot { it.canonicalFile == backupsDir.canonicalFile }
                .forEach { child ->
                    addToZip(zip, child, child.name)
                }
        }

        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Unable to finalize backup" }

        return LocalBackupInfo(
            fileName = target.name,
            modifiedAt = target.lastModified(),
            sizeBytes = target.length(),
            automatic = prefix == AUTO_PREFIX,
            imported = prefix == IMPORTED_PREFIX,
        )
    }

    private fun addToZip(zip: ZipOutputStream, file: File, relativePath: String) {
        if (file.isDirectory) {
            val children = file.listFiles().orEmpty()
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry("$relativePath/"))
                zip.closeEntry()
            } else {
                children.forEach { child ->
                    addToZip(zip, child, "$relativePath/${child.name}")
                }
            }
            return
        }

        zip.putNextEntry(ZipEntry(relativePath))
        BufferedInputStream(FileInputStream(file)).use { input ->
            input.copyTo(zip, BUFFER_SIZE)
        }
        zip.closeEntry()
    }

    private fun unzipSafely(archive: File, destination: File) {
        val destinationRoot = destination.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(destination, entry.name).canonicalFile
                require(
                    output.path == destinationRoot.path ||
                        output.path.startsWith(destinationRoot.path + File.separator)
                ) { "Unsafe backup entry" }

                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(output)).use { stream ->
                        zip.copyTo(stream, BUFFER_SIZE)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyRecursively(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun pruneAutomaticBackups() {
        backupsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(AUTO_PREFIX) && it.extension == "zip" }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_AUTO_BACKUPS)
            .forEach { it.delete() }
    }

    companion object {
        private const val MANUAL_PREFIX = "munote-manual"
        private const val AUTO_PREFIX = "munote-auto"
        private const val IMPORTED_PREFIX = "munote-imported"
        private const val AUTO_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val MAX_AUTO_BACKUPS = 7
        private const val BUFFER_SIZE = 256 * 1024
    }
}
