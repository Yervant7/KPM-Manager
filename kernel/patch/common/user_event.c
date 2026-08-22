/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2024 bmax121. All Rights Reserved.
 */

#include <user_event.h>
#include <userd.h>
#include <baselib.h>
#include <log.h>
#include <predata.h>
#ifdef ANDROID
extern int android_is_safe_mode;
#endif

int report_user_event(const char *event, const char *args)
{
    const char *safe_event = event ? event : "";
    const char *safe_args = args ? args : "";

    #ifdef ANDROID
    if (lib_strcmp(safe_event, "post-fs-data") == 0 && lib_strcmp(safe_args, "before") == 0) {
        if (android_is_safe_mode) {
            log_boot("post-fs-data: safe mode, skip ap KPM loading\n");
        } else {
            load_ap_kpm_modules();
        }
    }
    #endif
    logki("user report event: %s, args: %s\n", safe_event, safe_args);
    extra_event_init_args(safe_event, safe_args);
    return 0;
}
