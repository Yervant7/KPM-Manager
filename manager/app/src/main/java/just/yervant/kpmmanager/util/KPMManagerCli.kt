package just.yervant.kpmmanager.util

import android.net.Uri
import android.util.Log
import android.system.Os
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import just.yervant.kpmmanager.Natives
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.ui.screen.MODULE_TYPE
import java.io.File

private const val TAG = "KPM-ManagerCli"

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
            onStdout("- Loading KPM module: ${file.path}")
            val res = Natives.loadKernelPatchModule(file.path, "")
            if (res.rc == 0L) {
                onStdout("- KPM module loaded successfully")
                result = true
            } else {
                onStderr("- KPM module load failed with code: ${res.rc} msg: ${res.msg ?: ""}")
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
