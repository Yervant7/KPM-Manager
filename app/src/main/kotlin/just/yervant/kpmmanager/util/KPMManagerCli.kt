package just.yervant.kpmmanager.util

import android.net.Uri
import android.util.Log
import android.system.Os
import android.content.Context
import android.provider.OpenableColumns
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import just.yervant.kpmmanager.Natives
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.ui.screen.MODULE_TYPE
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private const val TAG = "KPM-ManagerCli"
private const val ADB_KPM_DIR = "/data/adb/kpm"

fun reboot(reason: String = "") {
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        Shell.cmd("/system/bin/input keyevent 26").exec()
    }
    Shell.cmd("/system/bin/svc power reboot $reason || /system/bin/reboot $reason").exec()
}

fun rootAvailable(): Boolean {
    return Shell.isAppGrantedRoot() == true
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun removeInstalledKpm(moduleName: String): Boolean {
    if (moduleName.isEmpty()) return false
    val sanitized = moduleName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val commands = listOf(
        "rm -rf '$ADB_KPM_DIR/$sanitized.kpm' '$ADB_KPM_DIR/$sanitized.kpm.disable' '$ADB_KPM_DIR/$sanitized.disable' '$ADB_KPM_DIR/$sanitized'",
        "rm -rf '/data/adb/ap/kpm/$sanitized.kpm' '/data/adb/ap/kpm/$sanitized.kpm.disable' '/data/adb/ap/kpm/$sanitized.disable' '/data/adb/ap/kpm/$sanitized'"
    )
    val res = Shell.cmd(*commands.toTypedArray()).exec()
    Log.i(TAG, "removeInstalledKpm $moduleName result: ${res.isSuccess}")
    return res.isSuccess
}

fun installModule(
    uri: Uri, type: MODULE_TYPE, onFinish: (Boolean) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val inputStream = try {
        kpmmApp.contentResolver.openInputStream(uri)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open input stream for $uri: $e")
        null
    }
    if (inputStream == null) {
        onStderr("- Failed to open input stream for: $uri")
        onFinish(false)
        return false
    }

    val ext = if (type == MODULE_TYPE.ANYKERNEL3) "zip" else "kpm"
    val file = File(kpmmApp.cacheDir, "module_install_${System.currentTimeMillis()}.$ext")
    try {
        inputStream.buffered().use { input ->
            file.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        onStderr("- Failed to cache module file: $e")
        file.delete()
        onFinish(false)
        return false
    }

    var result = false

    when (type) {
        MODULE_TYPE.KPM -> {
            onStdout("- Preparing KPM installation...")
            onStdout("- Target directory: $ADB_KPM_DIR")

            val mkdirRes = Shell.cmd("mkdir -p $ADB_KPM_DIR && chmod 755 /data/adb $ADB_KPM_DIR").exec()
            if (!mkdirRes.isSuccess) {
                onStderr("- Failed to create $ADB_KPM_DIR: ${mkdirRes.err.joinToString("\n")}")
            }

            // Check if file is actually a zip package containing .kpm
            val isZip = try {
                val header = ByteArray(4)
                FileInputStream(file).use { it.read(header) }
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            } catch (_: Exception) {
                false
            }

            if (isZip) {
                onStdout("- Detected ZIP package, extracting KPM module(s)...")
                val extractDir = File(kpmmApp.cacheDir, "kpm_zip_${System.currentTimeMillis()}")
                extractDir.mkdirs()
                var kpmCount = 0

                try {
                    ZipInputStream(FileInputStream(file).buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".kpm", ignoreCase = true)) {
                                val entryFileName = File(entry.name).name
                                val sanitizedName = entryFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                val targetDest = File(extractDir, sanitizedName)
                                FileOutputStream(targetDest).buffered().use { fos ->
                                    zis.copyTo(fos)
                                }
                                kpmCount++
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract ZIP: $e")
                    onStderr("- Error extracting ZIP: ${e.message}")
                }

                if (kpmCount > 0) {
                    var allSuccess = true
                    extractDir.listFiles { _, name -> name.endsWith(".kpm", ignoreCase = true) }?.forEach { kpmFile ->
                        val targetPath = "$ADB_KPM_DIR/${kpmFile.name}"
                        onStdout("- Installing KPM module: ${kpmFile.name} -> $targetPath")
                        val cpRes = Shell.cmd("cp '${kpmFile.absolutePath}' '$targetPath' && chmod 644 '$targetPath'").exec()
                        if (!cpRes.isSuccess) {
                            onStderr("- Failed to copy ${kpmFile.name} to $targetPath: ${cpRes.err.joinToString("\n")}")
                            allSuccess = false
                        } else {
                            val stem = kpmFile.name.removeSuffix(".kpm")
                            Shell.cmd("rm -f '$targetPath.disable' '$ADB_KPM_DIR/$stem.disable'").exec()
                            onStdout("- Loading KPM module into kernel: $targetPath")
                            val res = Natives.loadKernelPatchModule(targetPath, "")
                            if (res.rc == 0L) {
                                onStdout("- KPM module ${kpmFile.name} installed and loaded successfully!")
                            } else {
                                onStdout("- KPM module ${kpmFile.name} installed to $ADB_KPM_DIR (live load: ${res.rc} ${res.msg ?: ""})")
                            }
                        }
                    }
                    result = allSuccess
                } else {
                    onStderr("- No .kpm modules found in the ZIP archive.")
                    result = false
                }
                extractDir.deleteRecursively()
            } else {
                val originalName = getFileName(kpmmApp, uri) ?: "module.kpm"
                val targetName = if (originalName.endsWith(".kpm", ignoreCase = true)) originalName else "$originalName.kpm"
                val sanitizedName = targetName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "module_${System.currentTimeMillis()}.kpm" }
                val targetPath = "$ADB_KPM_DIR/$sanitizedName"

                onStdout("- Installing KPM module: $sanitizedName -> $targetPath")
                val cpRes = Shell.cmd("cp '${file.absolutePath}' '$targetPath' && chmod 644 '$targetPath'").exec()
                if (!cpRes.isSuccess) {
                    onStderr("- Failed to copy KPM module to $targetPath: ${cpRes.err.joinToString("\n")}")
                    result = false
                } else {
                    val stem = sanitizedName.removeSuffix(".kpm")
                    Shell.cmd("rm -f '$targetPath.disable' '$ADB_KPM_DIR/$stem.disable'").exec()

                    onStdout("- Setting permissions 0644 for $targetPath")
                    onStdout("- Loading KPM module into kernel...")
                    val res = Natives.loadKernelPatchModule(targetPath, "")
                    if (res.rc == 0L) {
                        onStdout("- KPM module installed to $targetPath and loaded successfully!")
                        result = true
                    } else {
                        onStdout("- KPM module installed to $targetPath (autoload on boot active).")
                        onStderr("- Notice: live load returned code: ${res.rc} msg: ${res.msg ?: ""}")
                        result = true
                    }
                }
            }
        }

        MODULE_TYPE.ANYKERNEL3 -> {
            onStdout("- Preparing AnyKernel3 package...")
            val patchDir = File(kpmmApp.filesDir.parent, "patch")
            patchDir.mkdirs()

            val info = kpmmApp.applicationInfo
            val execs = listOf("libkptools.so", "libbusybox.so", "libbootctl.so")
            val libs = File(info.nativeLibraryDir).listFiles { _, name -> execs.contains(name) } ?: emptyArray()
            for (lib in libs) {
                val name = lib.name.substring(3, lib.name.length - 3)
                try {
                    val symlink = File(patchDir, name)
                    symlink.delete()
                    Os.symlink(lib.path, symlink.path)
                } catch (_: Exception) {}
            }
            for (script in listOf("boot_patch.sh", "boot_unpatch.sh", "boot_extract.sh", "util_functions.sh", "kpimg")) {
                try {
                    val dest = File(patchDir, script)
                    kpmmApp.assets.open(script).writeTo(dest)
                } catch (_: Exception) {}
            }

            val ak3Dir = File(patchDir, "ak3")
            onStdout("- Extracting AnyKernel3 zip...")
            val extracted = try {
                file.inputStream().buffered().use { input ->
                    AnyKernelHelper.extractZip(input, ak3Dir)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extract AnyKernel3 error: $e")
                false
            }

            if (!extracted) {
                onStderr("- Failed to extract AnyKernel3 zip")
                file.delete()
                onFinish(false)
                return false
            }

            val kernelInfo = AnyKernelHelper.findKernelFile(ak3Dir)
            if (kernelInfo == null) {
                onStderr("- No supported kernel image found in AnyKernel3 package!")
                file.delete()
                onFinish(false)
                return false
            }
            onStdout("- Detected kernel: ${kernelInfo.name} (${kernelInfo.format})")

            val rawKernel = File(patchDir, "kernel_raw")
            val patchedKernel = File(patchDir, "kernel_patched")
            val prepared = AnyKernelHelper.prepareRawKernel(kernelInfo, rawKernel, patchDir)
            if (!prepared || !rawKernel.exists()) {
                onStderr("- Failed to decompress/prepare kernel image.")
                file.delete()
                onFinish(false)
                return false
            }

            onStdout("- Injecting KernelPatch (kpimg) into kernel...")
            val superkey = just.yervant.kpmmanager.KPMMApplication.superKey.ifEmpty { "su" }
            val patchCmd = "./kptools -p -i \"${rawKernel.absolutePath}\" -k kpimg -s \"$superkey\" -o \"${patchedKernel.absolutePath}\""
            val patchRes = Shell.cmd("cd \"${patchDir.absolutePath}\"", patchCmd).exec()
            if (!patchRes.isSuccess || !patchedKernel.exists()) {
                onStderr("- Failed to patch kernel with kptools: ${patchRes.err.joinToString("\n")}")
                file.delete()
                onFinish(false)
                return false
            }
            onStdout("- Kernel patched successfully.")

            val repacked = AnyKernelHelper.repackPatchedKernel(patchedKernel, kernelInfo, patchDir)
            if (!repacked) {
                onStderr("- Failed to update patched kernel inside AnyKernel3.")
                file.delete()
                onFinish(false)
                return false
            }

            onStdout("- Starting AnyKernel3 flashing process...")
            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    e?.let { line ->
                        val cleanLine = if (line.startsWith("ui_print ")) line.substring(9).trim() else line
                        if (cleanLine.isNotEmpty()) {
                            onStdout(cleanLine)
                        }
                    }
                }
            }
            val flashSucc = AnyKernelHelper.flashAnyKernel(ak3Dir, patchDir, logs)
            if (flashSucc) {
                onStdout("- AnyKernel3 installation completed successfully!")
                onStdout("- Please reboot your device to apply the new kernel.")
                result = true
            } else {
                onStderr("- AnyKernel3 installation failed.")
                result = false
            }
        }
    }

    Log.i(TAG, "install $type module $uri result: $result")

    file.delete()

    onFinish(result)
    return result
}
