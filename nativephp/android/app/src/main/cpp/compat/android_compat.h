#ifndef ANDROID_COMPAT_H
#define ANDROID_COMPAT_H

#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>

#ifdef __cplusplus
extern "C" {
#endif

__attribute__((visibility("default")))
int getdtablesize(void);

__attribute__((visibility("default")))
ssize_t copy_file_range(int fd_in, off64_t *off_in,
                        int fd_out, off64_t *off_out,
                        size_t len, unsigned int flags);

#ifdef __cplusplus
}
#endif

#endif /* ANDROID_COMPAT_H */