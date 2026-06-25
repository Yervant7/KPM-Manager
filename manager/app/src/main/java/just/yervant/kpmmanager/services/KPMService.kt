package just.yervant.kpmmanager.services

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import just.yervant.kpmmanager.IKPMService
import just.yervant.kpmmanager.Natives

class KPMService : RootService() {
    override fun onBind(intent: Intent): IBinder {
        // Load the JNI library in the root process Context
        try {
            System.loadLibrary("kpmmjni")
        } catch (e: Throwable) {
            // Ignored, fallback might be handled
        }
        return object : IKPMService.Stub() {
            override fun nativeReady(superKey: String): Boolean {
                return Natives.nativeReady(superKey)
            }

            override fun nativeKernelPatchVersion(superKey: String): Long {
                return Natives.nativeKernelPatchVersion(superKey)
            }

            override fun nativeKernelPatchBuildTime(superKey: String): String {
                return Natives.nativeKernelPatchBuildTime(superKey)
            }

            override fun nativeLoadKernelPatchModule(superKey: String, modulePath: String, args: String): Long {
                return Natives.nativeLoadKernelPatchModule(superKey, modulePath, args)
            }

            override fun nativeUnloadKernelPatchModule(superKey: String, moduleName: String): Long {
                return Natives.nativeUnloadKernelPatchModule(superKey, moduleName)
            }

            override fun nativeKernelPatchModuleNum(superKey: String): Long {
                return Natives.nativeKernelPatchModuleNum(superKey)
            }

            override fun nativeKernelPatchModuleList(superKey: String): String {
                return Natives.nativeKernelPatchModuleList(superKey)
            }

            override fun nativeKernelPatchModuleInfo(superKey: String, moduleName: String): String {
                return Natives.nativeKernelPatchModuleInfo(superKey, moduleName)
            }

            override fun nativeControlKernelPatchModule(superKey: String, moduleName: String, controlArg: String): Bundle {
                val res = Natives.nativeControlKernelPatchModule(superKey, moduleName, controlArg)
                return Bundle().apply {
                    putLong("rc", res.rc)
                    putString("outMsg", res.outMsg)
                }
            }
        }
    }
}
