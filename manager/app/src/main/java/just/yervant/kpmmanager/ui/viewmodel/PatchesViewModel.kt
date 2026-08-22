package just.yervant.kpmmanager.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import just.yervant.kpmmanager.BuildConfig
import just.yervant.kpmmanager.R
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.util.Version
import just.yervant.kpmmanager.util.copyAndClose
import just.yervant.kpmmanager.util.copyAndCloseOut
import just.yervant.kpmmanager.util.inputStream
import just.yervant.kpmmanager.util.writeTo
import just.yervant.kpmmanager.util.AnyKernelHelper
import org.ini4j.Ini
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.StringReader

private const val TAG = "PatchViewModel"

class PatchesViewModel : ViewModel() {

    enum class PatchMode(val sId: Int) {
        PATCH_ONLY(R.string.patch_mode_bootimg_patch),
        PATCH_AND_INSTALL(R.string.patch_mode_patch_and_install),
        INSTALL_TO_NEXT_SLOT(R.string.patch_mode_install_to_next_slot),
        UNPATCH(R.string.patch_mode_uninstall_patch)
    }

    var bootSlot by mutableStateOf("")
    var bootDev by mutableStateOf("")
    var kimgInfo by mutableStateOf(KPModel.KImgInfo(banner = "", patched = false))
    var kpimgInfo by mutableStateOf(KPModel.KPImgInfo("", "", "", ""))
    var superkey by mutableStateOf("")
    var existedExtras = mutableStateListOf<KPModel.IExtraInfo>()
    var newExtras = mutableStateListOf<KPModel.IExtraInfo>()
    var newExtrasFileName = mutableListOf<String>()

    var isAnyKernel by mutableStateOf(false)
    var ak3KernelFileName by mutableStateOf("")
    private var ak3KernelInfo: AnyKernelHelper.KernelFileInfo? = null

    var running by mutableStateOf(false)
    var patching by mutableStateOf(false)
    var patchdone by mutableStateOf(false)
    var needReboot by mutableStateOf(false)

    var error by mutableStateOf("")
    var patchLog by mutableStateOf("")

    private val patchDir: ExtendedFile = FileSystemManager.getLocal().getFile(kpmmApp.filesDir.parent, "patch")
    private var srcBoot: ExtendedFile = patchDir.getChildFile("boot.img")
    private val ak3Dir: File get() = File(patchDir.path, "ak3")
    private val rawKernelFile: File get() = File(patchDir.path, "kernel_raw")
    private val patchedKernelFile: File get() = File(patchDir.path, "kernel_patched")
    private var prepared: Boolean = false

    private fun prepare() {
        val savedBoot = patchDir.getChildFile("boot.img").takeIf { it.exists() }
        val tempFile = savedBoot?.let { 
            File(kpmmApp.cacheDir, "preserved_boot.img").also { temp ->
                it.inputStream().copyAndCloseOut(temp.outputStream())
            }
        }

        patchDir.deleteRecursively()
        patchDir.mkdirs()

        tempFile?.let {
            it.inputStream().copyAndCloseOut(savedBoot.newOutputStream())
        }

        val execs = listOf(
            "libkptools.so", "libbusybox.so", "libbootctl.so",
        )
        error = ""

        val info = kpmmApp.applicationInfo
        val libs = File(info.nativeLibraryDir).listFiles { _, name ->
            execs.contains(name)
        } ?: emptyArray()

        for (lib in libs) {
            val name = lib.name.substring(3, lib.name.length - 3)
            Os.symlink(lib.path, "$patchDir/$name")
        }

        // Extract scripts
        for (script in listOf(
            "boot_patch.sh", "boot_unpatch.sh", "boot_extract.sh", "util_functions.sh", "kpimg"
        )) {
            val dest = File(patchDir.path, script)
            try {
                kpmmApp.assets.open(script).writeTo(dest)
            } catch (e: Exception) {
                Log.w(TAG, "Asset not found: $script: $e")
                if (script == "kpimg") {
                    error += "kpimg asset not found. Please build or install with kpimg asset.\n"
                }
            }
        }

    }

    private fun parseKpimg() {
        val kpimgFile = File(patchDir.path, "kpimg")
        if (!kpimgFile.exists()) {
            return
        }
        val result = Shell.cmd("cd $patchDir", "./kptools -l -k kpimg").exec()

        if (result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            val kpimg = ini["kpimg"]
            if (kpimg != null) {
                kpimgInfo = KPModel.KPImgInfo(
                    kpimg["version"].toString(),
                    kpimg["compile_time"].toString(),
                    kpimg["config"].toString(),
                    "",     // manager no longer keeps a separate superkey
                )
            } else {
                error += "parse kpimg error\n"
            }
        } else {
            error = result.err.joinToString("\n")
        }
    }

    private fun parseKernelImage(kernelPath: String) {
        val result = Shell.cmd(
            "cd $patchDir",
            "./kptools -l -i \"$kernelPath\"",
        ).exec()
        if (result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            Log.d(TAG, "kernel image info: $ini")

            val kernel = ini["kernel"]
            if (kernel == null) {
                error += "empty kernel section"
                Log.d(TAG, error)
                return
            }
            kimgInfo = KPModel.KImgInfo(kernel["banner"].toString(), kernel["patched"].toBoolean())
            existedExtras.clear()
            if (kimgInfo.patched) {
                val superkey = ini["kpimg"]?.getOrDefault("superkey", "") ?: ""
                kpimgInfo = kpimgInfo.copy(superKey = superkey)
                if (checkSuperKeyValidation(superkey)) {
                    this.superkey = superkey
                }
                var kpmNum = kernel["extra_num"]?.toInt()
                if (kpmNum == null) {
                    val extras = ini["extras"]
                    kpmNum = extras?.get("num")?.toInt()
                }
                if (kpmNum != null && kpmNum > 0) {
                    for (i in 0..<kpmNum) {
                        val extra = ini["extra $i"]
                        if (extra == null) {
                            error += "empty extra section"
                            break
                        }
                        val type = KPModel.ExtraType.valueOf(extra["type"]!!.uppercase())
                        val name = extra["name"].toString()
                        val args = extra["args"].toString()
                        var event = extra["event"].toString()
                        if (event.isEmpty()) {
                            event = KPModel.TriggerEvent.PRE_KERNEL_INIT.event
                        }
                        if (type == KPModel.ExtraType.KPM) {
                            val kpmInfo = KPModel.KPMInfo(
                                type, name, event, args,
                                extra["version"].toString(),
                                extra["license"].toString(),
                                extra["author"].toString(),
                                extra["description"].toString(),
                            )
                            existedExtras.add(kpmInfo)
                        }
                    }

                }
            }
        } else {
            error += result.err.joinToString("\n")
        }
    }

    private fun parseBootimg(bootimg: String) {
        val result = Shell.cmd(
            "cd $patchDir",
            "./kptools unpacknolog \"$bootimg\"",
        ).exec()
        if (result.isSuccess) {
            parseKernelImage("kernel")
        } else {
            error += result.err.joinToString("\n")
        }
    }

    val checkSuperKeyValidation: (superKey: String) -> Boolean = { superKey ->
        superKey.length in 8..63 && superKey.any { it.isDigit() } && superKey.any { it.isLetter() }
    }

    private fun copyAndParseBootimgInternal(uri: Uri) {
        error = ""
        val isAk3 = AnyKernelHelper.isAnyKernelZip(kpmmApp, uri)
        if (isAk3) {
            isAnyKernel = true
            val extracted = try {
                kpmmApp.contentResolver.openInputStream(uri)?.use { input ->
                    AnyKernelHelper.extractZip(input, ak3Dir)
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract AnyKernel3 zip: $e")
                false
            }

            if (extracted) {
                val kernelInfo = AnyKernelHelper.findKernelFile(ak3Dir)
                if (kernelInfo != null) {
                    ak3KernelInfo = kernelInfo
                    ak3KernelFileName = kernelInfo.name
                    val preparedKernel = AnyKernelHelper.prepareRawKernel(
                        kernelInfo,
                        rawKernelFile,
                        File(patchDir.path)
                    )
                    if (preparedKernel && rawKernelFile.exists()) {
                        parseKernelImage(rawKernelFile.absolutePath)
                    } else {
                        error = "Failed to prepare kernel from AnyKernel3 zip\n"
                    }
                } else {
                    error = "No supported kernel image found in AnyKernel3 zip\n"
                }
            } else {
                error = "Failed to extract AnyKernel3 zip\n"
            }
        } else {
            isAnyKernel = false
            try {
                uri.inputStream().buffered().use { src ->
                    srcBoot.also {
                        src.copyAndCloseOut(it.newOutputStream())
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "copy boot image error: $e")
            }
            parseBootimg(srcBoot.path)
        }
    }

    fun copyAndParseBootimg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (running) return@launch
            running = true
            copyAndParseBootimgInternal(uri)
            running = false
        }
    }

    private fun extractAndParseBootimg(mode: PatchMode) {
        var cmdBuilder = "./boot_extract.sh"

        if (mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
            cmdBuilder += " true"
        }

        val result = Shell.cmd(
            "export ASH_STANDALONE=1",
            "cd $patchDir",
            "./busybox sh $cmdBuilder",
        ).exec()

        if (result.isSuccess) {
            bootSlot = if (!result.out.toString().contains("SLOT=")) {
                ""
            } else {
                result.out.firstOrNull { it.startsWith("SLOT=") }?.removePrefix("SLOT=") ?: ""
            }
            bootDev =
                result.out.firstOrNull { it.startsWith("BOOTIMAGE=") }?.removePrefix("BOOTIMAGE=") ?: ""
            Log.i(TAG, "current slot: $bootSlot")
            Log.i(TAG, "current bootimg: $bootDev")
            srcBoot = FileSystemManager.getLocal().getFile(bootDev)
            parseBootimg(bootDev)
        } else {
            error = result.err.joinToString("\n")
        }
    }

    fun prepare(mode: PatchMode, uri: Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (prepared) return@launch
            prepared = true

            running = true
            error = ""
            patchLog = ""
            patching = false
            patchdone = false
            needReboot = false
            isAnyKernel = false
            ak3KernelFileName = ""
            ak3KernelInfo = null
            bootSlot = ""
            bootDev = ""
            kimgInfo = KPModel.KImgInfo(banner = "", patched = false)
            existedExtras.clear()
            newExtras.clear()
            newExtrasFileName.clear()

            prepare()
            if (mode != PatchMode.UNPATCH) {
                parseKpimg()
            }
            if (uri != null) {
                copyAndParseBootimgInternal(uri)
            } else if (mode == PatchMode.PATCH_AND_INSTALL || mode == PatchMode.UNPATCH || mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                extractAndParseBootimg(mode)
            }
            running = false
        }
    }

    fun embedKPM(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (running) return@launch
            running = true
            error = ""

            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val kpmFileName = "$rand.kpm"
            val kpmFile: ExtendedFile = patchDir.getChildFile(kpmFileName)

            Log.i(TAG, "copy kpm to: " + kpmFile.path)
            try {
                uri.inputStream().buffered().use { src ->
                    kpmFile.also {
                        src.copyAndCloseOut(it.newOutputStream())
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Copy kpm error: $e")
            }

            val result = Shell.cmd("cd $patchDir", "./kptools -l -M ${kpmFile.path}").exec()

            if (result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                val kpm = ini["kpm"]
                if (kpm != null) {
                    val kpmInfo = KPModel.KPMInfo(
                        KPModel.ExtraType.KPM,
                        kpm["name"].toString(),
                        KPModel.TriggerEvent.PRE_KERNEL_INIT.event,
                        "",
                        kpm["version"].toString(),
                        kpm["license"].toString(),
                        kpm["author"].toString(),
                        kpm["description"].toString(),
                    )
                    newExtras.add(kpmInfo)
                    newExtrasFileName.add(kpmFileName)
                }
            } else {
                error = "Invalid KPM\n"
            }
            running = false
        }
    }

    fun doUnpatch() {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            patchLog = ""
            Log.i(TAG, "starting unpatching...")

            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.i(TAG, "" + e)
                    patchLog += "\n"
                }
            }

            logs.add("****************************")

            val result = Shell.cmd(
                "export ASH_STANDALONE=1",
                "cd $patchDir",
                "./busybox sh boot_unpatch.sh \"$bootDev\""
            ).to(logs, logs).exec()

            if (!result.isSuccess) {
                error = " Unpatch failed."
                logs.add(error)
            } else {
                logs.add("- Unpatch success, reboot to finish.")
                needReboot = true
            }

            logs.add("****************************")
            patchdone = true
            patching = false
        }
    }
    fun doPatch(mode: PatchMode, useKey: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            Log.d(TAG, "starting patching...")

            val apVer = Version.getManagerVersion().second
            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val outFilename = "kpmm_patched_${apVer}_${Version.buildKpmmVString()}_${rand}.img"

            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.d(TAG, "" + e)
                    patchLog += "\n"
                }
            }
            logs.add("****************************")

            var succ = false

            if (isAnyKernel) {
                logs.add("- Patching AnyKernel3 kernel image...")
                val superkey = if (useKey && this@PatchesViewModel.superkey.isNotEmpty()) this@PatchesViewModel.superkey else "su"
                val patchCommand = mutableListOf("./kptools", "-p", "-i", rawKernelFile.absolutePath, "-k", "kpimg", "-s", superkey, "-o", patchedKernelFile.absolutePath)

                for (i in newExtrasFileName.indices) {
                    patchCommand.addAll(listOf("-M", newExtrasFileName[i]))
                    val extra = newExtras[i]
                    if (extra.args.isNotEmpty()) {
                        patchCommand.addAll(listOf("-A", extra.args))
                    }
                    if (extra.event.isNotEmpty()) {
                        patchCommand.addAll(listOf("-V", extra.event))
                    }
                    patchCommand.addAll(listOf("-T", extra.type.desc))
                }
                for (i in existedExtras.indices) {
                    val extra = existedExtras[i]
                    patchCommand.addAll(listOf("-E", extra.name))
                    if (extra.args.isNotEmpty()) {
                        patchCommand.addAll(listOf("-A", extra.args))
                    }
                    if (extra.event.isNotEmpty()) {
                        patchCommand.addAll(listOf("-V", extra.event))
                    }
                    patchCommand.addAll(listOf("-T", extra.type.desc))
                }

                val commandString = patchCommand.joinToString(" ") {
                    if (it.contains(" ") || it.contains("$") || it.contains("\"")) {
                        "\"" + it.replace("\"", "\\\"").replace("$", "\\$") + "\""
                    } else {
                        it
                    }
                }

                val result = Shell.cmd(
                    "export ASH_STANDALONE=1",
                    "cd $patchDir",
                    commandString
                ).to(logs, logs).exec()

                if (!result.isSuccess || !patchedKernelFile.exists()) {
                    val msg = " Patching kernel image failed."
                    error = msg
                    logs.add(error)
                    logs.add("****************************")
                    patching = false
                    return@launch
                }

                logs.add("- Updating kernel inside AnyKernel3...")
                val repacked = ak3KernelInfo?.let {
                    AnyKernelHelper.repackPatchedKernel(patchedKernelFile, it, File(patchDir.path))
                } ?: false

                if (!repacked) {
                    error = "Failed to update AnyKernel3 kernel file."
                    logs.add(error)
                    logs.add("****************************")
                    patching = false
                    return@launch
                }

                if (mode == PatchMode.PATCH_ONLY) {
                    val outFilename = "kpmm_patched_ak3_${apVer}_${Version.buildKpmmVString()}_${rand}.zip"
                    val outZipFile = File(patchDir.path, outFilename)
                    logs.add("- Building patched AnyKernel3 zip...")
                    val zipCreated = AnyKernelHelper.createPatchedZip(ak3Dir, outZipFile)
                    if (!zipCreated || !outZipFile.exists()) {
                        error = "Failed to create patched zip."
                        logs.add(error)
                        logs.add("****************************")
                        patching = false
                        return@launch
                    }

                    val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!outDir.exists()) outDir.mkdirs()
                    val outPath = File(outDir, outFilename)
                    val inputUri = outZipFile.getUri(kpmmApp)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val outUri = createDownloadUri(kpmmApp, outFilename)
                        succ = insertDownload(kpmmApp, outUri, inputUri)
                    } else {
                        outZipFile.inputStream().copyAndClose(outPath.outputStream())
                        succ = true
                    }
                    if (succ) {
                        logs.add("- Patched AnyKernel3 zip generated successfully!")
                        logs.add(" Output file is written to ")
                        logs.add(" ${outPath.path}")
                    } else {
                        logs.add(" Write patched AnyKernel3 zip failed")
                    }
                } else if (mode == PatchMode.PATCH_AND_INSTALL || mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                    val flashSucc = AnyKernelHelper.flashAnyKernel(ak3Dir, File(patchDir.path), logs)
                    if (flashSucc) {
                        logs.add("- Installation successful! Reboot to apply.")
                        needReboot = true
                    } else {
                        error = " AnyKernel3 installation failed."
                        logs.add(error)
                    }
                }
                logs.add("****************************")
                patchdone = true
                patching = false
                return@launch
            }

            var patchCommand = mutableListOf("./busybox", "sh", "boot_patch.sh")

            val superkey = if (useKey && this@PatchesViewModel.superkey.isNotEmpty()) this@PatchesViewModel.superkey else "su"

            if (mode == PatchMode.PATCH_AND_INSTALL || mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                patchCommand.addAll(listOf(superkey, srcBoot.path, "true"))
            } else {
                patchCommand.addAll(listOf(superkey, srcBoot.path))
            }

            for (i in newExtrasFileName.indices) {
                patchCommand.addAll(listOf("-M", newExtrasFileName[i]))
                val extra = newExtras[i]
                if (extra.args.isNotEmpty()) {
                    patchCommand.addAll(listOf("-A", extra.args))
                }
                if (extra.event.isNotEmpty()) {
                    patchCommand.addAll(listOf("-V", extra.event))
                }
                patchCommand.addAll(listOf("-T", extra.type.desc))
            }
            for (i in existedExtras.indices) {
                val extra = existedExtras[i]
                patchCommand.addAll(listOf("-E", extra.name))
                if (extra.args.isNotEmpty()) {
                    patchCommand.addAll(listOf("-A", extra.args))
                }
                if (extra.event.isNotEmpty()) {
                    patchCommand.addAll(listOf("-V", extra.event))
                }
                patchCommand.addAll(listOf("-T", extra.type.desc))
            }

            val commandString = patchCommand.joinToString(" ") {
                if (it.contains(" ") || it.contains("$") || it.contains("\"")) {
                    "\"" + it.replace("\"", "\\\"").replace("$", "\\$") + "\""
                } else {
                    it
                }
            }

            val result = Shell.cmd(
                "export ASH_STANDALONE=1",
                "cd $patchDir",
                commandString
            ).to(logs, logs).exec()

            succ = result.isSuccess

            if (!succ) {
                val msg = " Patch failed."
                error = msg
//                error += result.err.joinToString("\n")
                logs.add(error)
                logs.add("****************************")
                patching = false
                return@launch
            }

            if (mode == PatchMode.PATCH_AND_INSTALL) {
                logs.add("- Reboot to finish the installation...")
                needReboot = true
            } else if (mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                logs.add("- Connecting boot hal...")
                val bootctlStatus = Shell.cmd(
                    "cd $patchDir", "chmod 0777 $patchDir/bootctl", "./bootctl hal-info"
                ).to(logs, logs).exec()
                if (!bootctlStatus.isSuccess) {
                    logs.add("[X] Failed to connect to boot hal, you may need switch slot manually")
                } else {
                    val currSlot = Shell.cmd(
                        "cd $patchDir", "./bootctl get-current-slot"
                    ).exec().out.toString()
                    val targetSlot = if (currSlot.contains("0")) {
                        1
                    } else {
                        0
                    }
                    logs.add("- Switching to next slot: $targetSlot...")
                    val setNextActiveSlot = Shell.cmd(
                        "cd $patchDir", "./bootctl set-active-boot-slot $targetSlot"
                    ).exec()
                    if (setNextActiveSlot.isSuccess) {
                        logs.add("- Switch done")
                    }
                }
                logs.add("- Reboot to finish the installation...")
                needReboot = true
            } else if (mode == PatchMode.PATCH_ONLY) {
                val newBootFile = patchDir.getChildFile("new-boot.img")
                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!outDir.exists()) outDir.mkdirs()
                val outPath = File(outDir, outFilename)
                val inputUri = newBootFile.getUri(kpmmApp)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val outUri = createDownloadUri(kpmmApp, outFilename)
                    succ = insertDownload(kpmmApp, outUri, inputUri)
                } else {
                    newBootFile.inputStream().copyAndClose(outPath.outputStream())
                }
                if (succ) {
                    logs.add(" Output file is written to ")
                    logs.add(" ${outPath.path}")
                } else {
                    logs.add(" Write patched boot.img failed")
                }
            }
            logs.add("****************************")
            patchdone = true
            patching = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createDownloadUri(context: Context, outFilename: String): Uri? {
        val mimeType = if (outFilename.endsWith(".zip")) "application/zip" else "application/octet-stream"
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, outFilename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun insertDownload(context: Context, outUri: Uri?, inputUri: Uri): Boolean {
        if (outUri == null) return false

        try {
            val resolver = context.contentResolver
            resolver.openInputStream(inputUri)?.use { inputStream ->
                resolver.openOutputStream(outUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(outUri, contentValues, null, null)

            return true
        } catch (_: FileNotFoundException) {
            return false
        }
    }

    fun File.getUri(context: Context): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, this)
    }

}
