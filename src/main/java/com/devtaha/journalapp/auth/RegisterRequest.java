package com.devtaha.journalapp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_.-]+",
                message = "may contain letters, digits, '_', '.' and '-' only") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 80) String displayName) {
}
