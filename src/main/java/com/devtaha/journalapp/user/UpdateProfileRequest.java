package com.devtaha.journalapp.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changing the password requires both the current and the new password. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String displayName,
        String currentPassword,
        @Size(min = 8, max = 72) String newPassword) {
}
