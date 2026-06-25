package just.yervant.kpmmanager.util

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.MainShell
import com.topjohnwu.superuser.io.SuFile
import just.yervant.kpmmanager.KPMMApplication
import just.yervant.kpmmanager.BuildConfig
import just.yervant.kpmmanager.Natives
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.ui.screen.MODULE_TYPE
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.zip.ZipFile

private const val TAG = "KPM-ManagerCli"

class RootShellInitializer : Shell.Initializer() {
    override fun onInit(context: Context, shell: Shell): Boolean {
        shell.newJob().add("export PATH=\$PATH:/system_ext/bin:/vendor/bin").exec()
        return true
    }
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create().setInitializers(RootShellInitializer::class.java)
    return try {
        if (globalMnt) {
            builder.build("su","-mm")
        }else{
            builder.build("su")
        }
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        return builder.build("sh")
    }
}

private fun createMainRootShell() : Shell {
    val builder = Shell.Builder.create()
        .setInitializers(RootShellInitializer::class.java)
    builder.setCommands("su")
    val shell = try {
        builder.build()
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        builder.setCommands("sh")
        builder.build()
    }

    MainShell.setBuilder(builder)
    return shell
}

object KPMManagerCli {
    var SHELL: Shell = createMainRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
    fun refresh() {
        val tmp = SHELL

        val clazz = MainShell::class.java // reset MainShell
        clazz.getDeclaredField("isInitMain").apply {
            isAccessible = true
            setBoolean(null, false)
            isAccessible = false
        }

        clazz.getDeclaredField("mainShell").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val arr = get(null) as Array<Any?>
            arr[0] = null
            isAccessible = false
        }

        clazz.getDeclaredField("mainBuilder").apply {
            isAccessible = true
            set(null, null)
            isAccessible = false
        }

        SHELL = createMainRootShell()
        tmp.close()
    }
}

fun getRootShell(globalMnt: Boolean = false): Shell {

    return if (globalMnt) KPMManagerCli.GLOBAL_MNT_SHELL else {
        KPMManagerCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun rootAvailable(): Boolean {
    val shell = getRootShell()
    return shell.isRoot
}

fun tryGetRootShell(): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        builder.build("su")
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        builder.build("sh")
    }
}

fun shellForResult(shell: Shell, vararg cmds: String): Shell.Result {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    return shell.newJob().add(*cmds).to(out, err).exec()
}

fun rootShellForResult(vararg cmds: String): Shell.Result {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    return getRootShell().newJob().add(*cmds).to(out, err).exec()
}

fun reboot(reason: String = "") {
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        getRootShell().newJob().add("/system/bin/input keyevent 26").exec()
    }
    getRootShell().newJob()
        .add("/system/bin/svc power reboot $reason || /system/bin/reboot $reason").exec()
}

fun installModule(
    uri: Uri, type: MODULE_TYPE, onFinish: (Boolean) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val resolver = kpmmApp.contentResolver
    with(resolver.openInputStream(uri)) {
        val file = File(kpmmApp.cacheDir, "module_$type.zip")
        file.outputStream().use { output ->
            this?.copyTo(output)
        }

        var result = false

        when (type) {
            MODULE_TYPE.KPM -> {
                onStdout("- Loading KPM module: ${file.path}")
                val rc = Natives.loadKernelPatchModule(file.path, "")
                if (rc == 0L) {
                    onStdout("- KPM module loaded successfully")
                    result = true
                } else {
                    onStderr("- KPM module load failed with code: $rc")
                }
            }
        }

        Log.i(TAG, "install $type module $uri result: $result")

        file.delete()

        onFinish(result)
        return result
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}
