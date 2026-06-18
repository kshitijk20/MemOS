package com.memos.web.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GitIngestRequest(

        @NotBlank
        @Pattern(
                regexp = "^(https?://|git@|git://)\\S+",
                message = "must be a valid Git repository URL (https://, git@, or git://)")
        String repoUrl,

        @NotBlank @Size(max = 500)
        String title,

        String branch) {
}
