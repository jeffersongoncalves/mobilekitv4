    #ifndef PHP_DNS_ANDROID_H
    #define PHP_DNS_ANDROID_H

    #ifdef __ANDROID__
    #include <resolv.h>
    #include <arpa/nameser.h>
    #include <netdb.h>

    typedef struct {
        struct __res_state state;
        int res_h_errno;
    } dns_handle;

    #define php_dns_search(handle, dname, class, type, answer, anslen) \
        res_query(dname, class, type, answer, anslen)

    #define php_dns_free_handle(handle) \
        res_close(&(handle->state))

    #define php_dns_errno(handle) (handle->res_h_errno)

    static inline int res_ninit(dns_handle *handle) {
        return res_init();
    }

    static inline int res_nsearch(dns_handle *handle, const char *dname, int class, int type, unsigned char *answer, int anslen) {
        return res_query(dname, class, type, answer, anslen);
    }

    static inline void res_nclose(dns_handle *handle) {
        res_close(&(handle->state));
    }

    #define dn_skipname __dn_skipname

    #endif /* __ANDROID__ */
    #endif /* PHP_DNS_ANDROID_H */
