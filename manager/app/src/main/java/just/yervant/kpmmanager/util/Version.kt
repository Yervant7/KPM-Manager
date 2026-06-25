package just.yervant.kpmmanager.util

import androidx.core.content.pm.PackageInfoCompat
import just.yervant.kpmmanager.Natives
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.BuildConfig
import org.ini4j.Ini
import java.io.StringReader
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.File
import android.system.Os


/**
 * version string is like 0.9.0 or 0.9.0-dev
 * version uint is hex number like: 0x000900
 */
object Version {

    private fun string2UInt(ver: String): UInt {
        val v = ver.trim().split("-")[0]
        val vn = v.split('.')
        val vi = vn[0].toInt().shl(16) + vn[1].toInt().shl(8) + vn[2].toInt()
        return vi.toUInt()
    }

    fun getKpImg(): String {
        val patchDir: ExtendedFile = FileSystemManager.getLocal().getFile(kpmmApp.filesDir.parent, "check")
        patchDir.deleteRecursively()
        patchDir.mkdirs()
        val execs = listOf(
            "libkptools.so",  "libbusybox.so"
        )

        val info = kpmmApp.applicationInfo
        val libs = File(info.nativeLibraryDir).listFiles { _, name ->
            execs.contains(name)
        } ?: emptyArray()

        for (lib in libs) {
            val name = lib.name.substring(3, lib.name.length - 3)
            Os.symlink(lib.path, "$patchDir/$name")
        }

        for (script in listOf(
            "boot_patch.sh", "boot_unpatch.sh", "boot_extract.sh", "util_functions.sh", "kpimg"
        )) {
            val dest = File(patchDir, script)
            kpmmApp.assets.open(script).writeTo(dest)
        }

        return withNewRootShell {
            val result = shellForResult(
                this, "cd $patchDir", "./kptools -l -k kpimg"
            )

            if (result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                val kpimg = ini["kpimg"]
                if (kpimg != null) {
                    return@withNewRootShell kpimg["compile_time"].toString()
                }
            }

            "unknown"
        }
    }

    fun uInt2String(ver: UInt): String {
        return "%d.%d.%d".format(
            ver.and(0xff0000u).shr(16).toInt(),
            ver.and(0xff00u).shr(8).toInt(),
            ver.and(0xffu).toInt()
        )
    }
    
    fun installedKPTime(): String {
        val time = Natives.kernelPatchBuildTime()
        return if (time.startsWith("ERROR_")) "读取失败" else time
    }

    fun buildKpmmVUInt(): UInt {
        val buildVS = BuildConfig.buildKpmmV
        return string2UInt(buildVS)
    }

    fun buildKpmmVString(): String {
        return BuildConfig.buildKpmmV
    }

    /**
     * installed KernelPatch version (installed kpimg)
     */
    fun installedKPVUInt(): UInt {
        return Natives.kernelPatchVersion().toUInt()
    }

    fun installedKPVString(): String {
        return uInt2String(installedKPVUInt())
    }

    fun getManagerVersion(): Pair<String, Long> {
        val packageInfo = kpmmApp.packageManager.getPackageInfo(kpmmApp.packageName, 0)!!
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        return Pair(packageInfo.versionName!!, versionCode)
    }
}
