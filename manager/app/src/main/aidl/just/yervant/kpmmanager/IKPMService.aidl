package just.yervant.kpmmanager;

import android.os.Bundle;

interface IKPMService {
    boolean nativeReady(String superKey);
    long nativeKernelPatchVersion(String superKey);
    String nativeKernelPatchBuildTime(String superKey);
    long nativeLoadKernelPatchModule(String superKey, String modulePath, String args);
    long nativeUnloadKernelPatchModule(String superKey, String moduleName);
    long nativeKernelPatchModuleNum(String superKey);
    String nativeKernelPatchModuleList(String superKey);
    String nativeKernelPatchModuleInfo(String superKey, String moduleName);
    Bundle nativeControlKernelPatchModule(String superKey, String moduleName, String controlArg);
}
