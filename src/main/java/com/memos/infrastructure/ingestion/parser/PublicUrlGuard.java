package com.memos.infrastructure.ingestion.parser;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.memos.domain.exception.IngestionException;

/**
 * Blocks server-side request forgery: URL and Git ingestion fetch whatever address a user gives,
 * and the fetched text becomes readable through chat. Without this check a user could make the
 * server read internal services, e.g. {@code http://localhost:8080/actuator} or the cloud
 * metadata endpoint {@code 169.254.169.254}.
 *
 * <p>The host is resolved and every address must be public. A DNS answer that changes between
 * this check and the actual fetch (DNS rebinding) is not covered.
 */
public final class PublicUrlGuard {

    private PublicUrlGuard() {
    }

    /** Throws {@link IngestionException} unless {@code url} is http(s) and its host resolves only to public addresses. */
    public static void requirePublicHttpUrl(String url) {
        requirePublicUrl(url, "http", "https");
    }

    /** Like {@link #requirePublicHttpUrl} but only https, for Git repositories. */
    public static void requirePublicHttpsUrl(String url) {
        requirePublicUrl(url, "https");
    }

    private static void requirePublicUrl(String url, String... allowedSchemes) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.strip());
        } catch (URISyntaxException e) {
            throw new IngestionException("'" + url + "' isn't a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!List.of(allowedSchemes).contains(scheme)) {
            throw new IngestionException("Only " + String.join(" and ", Arrays.stream(allowedSchemes)
                    .map(s -> s + "://").toList()) + " links can be added.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IngestionException("'" + url + "' has no host name.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IngestionException("Could not resolve host for " + url + ". Check the URL and retry.");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IngestionException(
                        "'" + host + "' points to a private or local network address, which can't be fetched.");
            }
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0                                        // 0.0.0.0/8 "this network"
                    && !(first == 100 && (second & 0xc0) == 64)      // 100.64.0.0/10 carrier-grade NAT
                    && !(first == 192 && second == 0 && b[2] == 0)   // 192.0.0.0/24 protocol assignments
                    && !(first == 198 && (second & 0xfe) == 18)      // 198.18.0.0/15 benchmarking
                    && first < 240;                                  // 240.0.0.0/4 reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc;                            // fc00::/7 unique local
        }
        return true;
    }
}
