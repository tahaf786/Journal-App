package com.devtaha.journalapp.exception;

/** Bad credentials or an invalid, expired or wrong-type token. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
