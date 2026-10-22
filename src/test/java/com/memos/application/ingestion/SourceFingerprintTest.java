package com.memos.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceFingerprintTest {

    @Test
    void webPageFingerprintsIgnoreTrailingSlashAndHostCase() {
        String a = SourceFingerprint.forWebPage("https://Example.com/docs/");
        String b = SourceFingerprint.forWebPage("https://example.com/docs");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    void gitFingerprintsIncludeNormalizedBranch() {
        String main = SourceFingerprint.forGit("https://github.com/org/repo.git", "Main");
        String other = SourceFingerprint.forGit("https://github.com/org/repo", "main");
        String defaultBranch = SourceFingerprint.forGit("https://github.com/org/repo", null);

        assertThat(main).isEqualTo(other);
        assertThat(main).isNotEqualTo(defaultBranch);
    }

    @Test
    void fileFingerprintsMatchSameBytes() {
        byte[] content = "hello".getBytes();
        assertThat(SourceFingerprint.forFile(content)).isEqualTo(SourceFingerprint.forFile("hello".getBytes()));
        assertThat(SourceFingerprint.forFile(content)).isNotEqualTo(SourceFingerprint.forFile("hello!".getBytes()));
    }
}
