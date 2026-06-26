package just.yervant.kpmmanager.util

import android.net.Uri
import android.util.Log
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
