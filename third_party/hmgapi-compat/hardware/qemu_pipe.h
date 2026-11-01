/* HMG-API: adbd here never runs in the emulator; the qemu pipe is never there. */
#ifndef HMGAPI_QEMU_PIPE_H
#define HMGAPI_QEMU_PIPE_H
#include <errno.h>
static inline int qemu_pipe_open(const char *name) { (void)name; errno = ENOENT; return -1; }
#endif
