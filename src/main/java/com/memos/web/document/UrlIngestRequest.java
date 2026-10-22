package com.memos.web.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UrlIngestRequest(

        @NotBlank
        @Size(max = 500)
        String title,

        @NotBlank
        String url) {
}
