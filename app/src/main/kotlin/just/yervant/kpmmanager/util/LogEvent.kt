package just.yervant.kpmmanager.util

import android.content.Context
import android.os.Build
import android.system.Os
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun getBugreportFile(context: Context): File {

    val bugreportDir = File(context.cacheDir, "bugreport")
    bugreportDir.mkdirs()

    val dmesgFile = File(bugreportDir, "dmesg.txt")
    val logcatFile = File(bugreportDir, "logcat.txt")
    val tombstonesFile = File(bugreportDir, "tombstones.tar.gz")
    val dropboxFile = File(bugreportDir, "dropbox.tar.gz")
    val pstoreFile = File(bugreportDir, "pstore.tar.gz")
    val diagFile = File(bugreportDir, "diag.tar.gz")
    val bootlogFile = File(bugreportDir, "bootlog.tar.gz")
    val propFile = File(bugreportDir, "props.txt")
    val kernelConfig = File(bugreportDir, "defconfig")

    Shell.cmd("dmesg > ${dmesgFile.absolutePath}").exec()
    Shell.cmd("logcat -d > ${logcatFile.absolutePath}").exec()
    Shell.cmd("tar -czf ${tombstonesFile.absolutePath} -C /data/tombstones .").exec()
    Shell.cmd("tar -czf ${dropboxFile.absolutePath} -C /data/system/dropbox .").exec()
    Shell.cmd("tar -czf ${pstoreFile.absolutePath} -C /sys/fs/pstore .").exec()
    Shell.cmd("tar -czf ${diagFile.absolutePath} -C /data/vendor/diag .").exec()
    Shell.cmd("getprop > ${propFile.absolutePath}").exec()
    Shell.cmd("zcat /proc/config.gz > ${kernelConfig.absolutePath}").exec()

    val selinux = Shell.cmd("getenforce").exec().out

    // basic information
    val buildInfo = File(bugreportDir, "basic.txt")
    PrintWriter(FileWriter(buildInfo)).use { pw ->
        pw.println("Kernel: ${System.getProperty("os.version")}")
        pw.println("BRAND: " + Build.BRAND)
        pw.println("MODEL: " + Build.MODEL)
        pw.println("PRODUCT: " + Build.PRODUCT)
        pw.println("MANUFACTURER: " + Build.MANUFACTURER)
        pw.println("SDK: " + Build.VERSION.SDK_INT)
        pw.println("PREVIEW_SDK: " + Build.VERSION.PREVIEW_SDK_INT)
        pw.println("FINGERPRINT: " + Build.FINGERPRINT)
        pw.println("DEVICE: " + Build.DEVICE)
        pw.println("Manager: " + Version.getManagerVersion())
        pw.println("SELinux: $selinux")

        val uname = Os.uname()
        pw.println("KernelRelease: ${uname.release}")
        pw.println("KernelVersion: ${uname.version}")
        pw.println("Mahcine: ${uname.machine}")
        pw.println("Nodename: ${uname.nodename}")
        pw.println("Sysname: ${uname.sysname}")

        pw.println("KPatch: ${Version.installedKPVString()}")
        val safeMode = false
        pw.println("SafeMode: $safeMode")
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
    val current = LocalDateTime.now().format(formatter)

    val targetFile = File(context.cacheDir, "KPM-Manager_bugreport_${current}.tar.gz")

    Shell.cmd("tar czf ${targetFile.absolutePath} -C ${bugreportDir.absolutePath} .")
        .exec()
    Shell.cmd("rm -rf ${bugreportDir.absolutePath}").exec()
    Shell.cmd("chmod 0644 ${targetFile.absolutePath}").exec()

    return targetFile
}
