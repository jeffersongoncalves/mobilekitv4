// android_compat.cpp
#include "android_compat.h"
#include <unistd.h>
#include <sys/resource.h>
#include <syscall.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "Compat", __VA_ARGS__)


__attribute__((visibility("default")))
extern "C" int getdtablesize(void) {
    __android_log_print(ANDROID_LOG_INFO, "Compat", "getdtablesize called");
    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim) == 0) {
        return rlim.rlim_cur;
    }
    return 1024;
}

__attribute__((visibility("default")))
extern "C" ssize_t copy_file_range(int fd_in, off64_t *off_in,
                                   int fd_out, off64_t *off_out,
                                   size_t len, unsigned int flags) {
    return syscall(__NR_copy_file_range, fd_in, off_in,
                   fd_out, off_out, len, flags);
}