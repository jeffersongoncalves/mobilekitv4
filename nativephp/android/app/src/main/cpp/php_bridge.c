#include <jni.h>
#include <android/log.h>
#include "php_embed.h"
#include "PHP.h"
#include <zend_exceptions.h>

// Define Android logging macros first
#define LOG_TAG "PHP-Native"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

JavaVM *g_jvm = NULL;
jobject g_bridge_instance = NULL;

// Global state
static int php_initialized = 0;
static jobject g_callback_obj = NULL;
static jmethodID g_callback_method = NULL;
static char *g_collected_output = NULL;
static size_t g_collected_length = 0;
static size_t g_collected_capacity = 0;

#define BUFFER_CHUNK_SIZE (256 * 1024)  // 256KB increments
#define MAX_BUFFER_SIZE (16 * 1024 * 1024)  // 16MB max buffer

static void (*jni_output_callback_ptr)(const char *) = NULL;

void clear_collected_output() {
    if (g_collected_output) {
        free(g_collected_output);
        g_collected_output = NULL;
    }

    g_collected_capacity = BUFFER_CHUNK_SIZE;
    g_collected_length = 0;
    g_collected_output = (char *) malloc(g_collected_capacity);
    if (g_collected_output) {
        g_collected_output[0] = '\0';
    }
}


void pipe_php_output(const char *str) {

//    LOGI("PIPE: Output received: %s", str);

    // Safety check
    if (!g_collected_output) {
        clear_collected_output();
        return;  // Failed to allocate
    }

    size_t length = strlen(str);

    // Check if we need more space
    if (g_collected_length + length + 1 > g_collected_capacity) {
        // Calculate new size in chunks
        size_t needed_capacity = g_collected_capacity;
        while (needed_capacity < g_collected_length + length + 1) {
            needed_capacity += BUFFER_CHUNK_SIZE;
        }

        // Enforce maximum size limit
        if (needed_capacity > MAX_BUFFER_SIZE) {
            LOGE("Output buffer exceeded maximum size of %d MB", MAX_BUFFER_SIZE / (1024 * 1024));
            // Just return and drop output beyond this point
            return;
        }

        // Reallocate with the new size
        char *new_buffer = (char *) realloc(g_collected_output, needed_capacity);
        if (new_buffer) {
            g_collected_output = new_buffer;
            g_collected_capacity = needed_capacity;
        } else {
            LOGE("Failed to reallocate output buffer to %zu bytes", needed_capacity);
            return;  // Failed to reallocate
        }
    }

    // Append the string
    strcpy(g_collected_output + g_collected_length, str);
    g_collected_length += length;
}

void cleanup_output_buffer() {
    if (g_collected_output) {
        g_collected_output[0] = '\0';
        g_collected_length = 0;
    }
}

size_t capture_php_output(const char *str, size_t str_length) {
    // Log the raw output coming from PHP
//    LOGI("PHP output captured: length=%zu", str_length);
    if (str_length > 0) {
        // Log a preview of the output (first 100 chars or so)
        char preview[10001] = {0};
        strncpy(preview, str, str_length > 10000 ? 10000 : str_length);
        preview[10000] = '\0'; // Ensure null termination
    } else {
        LOGI("Empty output received");
    }

    // Rest of your original code...
    char *buffer = malloc(str_length + 1);
    if (buffer) {
        memcpy(buffer, str, str_length);
        buffer[str_length] = '\0';

        pipe_php_output(buffer);
        free(buffer);
    }

    return str_length;
}

void override_embed_module_output(void (*callback)(const char *)) {
    jni_output_callback_ptr = callback;
    php_embed_module.ub_write = capture_php_output;
}

void jni_output_callback(const char *output) {
//    LOGI("PHP Output Debug - Callback called with: %s", output);

    JNIEnv *env;
    if ((*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment");
        return;
    }

    if (g_callback_obj && g_callback_method) {
        LOGI("WE MADE IT HERE");
        jstring joutput = (*env)->NewStringUTF(env, output);
        (*env)->CallVoidMethod(env, g_callback_obj, g_callback_method, joutput);
        (*env)->DeleteLocalRef(env, joutput);
    }

}

int android_header_handler(sapi_header_struct *sapi_header, sapi_header_op_enum op, sapi_headers_struct *sapi_headers) {
    LOGI("📤 SAPI header: %s", sapi_header->header);
    // You can collect headers here if you want
    return 0;
}

char* run_php_script_once(const char* scriptPath, const char* method, const char* uri, const char* postData) {
    clear_collected_output();

    // 🔁 Reset in case PHP was already initialized
    if (php_initialized) {
        php_embed_shutdown();
        php_initialized = 0;
    }

    // 🧠 Get session path from environment (set by Kotlin)
    const char* session_path = getenv("SESSION_SAVE_PATH");
    if (!session_path) session_path = "/tmp"; // fallback

    // ✅ Build ini entries per request
    php_embed_module.ub_write = capture_php_output;
    php_embed_module.phpinfo_as_text = 1;
    php_embed_module.php_ini_ignore = 0;
    php_embed_module.ini_entries = "output_buffering=4096\n"
                                   "implicit_flush=0\n"
                                   "display_errors=1\n"
                                   "error_reporting=E_ALL\n";

    php_embed_module.header_handler = android_header_handler;

    // ✅ Start PHP
    if (php_embed_init(0, NULL) != SUCCESS) {
        return strdup("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\n\r\nPHP init failed.");
    }
    sapi_module.header_handler = android_header_handler;
    php_initialized = 1;

    // ✅ Set Laravel-relevant env vars
    setenv("REQUEST_URI", uri, 1);
    setenv("REQUEST_METHOD", method, 1);
    setenv("SCRIPT_FILENAME", scriptPath, 1);
    setenv("PHP_SELF", "/native.php", 1);
    setenv("HTTP_HOST", "127.0.0.1", 1);
    setenv("APP_URL", "http://127.0.0.1", 1);
    setenv("ASSET_URL", "http://127.0.0.1/_assets/", 1);
    setenv("NATIVEPHP_RUNNING", "true", 1);

    // ✅ Set QUERY_STRING and defer parsing
    const char* query_string = "";
    const char* query_start = strchr(uri, '?');
    if (query_start && strlen(query_start + 1) > 0) {
        query_string = query_start + 1;
        setenv("QUERY_STRING", query_string, 1);
        LOGI("✅ Set QUERY_STRING: %s", query_string);
    } else {
        unsetenv("QUERY_STRING");
        LOGI("⚠️ No QUERY_STRING found in URI");
    }

    // ✅ Activate Zend and parse query data AFTER engine is live
    zend_first_try {
                zend_activate_modules();

                if (strlen(query_string) > 0) {
                    zend_string *query = zend_string_init(query_string, strlen(query_string), 0);
                    sapi_module.treat_data(PARSE_GET, query->val, NULL);
                    zend_string_free(query);
                    LOGI("✅ Parsed query string into $_GET");
                }

                // ✅ Set up POST data (if needed)
                initialize_php_with_request(postData ?: "", method, uri);

                // ✅ Execute the PHP script
                zend_file_handle fileHandle;
                zend_stream_init_filename(&fileHandle, scriptPath);
                php_execute_script(&fileHandle);

                LOGI("✅ PHP script finished executing");

                if (strlen(query_string) > 0) {
                    zend_string *query2 = zend_string_init(query_string, strlen(query_string), 0);
                    sapi_module.treat_data(PARSE_GET, query2->val, NULL);
                    zend_string_free(query2);
                    LOGI("✅ Re-parsed query string after php_execute_script()");
                }

            } zend_end_try();

    // ✅ Copy output before shutdown
    char *response = g_collected_output ? strdup(g_collected_output) : strdup("");

    php_embed_shutdown();
    php_initialized = 0;

    return response;
}

JNIEXPORT void JNICALL native_initialize(JNIEnv *env, jobject thiz) {
    if (php_initialized) {
        LOGI("PHP already initialized");
        return;
    }

    LOGI("Initializing PHP");

    if (g_bridge_instance) {
        LOGI("Deleting existing bridge instance");
        (*env)->DeleteGlobalRef(env, g_bridge_instance);
    }
    g_bridge_instance = (*env)->NewGlobalRef(env, thiz);
    LOGI("Set g_bridge_instance to %p", g_bridge_instance);

    // Configure the embed SAPI
    php_embed_module.ub_write = capture_php_output;
    php_embed_module.phpinfo_as_text = 1;
    php_embed_module.php_ini_ignore = 0;

    // Initialize PHP
    if (php_embed_init(0, NULL) == SUCCESS) {
        php_initialized = 1;
        sapi_module.header_handler = php_embed_module.header_handler;
        LOGI("PHP initialized successfully");
    } else {
        LOGI("PHP initialization failed");
    }
}


JNIEXPORT jint JNICALL native_set_env(JNIEnv *env, jobject thiz,
                                                            jstring name, jstring value,
                                                            jint overwrite) {

    const char *nameStr = (*env)->GetStringUTFChars(env, name, NULL);
    const char *valueStr = (*env)->GetStringUTFChars(env, value, NULL);

    int result = setenv(nameStr, valueStr, overwrite);

    (*env)->ReleaseStringUTFChars(env, name, nameStr);
    (*env)->ReleaseStringUTFChars(env, value, valueStr);

    return result;
}

JNIEXPORT void JNICALL native_set_request_info(JNIEnv *env, jobject thiz,
                                                     jstring method, jstring uri,
                                                     jstring post_data) {

    const char *methodStr = (*env)->GetStringUTFChars(env, method, NULL);
    const char *uriStr = (*env)->GetStringUTFChars(env, uri, NULL);
    const char *postStr = post_data ? (*env)->GetStringUTFChars(env, post_data, NULL) : "";

    initialize_php_with_request(postStr, methodStr, uriStr);

    (*env)->ReleaseStringUTFChars(env, method, methodStr);
    (*env)->ReleaseStringUTFChars(env, uri, uriStr);
    if (post_data) {
        (*env)->ReleaseStringUTFChars(env, post_data, postStr);
    }
}

JNIEXPORT jstring JNICALL native_run_artisan_command(JNIEnv *env, jobject thiz, jstring jcommand) {
    const char *command = (*env)->GetStringUTFChars(env, jcommand, NULL);
    LOGI("🛠️ runArtisanCommand: %s", command);

    clear_collected_output();
    php_embed_module.ub_write = capture_php_output;
    php_embed_module.phpinfo_as_text = 1;
    php_embed_module.php_ini_ignore = 0;
    php_embed_module.ini_entries = "display_errors=1\nimplicit_flush=1\noutput_buffering=0\n";

    // Get Laravel path
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID method = (*env)->GetMethodID(env, cls, "getLaravelPublicPath", "()Ljava/lang/String;");
    jstring jLaravelPath = (jstring)(*env)->CallObjectMethod(env, thiz, method);
    const char *cLaravelPath = (*env)->GetStringUTFChars(env, jLaravelPath, NULL);

    native_initialize(env, thiz);

    char artisanPath[1024];
    snprintf(artisanPath, sizeof(artisanPath), "%s/../artisan.php", cLaravelPath);
    char basePath[1024];
    snprintf(basePath, sizeof(basePath), "%s/..", cLaravelPath);
    chdir(basePath);
    LOGI("✅ Changed CWD to Laravel base: %s", basePath);

    // Tokenize command
    char *argv[128];
    int argc = 0;
    argv[argc++] = "php";

    char *commandCopy = strdup(command);
    char *token = strtok(commandCopy, " ");
    while (token && argc < 127) {
        argv[argc++] = token;
        token = strtok(NULL, " ");
    }
    argv[argc] = NULL;

    if (php_initialized) {
        php_embed_shutdown();
        php_initialized = 0;
    }

    setenv("APP_RUNNING_IN_CONSOLE", "true", 1);
    setenv("PHP_SELF", "artisan.php", 1);
    setenv("APP_ENV", "local", 1);
    setenv("APP_DEBUG", "true", 1);

    if (php_embed_init(argc, argv) == SUCCESS) {
        php_initialized = 1;

        // Force STDOUT/STDERR through php://output so Symfony StreamOutput works
        zend_eval_string(
                "if (!defined('STDOUT')) define('STDOUT', fopen('php://output', 'w')); "
                "if (!defined('STDERR')) define('STDERR', fopen('php://output', 'w'));",
                NULL, "patch_stdio"
        );

        zend_file_handle file_handle;
        zend_stream_init_filename(&file_handle, artisanPath);
        php_execute_script(&file_handle);
        php_embed_shutdown();
        php_initialized = 0;
    } else {
        LOGE("❌ Failed to initialize PHP runtime");
    }

    (*env)->ReleaseStringUTFChars(env, jcommand, command);
    (*env)->ReleaseStringUTFChars(env, jLaravelPath, cLaravelPath);
    (*env)->DeleteLocalRef(env, jLaravelPath);
    free(commandCopy);

    return (*env)->NewStringUTF(env, g_collected_output ? g_collected_output : "");
}

JNIEXPORT jstring JNICALL native_get_laravel_root_path(JNIEnv *env, jobject thiz) {
    // Get context from the PHPBridge instance
    jclass bridgeClass = (*env)->GetObjectClass(env, thiz);
    jfieldID contextFieldId = (*env)->GetFieldID(env, bridgeClass, "context", "Landroid/content/Context;");
    jobject context = (*env)->GetObjectField(env, thiz, contextFieldId);

    // Call getDir method on the context
    jclass contextClass = (*env)->GetObjectClass(env, context);
    jmethodID getDirMethod = (*env)->GetMethodID(env, contextClass, "getDir", "(Ljava/lang/String;I)Ljava/io/File;");
    jstring dirName = (*env)->NewStringUTF(env, "storage");
    jint mode = 0; // MODE_PRIVATE
    jobject storageDir = (*env)->CallObjectMethod(env, context, getDirMethod, dirName, mode);

    // Get the absolute path from the file object
    jclass fileClass = (*env)->GetObjectClass(env, storageDir);
    jmethodID getAbsolutePathMethod = (*env)->GetMethodID(env, fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring storagePath = (jstring) (*env)->CallObjectMethod(env, storageDir, getAbsolutePathMethod);

    // Convert to C string for concatenation
    const char *cStoragePath = (*env)->GetStringUTFChars(env, storagePath, NULL);

    // Concatenate with "/laravel/public"
    char fullPath[1024];
    sprintf(fullPath, "%s/laravel", cStoragePath);

    // Release resources
    (*env)->ReleaseStringUTFChars(env, storagePath, cStoragePath);
    (*env)->DeleteLocalRef(env, dirName);
    (*env)->DeleteLocalRef(env, storageDir);
    (*env)->DeleteLocalRef(env, storagePath);

    // Return the final path
    return (*env)->NewStringUTF(env, fullPath);
}

JNIEXPORT jstring JNICALL native_handle_request_once(
        JNIEnv *env, jobject thiz,
        jstring jMethod, jstring jUri, jstring jPostData, jstring jScriptPath) {

    const char *method = (*env)->GetStringUTFChars(env, jMethod, NULL);
    const char *uri = (*env)->GetStringUTFChars(env, jUri, NULL);
    const char *post = jPostData ? (*env)->GetStringUTFChars(env, jPostData, NULL) : "";
    const char *path = (*env)->GetStringUTFChars(env, jScriptPath, NULL);

    char *output = run_php_script_once(path, method, uri, post);

    jstring result = (*env)->NewStringUTF(env, output ? output : "");

    // Clean up
    free(output);
    (*env)->ReleaseStringUTFChars(env, jMethod, method);
    (*env)->ReleaseStringUTFChars(env, jUri, uri);
    (*env)->ReleaseStringUTFChars(env, jScriptPath, path);
    if (jPostData) (*env)->ReleaseStringUTFChars(env, jPostData, post);

    return result;
}

JNIEXPORT jstring JNICALL native_get_laravel_public_path(JNIEnv *env, jobject thiz) {
    // Get context from the PHPBridge instance
    jclass bridgeClass = (*env)->GetObjectClass(env, thiz);
    jfieldID contextFieldId = (*env)->GetFieldID(env, bridgeClass, "context", "Landroid/content/Context;");
    jobject context = (*env)->GetObjectField(env, thiz, contextFieldId);

    // Call getDir method on the context
    jclass contextClass = (*env)->GetObjectClass(env, context);
    jmethodID getDirMethod = (*env)->GetMethodID(env, contextClass, "getDir", "(Ljava/lang/String;I)Ljava/io/File;");
    jstring dirName = (*env)->NewStringUTF(env, "storage");
    jint mode = 0; // MODE_PRIVATE
    jobject storageDir = (*env)->CallObjectMethod(env, context, getDirMethod, dirName, mode);

    // Get the absolute path from the file object
    jclass fileClass = (*env)->GetObjectClass(env, storageDir);
    jmethodID getAbsolutePathMethod = (*env)->GetMethodID(env, fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring storagePath = (jstring) (*env)->CallObjectMethod(env, storageDir, getAbsolutePathMethod);

    // Convert to C string for concatenation
    const char *cStoragePath = (*env)->GetStringUTFChars(env, storagePath, NULL);
    setenv("APP_RUNNING_IN_CONSOLE", "false", 1);

    // Concatenate with "/laravel/public"
    char fullPath[1024];
    sprintf(fullPath, "%s/laravel/public", cStoragePath);

    // Release resources
    (*env)->ReleaseStringUTFChars(env, storagePath, cStoragePath);
    (*env)->DeleteLocalRef(env, dirName);
    (*env)->DeleteLocalRef(env, storageDir);
    (*env)->DeleteLocalRef(env, storagePath);

    // Return the final path
    return (*env)->NewStringUTF(env, fullPath);
}

JNIEXPORT void JNICALL native_shutdown(JNIEnv *env, jobject thiz) {
    if (php_initialized) {
        php_embed_shutdown();
        php_initialized = 0;

        if (g_callback_obj) {
            (*env)->DeleteGlobalRef(env, g_callback_obj);
            g_callback_obj = NULL;
        }
        g_callback_method = NULL;

        if (g_bridge_instance) {
            (*env)->DeleteGlobalRef(env, g_bridge_instance);
            g_bridge_instance = NULL;
        }

        // Free the collected output buffer
        if (g_collected_output) {
            free(g_collected_output);
            g_collected_output = NULL;
            g_collected_length = 0;
            g_collected_capacity = 0;
        }
    }
}

JNIEXPORT jstring JNICALL native_execute_script(JNIEnv *env, jobject thiz, jstring filename) {
    const char *phpFilePath = (*env)->GetStringUTFChars(env, filename, NULL);

    zend_file_handle file_handle;
    zend_stream_init_filename(&file_handle, phpFilePath);

    php_execute_script(&file_handle);

    (*env)->ReleaseStringUTFChars(env, filename, phpFilePath);

    // Return collected output
    return (*env)->NewStringUTF(env, g_collected_output ? g_collected_output : "");
}

static JNINativeMethod gMethods[] = {
        // PHPBridge
        {"nativeExecuteScript", "(Ljava/lang/String;)Ljava/lang/String;", (void *) native_execute_script},
        {"initialize", "()V", (void *) native_initialize},
        {"shutdown", "()V", (void *) native_shutdown},
        {"setRequestInfo", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", (void *) native_set_request_info},
        {"runArtisanCommand", "(Ljava/lang/String;)Ljava/lang/String;", (void *) native_run_artisan_command},
        {"getLaravelPublicPath", "()Ljava/lang/String;", (void *) native_get_laravel_public_path},
        {"getLaravelRootPath", "()Ljava/lang/String;", (void *) native_get_laravel_root_path},

        // LaravelEnvironment
        {"nativeSetEnv", "(Ljava/lang/String;Ljava/lang/String;I)I", (void *) native_set_env},
        {"nativeHandleRequestOnce","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",(void *) native_handle_request_once}
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Register native methods for PHPBridge
    jclass phpBridgeClass = (*env)->FindClass(env, "com/example/androidphp/bridge/PHPBridge");
    if (phpBridgeClass == NULL) {
        return JNI_ERR;
    }

    if ((*env)->RegisterNatives(env, phpBridgeClass, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) != 0) {
        return JNI_ERR;
    }

    // Register native methods for LaravelEnvironment
    jclass laravelEnvClass = (*env)->FindClass(env, "com/example/androidphp/bridge/LaravelEnvironment");
    if (laravelEnvClass == NULL) {
        return JNI_ERR;
    }

    static JNINativeMethod envMethods[] = {
            {"nativeSetEnv", "(Ljava/lang/String;Ljava/lang/String;I)I", (void *) native_set_env}
    };

    if ((*env)->RegisterNatives(env, laravelEnvClass, envMethods, sizeof(envMethods) / sizeof(envMethods[0])) != 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
