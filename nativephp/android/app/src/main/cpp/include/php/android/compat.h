// compat.h
#ifndef COMPAT_H
#define COMPAT_H

#include <sys/types.h>

extern int getdtablesize(void);
extern ssize_t copy_file_range(int fd_in, off_t *off_in, int fd_out, off_t *off_out, size_t len, unsigned int flags);

#endif
