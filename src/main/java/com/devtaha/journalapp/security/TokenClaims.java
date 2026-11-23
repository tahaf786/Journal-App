package com.devtaha.journalapp.security;

import com.devtaha.journalapp.user.Role;

import java.util.Set;

public record TokenClaims(String userId, String username, Set<Role> roles, TokenType type) {
}
