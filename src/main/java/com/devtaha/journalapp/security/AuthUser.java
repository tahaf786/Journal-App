package com.devtaha.journalapp.security;

/** The authenticated caller, taken from the verified access token. */
public record AuthUser(String id, String username) {
}
