/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 * Copyright (C) 2024 GarfieldHan. All Rights Reserved.
 * Copyright (C) 2024 1f2003d5. All Rights Reserved.
 */

#include <cstring>
#include <vector>

#include "kpmmjni.hpp"
#include "supercall.h"

jboolean nativeReady(JNIEnv *env, jobject /* this */, jstring super_key_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    if (super_key.get() == nullptr || strlen(super_key.get()) == 0) {
        return sc_ready("su");
    }
    return sc_ready(super_key.get());
}

jlong nativeKernelPatchVersion(JNIEnv *env, jobject /* this */, jstring super_key_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);

    return sc_kp_ver(super_key.get());
}

jstring nativeKernelPatchBuildTime(JNIEnv *env, jobject /* this */, jstring super_key_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    char buf[4096] = { '\0' };

    sc_get_build_time(super_key.get(), buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

jlong nativeLoadKernelPatchModule(JNIEnv *env, jobject /* this */, jstring super_key_jstr, jstring module_path_jstr,
                                  jstring args_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    const auto module_path = JUTFString(env, module_path_jstr);
    const auto args = JUTFString(env, args_jstr);
    long rc = sc_kpm_load(super_key.get(), module_path.get(), args.get(), nullptr);
    if (rc < 0) [[unlikely]] {
        LOGE("nativeLoadKernelPatchModule error: %ld", rc);
    }

    return rc;
}

jobject nativeControlKernelPatchModule(JNIEnv *env, jobject /* this */, jstring super_key_jstr,
                                       jstring module_name_jstr, jstring control_args_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    const auto module_name = JUTFString(env, module_name_jstr);
    const auto control_args = JUTFString(env, control_args_jstr);

    char buf[4096] = { '\0' };
    long rc = sc_kpm_control(super_key.get(), module_name.get(), control_args.get(), buf, sizeof(buf));
    if (rc < 0) [[unlikely]] {
        LOGE("nativeControlKernelPatchModule error: %ld", rc);
    }

    jclass cls = env->FindClass("just/yervant/kpmmanager/Natives$KPMCtlRes");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");
    jfieldID rcField = env->GetFieldID(cls, "rc", "J");
    jfieldID outMsg = env->GetFieldID(cls, "outMsg", "Ljava/lang/String;");

    jobject obj = env->NewObject(cls, constructor);
    env->SetLongField(obj, rcField, rc);
    env->SetObjectField(obj, outMsg, env->NewStringUTF(buf));

    return obj;
}

jlong nativeUnloadKernelPatchModule(JNIEnv *env, jobject /* this */, jstring super_key_jstr, jstring module_name_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    const auto module_name = JUTFString(env, module_name_jstr);
    long rc = sc_kpm_unload(super_key.get(), module_name.get(), nullptr);
    if (rc < 0) [[unlikely]] {
        LOGE("nativeUnloadKernelPatchModule error: %ld", rc);
    }

    return rc;
}

jlong nativeKernelPatchModuleNum(JNIEnv *env, jobject /* this */, jstring super_key_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    long rc = sc_kpm_nums(super_key.get());
    if (rc < 0) [[unlikely]] {
        LOGE("nativeKernelPatchModuleNum error: %ld", rc);
    }

    return rc;
}

jstring nativeKernelPatchModuleList(JNIEnv *env, jobject /* this */, jstring super_key_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);

    char buf[4096] = { '\0' };
    long rc = sc_kpm_list(super_key.get(), buf, sizeof(buf));
    if (rc < 0) [[unlikely]] {
        LOGE("nativeKernelPatchModuleList error: %ld", rc);
    }

    return env->NewStringUTF(buf);
}

jstring nativeKernelPatchModuleInfo(JNIEnv *env, jobject /* this */, jstring super_key_jstr, jstring module_name_jstr)
{
    ensureSuperKeyNonNull(super_key_jstr);

    const auto super_key = JUTFString(env, super_key_jstr);
    const auto module_name = JUTFString(env, module_name_jstr);
    char buf[1024] = { '\0' };
    long rc = sc_kpm_info(super_key.get(), module_name.get(), buf, sizeof(buf));
    if (rc < 0) [[unlikely]] {
        LOGE("nativeKernelPatchModuleInfo error: %ld", rc);
    }

    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /*reserved*/)
{
    LOGI("Enter OnLoad");

    JNIEnv *env{};
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) [[unlikely]] {
        LOGE("Get JNIEnv error!");
        return JNI_FALSE;
    }

    auto clazz = JNI_FindClass(env, "just/yervant/kpmmanager/Natives");
    if (clazz.get() == nullptr) [[unlikely]] {
        LOGE("Failed to find Natives class");
        return JNI_FALSE;
    }

    const static JNINativeMethod gMethods[] = {
        { "nativeReady", "(Ljava/lang/String;)Z", reinterpret_cast<void *>(&nativeReady) },
        { "nativeKernelPatchVersion", "(Ljava/lang/String;)J", reinterpret_cast<void *>(&nativeKernelPatchVersion) },
        { "nativeKernelPatchBuildTime", "(Ljava/lang/String;)Ljava/lang/String;",
          reinterpret_cast<void *>(&nativeKernelPatchBuildTime) },
        { "nativeLoadKernelPatchModule", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J",
          reinterpret_cast<void *>(&nativeLoadKernelPatchModule) },
        { "nativeControlKernelPatchModule",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljust/yervant/kpmmanager/Natives$KPMCtlRes;",
          reinterpret_cast<void *>(&nativeControlKernelPatchModule) },
        { "nativeUnloadKernelPatchModule", "(Ljava/lang/String;Ljava/lang/String;)J",
          reinterpret_cast<void *>(&nativeUnloadKernelPatchModule) },
        { "nativeKernelPatchModuleNum", "(Ljava/lang/String;)J",
          reinterpret_cast<void *>(&nativeKernelPatchModuleNum) },
        { "nativeKernelPatchModuleList", "(Ljava/lang/String;)Ljava/lang/String;",
          reinterpret_cast<void *>(&nativeKernelPatchModuleList) },
        { "nativeKernelPatchModuleInfo", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          reinterpret_cast<void *>(&nativeKernelPatchModuleInfo) },
    };

    if (JNI_RegisterNatives(env, clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0) [[unlikely]] {
        LOGE("Failed to register native methods");
        return JNI_FALSE;
    }

    LOGI("JNI_OnLoad Done!");
    return JNI_VERSION_1_6;
}
