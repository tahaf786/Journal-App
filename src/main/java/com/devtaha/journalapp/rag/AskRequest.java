package com.devtaha.journalapp.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code topK} is optional; when absent the configured default (4) is used. */
public record AskRequest(@NotBlank @Size(max = 1000) String question,
                         @Min(1) @Max(10) Integer topK) {
}
