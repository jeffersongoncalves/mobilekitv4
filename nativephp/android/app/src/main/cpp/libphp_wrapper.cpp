#include "android_compat.h"
#include <android/log.h>
#include <dlfcn.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <php.h>
#include <php_main.h>
#include <dlfcn.h>
#include <android/log.h>
#include <link.h>


#define VIS_LOG_TAG "JNI-Symbols"
#define VIS_LOG(...) __android_log_print(ANDROID_LOG_INFO, VIS_LOG_TAG, __VA_ARGS__)
#define LOG_TAG "PHP-Wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int print_phdr(struct dl_phdr_info *info, size_t size, void *data) {
    if (info->dlpi_name && strstr(info->dlpi_name, "php_wrapper")) {
        __android_log_print(ANDROID_LOG_INFO, "LINKER", "📦 LOADED: %s", info->dlpi_name);
    }
    return 0;
}

__attribute__((constructor))
void list_loaded_libraries() {
    dl_iterate_phdr(print_phdr, NULL);
}

__attribute__((constructor))
static void expose_symbols_to_php() {
    void* handle = dlopen("libphp_wrapper.so", RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        __android_log_print(ANDROID_LOG_ERROR, "SymbolExport", "❌ dlopen(libphp_wrapper.so) failed: %s", dlerror());
    } else {
        __android_log_print(ANDROID_LOG_INFO, "SymbolExport", "✅ Re-opened libphp_wrapper.so with RTLD_GLOBAL");
    }
}


// Forward declare PHP functions we need to wrap
extern "C" {
typedef int (*php_embed_init_func)(int, char**);
typedef void (*php_embed_shutdown_func)(void);
typedef int (*php_request_startup_func)(void);
typedef void (*php_request_shutdown_func)(void*);

static void* php_handle = nullptr;
static php_embed_init_func original_php_embed_init = nullptr;
static php_embed_shutdown_func original_php_embed_shutdown = nullptr;
static php_request_startup_func original_php_request_startup = nullptr;
static php_request_shutdown_func original_php_request_shutdown = nullptr;

static void __attribute__((constructor)) init_wrapper() {
    LOGI("Initializing PHP wrapper");

    // Load compat library first
    void* compat_handle = dlopen("libcompat.so", RTLD_NOW | RTLD_GLOBAL);
    if (!compat_handle) {
        LOGE("Failed to load libcompat.so: %s", dlerror());
        return;
    }

    // Load PHP library
    php_handle = dlopen("libphp.so", RTLD_NOW | RTLD_GLOBAL);
    if (!php_handle) {
        LOGE("Failed to load libphp.so: %s", dlerror());
        dlclose(compat_handle);
        return;
    }

    // Get function pointers for required PHP functions
    original_php_embed_init = (php_embed_init_func)dlsym(php_handle, "php_embed_init");
    original_php_embed_shutdown = (php_embed_shutdown_func)dlsym(php_handle, "php_embed_shutdown");
    original_php_request_startup = (php_request_startup_func)dlsym(php_handle, "php_request_startup");
    original_php_request_shutdown = (php_request_shutdown_func)dlsym(php_handle, "php_request_shutdown");

    if (!original_php_embed_init || !original_php_embed_shutdown ||
        !original_php_request_startup || !original_php_request_shutdown) {
        LOGE("Failed to find PHP symbols: %s", dlerror());
        dlclose(php_handle);
        dlclose(compat_handle);
        return;
    }

    LOGI("PHP wrapper initialized successfully");
}

// Cleanup wrapper - called when library is unloaded
__attribute__((destructor))
static void cleanup_wrapper() {
    if (php_handle) {
        dlclose(php_handle);
        php_handle = nullptr;
    }
}

// Wrap PHP's initialization function
int php_embed_init(int argc, char** argv) {
    if (!original_php_embed_init) {
        LOGE("PHP init function not found");
        return -1;
    }
    return original_php_embed_init(argc, argv);
}

// Wrap PHP's shutdown function
void php_embed_shutdown(void) {
    if (original_php_embed_shutdown) {
        original_php_embed_shutdown();
    }
}

void php_request_shutdown(void* dummy) {
    if (original_php_request_shutdown) {
        original_php_request_shutdown(dummy);
    }
}

}