#ifndef PHP_DNS_H
#define PHP_DNS_H

#include <netdb.h>
#include <resolv.h>

#define DNS_A      1
#define DNS_NS     2
#define DNS_CNAME  5
#define DNS_SOA    6
#define DNS_PTR    12
#define DNS_HINFO  13
#define DNS_MX     15
#define DNS_TXT    16
#define DNS_AAAA   28
#define DNS_SRV    33
#define DNS_NAPTR  35
#define DNS_A6     38
#define DNS_ANY    255

PHP_FUNCTION(dns_get_record);

#endif /* PHP_DNS_H */
