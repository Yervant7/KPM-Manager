package just.yervant.kpmmanager.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AnyKernelHelper {

    private const val TAG = "AnyKernelHelper"

    enum class KernelFormat {
        RAW,
        RAW_DTB,
        GZIP,
        GZIP_DTB,
        LZ4,
        BZIP2,
        BOOT_IMG
    }

    data class KernelFileInfo(
        val file: File,
        val name: String,
        val format: KernelFormat,
        var dtbFile: File? = null
    )

    private val KERNEL_CANDIDATES = listOf(
        "Image" to KernelFormat.RAW,
        "Image.gz" to KernelFormat.GZIP,
        "Image.lz4" to KernelFormat.LZ4,
        "Image.gz-dtb" to KernelFormat.GZIP_DTB,
        "Image-dtb" to KernelFormat.RAW_DTB,
        "Image.bz2" to KernelFormat.BZIP2,
        "zImage" to KernelFormat.RAW,
        "zImage-dtb" to KernelFormat.RAW_DTB,
        "kernel" to KernelFormat.RAW,
        "bzImage" to KernelFormat.RAW,
        "boot.img" to KernelFormat.BOOT_IMG,
    )

    // DTB magic is 0xd00dfeed (big-endian)
    private val DTB_MAGIC = byteArrayOf(0xd0.toByte(), 0x0d.toByte(), 0xfe.toByte(), 0xed.toByte())

    /**
     * Check whether a file/URI appears to be an AnyKernel3 zip archive.
     */
    fun isAnyKernelZip(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { isAnyKernelZip(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect file as AnyKernel3 zip: $e")
            false
        }
    }

    fun isAnyKernelZip(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { isAnyKernelZip(it) } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect URI as AnyKernel3 zip: $e")
            false
        }
    }

    fun isAnyKernelZip(inputStream: InputStream): Boolean {
        try {
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.trimStart('/').lowercase()
                    val parts = name.split('/')
                    if ((parts.size == 1 && (parts[0] == "anykernel.sh" || parts[0] == "anykernel2.sh" || parts[0] == "anykernel3.sh")) ||
                        (parts.size == 2 && parts[0] == "tools" && (parts[1] == "ak3-core.sh" || parts[1] == "ak2-core.sh" || parts[1] == "ak3.sh")) ||
                        (parts.size == 2 && (parts[1] == "anykernel.sh" || parts[1] == "anykernel2.sh" || parts[1] == "anykernel3.sh"))
                    ) {
                        return true
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking zip entries: $e")
        }
        return false
    }

    /**
     * Extracts a zip file or input stream into target directory.
     */
    fun extractZip(inputStream: InputStream, destDir: File): Boolean {
        destDir.deleteRecursively()
        destDir.mkdirs()

        return try {
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                val destCanonicalPath = destDir.canonicalPath

                while (entry != null) {
                    val destFile = File(destDir, entry.name)
                    // Security: prevent Zip Slip vulnerability
                    val fileCanonicalPath = destFile.canonicalPath
                    if (!fileCanonicalPath.startsWith(destCanonicalPath + File.separator) && fileCanonicalPath != destCanonicalPath) {
                        Log.e(TAG, "ZipSlip attempt detected in entry: ${entry.name}")
                        return false
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().buffered().use { out ->
                            zis.copyTo(out)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract AnyKernel3 zip: $e")
            false
        }
    }

    /**
     * Locate the kernel file inside the extracted AnyKernel3 directory.
     */
    fun findKernelFile(ak3Dir: File): KernelFileInfo? {
        if (!ak3Dir.exists() || !ak3Dir.isDirectory) return null

        val searchDirs = mutableListOf(ak3Dir)
        ak3Dir.listFiles()?.filter { it.isDirectory }?.let { searchDirs.addAll(it) }

        for (dir in searchDirs) {
            for ((candidateName, candidateFormat) in KERNEL_CANDIDATES) {
                val candidateFile = File(dir, candidateName)
                if (candidateFile.exists() && candidateFile.isFile && candidateFile.length() > 0) {
                    val actualFormat = when (candidateFormat) {
                        KernelFormat.GZIP -> {
                            if (hasAppendedDtb(candidateFile)) KernelFormat.GZIP_DTB else KernelFormat.GZIP
                        }
                        KernelFormat.RAW -> {
                            if (hasAppendedDtb(candidateFile)) KernelFormat.RAW_DTB else KernelFormat.RAW
                        }
                        else -> candidateFormat
                    }

                    return KernelFileInfo(
                        file = candidateFile,
                        name = candidateName,
                        format = actualFormat
                    )
                }
            }
        }
        return null
    }

    /**
     * Checks if a kernel file has a DTB appended at the end.
     */
    private fun hasAppendedDtb(file: File): Boolean {
        return findDtbOffset(file) > 0
    }

    private fun findDtbOffset(file: File): Long {
        if (!file.exists() || file.length() < 64) return -1L
        try {
            val fileLen = file.length()
            val maxScan = minOf(fileLen, 32L * 1024 * 1024)

            // Check ARM64 Image header (image_size at offset 16)
            if (fileLen > 64) {
                file.inputStream().buffered().use { input ->
                    val header = ByteArray(64)
                    val read = input.read(header)
                    if (read == 64) {
                        // ARM64 magic at offset 56: 0x644d5241 ("ARM\x64")
                        if (header[56] == 0x41.toByte() && header[57] == 0x52.toByte() &&
                            header[58] == 0x4d.toByte() && header[59] == 0x64.toByte()
                        ) {
                            var imageSize = 0L
                            for (i in 0..7) {
                                imageSize = imageSize or ((header[16 + i].toLong() and 0xFF) shl (i * 8))
                            }
                            if (imageSize in 64 until fileLen) {
                                if (isFdtHeaderAt(file, imageSize)) {
                                    return imageSize
                                }
                            }
                        }
                    }
                }
            }

            // Scan from stream with FDT header verification
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                var currentOffset = 0L
                val startOffset = if (fileLen > 1024) 1024L else 10L
                if (startOffset > 0) {
                    input.skip(startOffset)
                    currentOffset = startOffset
                }

                while (currentOffset < maxScan) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 4) break

                    for (i in 0 until bytesRead - 4) {
                        if (buffer[i] == DTB_MAGIC[0] &&
                            buffer[i + 1] == DTB_MAGIC[1] &&
                            buffer[i + 2] == DTB_MAGIC[2] &&
                            buffer[i + 3] == DTB_MAGIC[3]
                        ) {
                            val candidateOffset = currentOffset + i
                            if (isFdtHeaderAt(file, candidateOffset)) {
                                return candidateOffset
                            }
                        }
                    }
                    currentOffset += (bytesRead - 4)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check DTB offset: $e")
        }
        return -1L
    }

    private fun isFdtHeaderAt(file: File, offset: Long): Boolean {
        val fileLen = file.length()
        if (offset + 24 > fileLen) return false
        try {
            file.inputStream().buffered().use { input ->
                input.skip(offset)
                val fdtHeader = ByteArray(24)
                val read = input.read(fdtHeader)
                if (read < 24) return false

                if (fdtHeader[0] != DTB_MAGIC[0] ||
                    fdtHeader[1] != DTB_MAGIC[1] ||
                    fdtHeader[2] != DTB_MAGIC[2] ||
                    fdtHeader[3] != DTB_MAGIC[3]
                ) {
                    return false
                }

                val totalSize = ((fdtHeader[4].toInt() and 0xFF) shl 24) or
                        ((fdtHeader[5].toInt() and 0xFF) shl 16) or
                        ((fdtHeader[6].toInt() and 0xFF) shl 8) or
                        (fdtHeader[7].toInt() and 0xFF)

                if (totalSize <= 0 || offset + totalSize > fileLen) return false

                val version = ((fdtHeader[20].toInt() and 0xFF) shl 24) or
                        ((fdtHeader[21].toInt() and 0xFF) shl 16) or
                        ((fdtHeader[22].toInt() and 0xFF) shl 8) or
                        (fdtHeader[23].toInt() and 0xFF)

                return version in 1..30
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Decompress or prepare the kernel to a raw uncompressed kernel image for kptools.
     */
    fun prepareRawKernel(kernelInfo: KernelFileInfo, rawKernelOut: File, workDir: File): Boolean {
        rawKernelOut.delete()

        return try {
            when (kernelInfo.format) {
                KernelFormat.RAW -> {
                    kernelInfo.file.inputStream().buffered().use { input ->
                        rawKernelOut.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.RAW_DTB -> {
                    val dtbOffset = findDtbOffset(kernelInfo.file)
                    if (dtbOffset > 0) {
                        val dtbFile = File(workDir, "saved_dtb.bin")
                        kernelInfo.file.inputStream().buffered().use { input ->
                            rawKernelOut.outputStream().buffered().use { output ->
                                val buffer = ByteArray(8192)
                                var remaining = dtbOffset
                                while (remaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                    val read = input.read(buffer, 0, toRead)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    remaining -= read
                                }
                            }
                            dtbFile.outputStream().buffered().use { dtbOut ->
                                input.copyTo(dtbOut)
                            }
                        }
                        kernelInfo.dtbFile = dtbFile
                    } else {
                        kernelInfo.file.inputStream().buffered().use { input ->
                            rawKernelOut.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.GZIP -> {
                    GZIPInputStream(kernelInfo.file.inputStream().buffered()).use { gzipIn ->
                        rawKernelOut.outputStream().buffered().use { output ->
                            gzipIn.copyTo(output)
                        }
                    }
                    rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.GZIP_DTB -> {
                    val dtbOffset = findDtbOffset(kernelInfo.file)
                    if (dtbOffset > 0) {
                        val dtbFile = File(workDir, "saved_dtb.bin")
                        kernelInfo.file.inputStream().buffered().use { input ->
                            input.skip(dtbOffset)
                            dtbFile.outputStream().buffered().use { dtbOut ->
                                input.copyTo(dtbOut)
                            }
                        }
                        kernelInfo.dtbFile = dtbFile
                    }

                    GZIPInputStream(kernelInfo.file.inputStream().buffered()).use { gzipIn ->
                        rawKernelOut.outputStream().buffered().use { output ->
                            gzipIn.copyTo(output)
                        }
                    }
                    rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.LZ4 -> {
                    val bb = File(workDir, "busybox").absolutePath
                    val res = Shell.cmd("\"$bb\" lz4 -dc \"${kernelInfo.file.absolutePath}\" > \"${rawKernelOut.absolutePath}\"").exec()
                    res.isSuccess && rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.BZIP2 -> {
                    val bb = File(workDir, "busybox").absolutePath
                    val res = Shell.cmd("\"$bb\" bzcat \"${kernelInfo.file.absolutePath}\" > \"${rawKernelOut.absolutePath}\"").exec()
                    res.isSuccess && rawKernelOut.exists() && rawKernelOut.length() > 0
                }

                KernelFormat.BOOT_IMG -> {
                    val res = Shell.cmd("cd \"${workDir.absolutePath}\"", "./kptools unpacknolog \"${kernelInfo.file.absolutePath}\"").exec()
                    if (res.isSuccess) {
                        val extracted = File(workDir, "kernel")
                        if (extracted.exists()) {
                            extracted.copyTo(rawKernelOut, overwrite = true)
                            true
                        } else false
                    } else false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare raw kernel: $e")
            false
        }
    }

    /**
     * Replaces the original kernel in the AnyKernel directory with the patched raw kernel.
     */
    fun repackPatchedKernel(patchedRawFile: File, kernelInfo: KernelFileInfo, workDir: File): Boolean {
        if (!patchedRawFile.exists()) return false

        return try {
            when (kernelInfo.format) {
                KernelFormat.RAW -> {
                    patchedRawFile.copyTo(kernelInfo.file, overwrite = true)
                    true
                }

                KernelFormat.RAW_DTB -> {
                    kernelInfo.file.delete()
                    patchedRawFile.inputStream().buffered().use { input ->
                        kernelInfo.file.outputStream().buffered().use { output ->
                            input.copyTo(output)
                            kernelInfo.dtbFile?.takeIf { it.exists() }?.inputStream()?.buffered()?.use { dtbIn ->
                                dtbIn.copyTo(output)
                            }
                        }
                    }
                    true
                }

                KernelFormat.GZIP -> {
                    kernelInfo.file.delete()
                    GZIPOutputStream(kernelInfo.file.outputStream().buffered()).use { gzipOut ->
                        patchedRawFile.inputStream().buffered().use { input ->
                            input.copyTo(gzipOut)
                        }
                    }
                    true
                }

                KernelFormat.GZIP_DTB -> {
                    kernelInfo.file.delete()
                    GZIPOutputStream(kernelInfo.file.outputStream().buffered()).use { gzipOut ->
                        patchedRawFile.inputStream().buffered().use { input ->
                            input.copyTo(gzipOut)
                        }
                    }
                    // Append DTB if we saved one
                    kernelInfo.dtbFile?.takeIf { it.exists() }?.let { dtb ->
                        kernelInfo.file.appendBytes(dtb.readBytes())
                    }
                    true
                }

                KernelFormat.LZ4 -> {
                    val bb = File(workDir, "busybox").absolutePath
                    val res = Shell.cmd("\"$bb\" lz4 -c \"${patchedRawFile.absolutePath}\" > \"${kernelInfo.file.absolutePath}\"").exec()
                    res.isSuccess && kernelInfo.file.exists() && kernelInfo.file.length() > 0
                }

                KernelFormat.BZIP2 -> {
                    val bb = File(workDir, "busybox").absolutePath
                    val res = Shell.cmd("\"$bb\" bzip2 -c \"${patchedRawFile.absolutePath}\" > \"${kernelInfo.file.absolutePath}\"").exec()
                    res.isSuccess && kernelInfo.file.exists() && kernelInfo.file.length() > 0
                }

                KernelFormat.BOOT_IMG -> {
                    val workKernel = File(workDir, "kernel")
                    patchedRawFile.copyTo(workKernel, overwrite = true)
                    val res = Shell.cmd("cd \"${workDir.absolutePath}\"", "./kptools repack \"${kernelInfo.file.absolutePath}\"").exec()
                    if (res.isSuccess) {
                        val newBoot = File(workDir, "new-boot.img")
                        if (newBoot.exists()) {
                            newBoot.copyTo(kernelInfo.file, overwrite = true)
                            true
                        } else false
                    } else false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to repack patched kernel: $e")
            false
        }
    }

    /**
     * Creates a zip archive from the contents of ak3Dir.
     */
    fun createPatchedZip(ak3Dir: File, outputZip: File): Boolean {
        outputZip.delete()
        outputZip.parentFile?.mkdirs()

        return try {
            ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
                val baseDir = ak3Dir.canonicalFile
                addDirToZip(baseDir, baseDir, zos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create patched zip: $e")
            false
        }
    }

    private fun addDirToZip(baseDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return

        for (file in files) {
            val relativePath = file.relativeTo(baseDir).invariantSeparatorsPath

            if (file.isDirectory) {
                val entryName = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                val entry = ZipEntry(entryName)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)
                zos.closeEntry()
                addDirToZip(baseDir, file, zos)
            } else {
                val entry = ZipEntry(relativePath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)
                file.inputStream().buffered().use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    /**
     * Executes AnyKernel3 flashing in root shell.
     */
    fun flashAnyKernel(
        ak3Dir: File,
        patchDir: File,
        logs: CallbackList<String>
    ): Boolean {
        logs.add("- Starting AnyKernel3 flashing process...")

        val cmd = """
            cd "${ak3Dir.absolutePath}"
            chmod -R 755 . 2>/dev/null
            export OUTFD=1
            export BOOTMODE=true
            export ASH_STANDALONE=1
            export AKHOME="${ak3Dir.absolutePath}"
            export PATH="${ak3Dir.absolutePath}/tools:$patchDir:${'$'}PATH"
            
            if [ -f anykernel.sh ]; then
                "$patchDir/busybox" sh anykernel.sh
            elif [ -f META-INF/com/google/android/update-binary ]; then
                "$patchDir/busybox" sh META-INF/com/google/android/update-binary 3 1
            else
                echo "- Error: No anykernel.sh or update-binary found!"
                exit 1
            fi
        """.trimIndent()

        val filteredLogs = object : CallbackList<String>() {
            override fun onAddElement(e: String?) {
                e?.let { line ->
                    val cleanLine = if (line.startsWith("ui_print ")) line.substring(9).trim() else line
                    if (cleanLine.isNotEmpty()) {
                        logs.add(cleanLine)
                    }
                }
            }
        }

        val result = Shell.cmd(cmd).to(filteredLogs, filteredLogs).exec()
        return result.isSuccess
    }
}
