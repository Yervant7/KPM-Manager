/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifndef _KPU_SUPERCALL_H_
#define _KPU_SUPERCALL_H_

#include <unistd.h>
#include <sys/syscall.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>

#include "uapi/scdefs.h"
#include "version"

/// KernelPatch version is greater than or equal to 0x0a05
static inline long ver_and_cmd(const char *key, long cmd)
{
    uint32_t version_code = (MAJOR << 16) + (MINOR << 8) + PATCH;
    return ((long)version_code << 32) | (0x1158 << 16) | (cmd & 0xFFFF);
}

/**
 * @brief If KernelPatch installed, @see SUPERCALL_HELLO_ECHO will echoed.
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return long 
 */
static inline long sc_hello(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_HELLO));
    return ret;
}

/**
 * @brief Is KernelPatch installed?
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return true 
 * @return false 
 */
static inline bool sc_ready(const char *key)
{
    return sc_hello(key) == SUPERCALL_HELLO_MAGIC;
}

/**
 * @brief Print messages by printk in the kernel
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param msg 
 * @return long 
 */
static inline long sc_klog(const char *key, const char *msg)
{
    if (!key || !key[0]) return -EINVAL;
    if (!msg || strlen(msg) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KLOG), msg);
    return ret;
}

/**
 * @brief Print build kernel time
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param buildtime 
 * @param timestamp
 * @return long 
 */
static inline long sc_get_build_time(const char *key, const char *buildtime, size_t len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!buildtime) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_BUILD_TIME), buildtime, len);
    return ret;
}

/**
 * @brief KernelPatch version number
 * 
 * @param key 
 * @return uint32_t 
 */
static inline uint32_t sc_kp_ver(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KERNELPATCH_VER));
    return (uint32_t)ret;
}

/**
 * @brief Kernel version number
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return uint32_t 
 */
static inline uint32_t sc_k_ver(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KERNEL_VER));
    return (uint32_t)ret;
}

/**
 * @brief Load module
 * 
 * @param key : superkey
 * @param path 
 * @param args 
 * @param reserved 
 * @return long : 0 if succeed
 */
static inline long sc_kpm_load(const char *key, const char *path, const char *args, void *reserved)
{
    if (!key || !key[0]) return -EINVAL;
    if (!path || strlen(path) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_LOAD), path, args, reserved);
    return ret;
}

/**
 * @brief Control module with arguments 
 * 
 * @param key : superkey
 * @param name : module name
 * @param ctl_args : control argument
 * @param out_msg : output message buffer
 * @param outlen : buffer length of out_msg
 * @return long : 0 if succeed
 */
static inline long sc_kpm_control(const char *key, const char *name, const char *ctl_args, char *out_msg, long outlen)
{
    if (!key || !key[0]) return -EINVAL;
    if (!name || strlen(name) <= 0) return -EINVAL;
    if (!ctl_args || strlen(ctl_args) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_CONTROL), name, ctl_args, out_msg, outlen);
    return ret;
}

/**
 * @brief Unload module
 * 
 * @param key : superkey
 * @param name : module name
 * @param reserved 
 * @return long : 0 if succeed
 */
static inline long sc_kpm_unload(const char *key, const char *name, void *reserved)
{
    if (!key || !key[0]) return -EINVAL;
    if (!name || strlen(name) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_UNLOAD), name, reserved);
    return ret;
}

/**
 * @brief Current loaded module numbers
 * 
 * @param key : superkey
 * @return long
 */
static inline long sc_kpm_nums(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_NUMS));
    return ret;
}

/**
 * @brief List names of current loaded modules, splited with '\n'
 * 
 * @param key : superkey
 * @param names_buf : output buffer
 * @param buf_len : the length of names_buf
 * @return long : the length of result string if succeed, negative if failed
 */
static inline long sc_kpm_list(const char *key, char *names_buf, int buf_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!names_buf || buf_len <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_LIST), names_buf, buf_len);
    return ret;
}

/**
 * @brief Get module information. 
 * 
 * @param key : superkey
 * @param name : module name
 * @param buf : 
 * @param buf_len : 
 * @return long : The length of result string if succeed, negative if failed
 */
static inline long sc_kpm_info(const char *key, const char *name, char *buf, int buf_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!buf || buf_len <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KPM_INFO), name, buf, buf_len);
    return ret;
}

/**
 * @brief Get current superkey
 * 
 * @param key : superkey
 * @param out_key 
 * @param outlen 
 * @return long : 0 if succeed
 */
static inline long sc_skey_get(const char *key, char *out_key, int outlen)
{
    if (!key || !key[0]) return -EINVAL;
    if (outlen < SUPERCALL_KEY_MAX_LEN) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_SKEY_GET), out_key, outlen);
    return ret;
}

/**
 * @brief Reset current superkey
 * 
 * @param key : superkey
 * @param new_key 
 * @return long : 0 if succeed
 */
static inline long sc_skey_set(const char *key, const char *new_key)
{
    if (!key || !key[0]) return -EINVAL;
    if (!new_key || !new_key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_SKEY_SET), new_key);
    return ret;
}

/**
 * @brief Whether to enable hash verification for root superkey.
 * 
 * @param key : superkey
 * @param enable 
 * @return long 
 */
static inline long sc_skey_root_enable(const char *key, bool enable)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_SKEY_ROOT_ENABLE), (long)enable);
    return ret;
}

static inline long sc_bootlog(const char *key)
{
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_BOOTLOG));
    return ret;
}

static inline long sc_panic(const char *key)
{
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_PANIC));
    return ret;
}

static inline long __sc_test(const char *key, long a1, long a2, long a3)
{
    long ret = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_TEST), a1, a2, a3);
    return ret;
}

#endif