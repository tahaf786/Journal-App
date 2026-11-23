package com.devtaha.journalapp.user;

import java.time.Instant;
import java.util.Set;

public record UserResponse(String id, String username, String displayName, Set<Role> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoles(),
                user.getCreatedAt());
    }
}
