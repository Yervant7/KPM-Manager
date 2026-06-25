package just.yervant.kpmmanager

import android.os.Bundle
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import dalvik.annotation.optimization.FastNative
import kotlinx.parcelize.Parcelize
import just.yervant.kpmmanager.services.KPMServiceConnection

object Natives {
    init {
        try {
            System.loadLibrary("kpmmjni")
        } catch (e: Throwable) {
            // Ignored, service will load it in its process
        }
    }

    @Keep
    class KPMCtlRes {
        var rc: Long = 0
        var outMsg: String? = null

        constructor()

        constructor(rc: Long, outMsg: String?) {
            this.rc = rc
            this.outMsg = outMsg
        }
    }

    @FastNative
    external fun nativeReady(superKey: String): Boolean
    fun ready(superKey: String): Boolean {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeReady(superKey)
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    @FastNative
    external fun nativeKernelPatchVersion(superKey: String): Long
    fun kernelPatchVersion(): Long {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeKernelPatchVersion(KPMMApplication.superKey)
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
    }

    @FastNative
    external fun nativeKernelPatchBuildTime(superKey: String): String
    fun kernelPatchBuildTime(): String {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeKernelPatchBuildTime(KPMMApplication.superKey)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    external fun nativeLoadKernelPatchModule(
        superKey: String, modulePath: String, args: String
    ): Long
    fun loadKernelPatchModule(modulePath: String, args: String): Long {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeLoadKernelPatchModule(KPMMApplication.superKey, modulePath, args)
            } catch (e: Exception) {
                -1L
            }
        } else {
            -1L
        }
    }

    external fun nativeUnloadKernelPatchModule(superKey: String, moduleName: String): Long
    fun unloadKernelPatchModule(moduleName: String): Long {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeUnloadKernelPatchModule(KPMMApplication.superKey, moduleName)
            } catch (e: Exception) {
                -1L
            }
        } else {
            -1L
        }
    }

    @FastNative
    external fun nativeKernelPatchModuleNum(superKey: String): Long
    fun kernelPatchModuleNum(): Long {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeKernelPatchModuleNum(KPMMApplication.superKey)
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
    }

    @FastNative
    external fun nativeKernelPatchModuleList(superKey: String): String
    fun kernelPatchModuleList(): String {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeKernelPatchModuleList(KPMMApplication.superKey)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    @FastNative
    external fun nativeKernelPatchModuleInfo(superKey: String, moduleName: String): String
    fun kernelPatchModuleInfo(moduleName: String): String {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                s.nativeKernelPatchModuleInfo(KPMMApplication.superKey, moduleName)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    external fun nativeControlKernelPatchModule(
        superKey: String, modName: String, jctlargs: String
    ): KPMCtlRes
    fun kernelPatchModuleControl(moduleName: String, controlArg: String): KPMCtlRes {
        val s = KPMServiceConnection.service
        return if (s != null) {
            try {
                val bundle = s.nativeControlKernelPatchModule(KPMMApplication.superKey, moduleName, controlArg)
                KPMCtlRes(bundle.getLong("rc"), bundle.getString("outMsg"))
            } catch (e: Exception) {
                KPMCtlRes(-1, null)
            }
        } else {
            KPMCtlRes(-1, null)
        }
    }
}
