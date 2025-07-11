#ifndef PHP_BRIDGE_H
#define PHP_BRIDGE_H

#include "php_embed.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*phpOutputCallback)(const char* output);
void override_embed_module_output(phpOutputCallback callback);
void initialize_php_with_request(const char* post_data, const char* method, const char* uri);
size_t capture_php_output(const char *str, size_t str_length);

#ifdef __cplusplus
}
#endif

#endif // PHP_BRIDGE_H