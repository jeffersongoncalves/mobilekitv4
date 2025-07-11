#ifndef ANDROID_COMPAT_H
#define ANDROID_COMPAT_H

#ifdef __ANDROID__
#include <android/log.h>
#include <jni.h>
#define PHP_ANDROID_DEBUG(fmt, ...) \
    __android_log_print(ANDROID_LOG_DEBUG, "PHP", fmt, ##__VA_ARGS__)
#endif

#endif
