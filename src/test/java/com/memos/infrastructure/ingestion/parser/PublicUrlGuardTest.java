package com.memos.infrastructure.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.memos.domain.exception.IngestionException;

class PublicUrlGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "198.18.0.1", "255.255.255.255", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1"})
    void privateAndLocalAddressesAreNotPublic(String ip) throws Exception {
        assertThat(PublicUrlGuard.isPublic(InetAddress.getByName(ip))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.215.14", "140.82.112.3", "2606:4700::6810:84e5"})
    void publicAddressesArePublic(String ip) throws Exception {
        assertThat(PublicUrlGuard.isPublic(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/actuator/health", "http://127.0.0.1/", "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/", "http://10.0.0.5/admin"})
    void localUrlsAreRejected(String url) {
        assertThatThrownBy(() -> PublicUrlGuard.requirePublicHttpUrl(url))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("private or local");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/x", "gopher://127.0.0.1/"})
    void nonHttpSchemesAreRejected(String url) {
        assertThatThrownBy(() -> PublicUrlGuard.requirePublicHttpUrl(url))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("http://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://github.com/a/b.git", "git://github.com/a/b.git", "ssh://git@github.com/a/b.git"})
    void gitNeedsHttps(String url) {
        assertThatThrownBy(() -> PublicUrlGuard.requirePublicHttpsUrl(url))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("https://");
    }
}
