package com.devtaha.journalapp.entry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** The date is optional and defaults to the time of creation. */
public record EntryRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String content,
        Instant date) {
}
