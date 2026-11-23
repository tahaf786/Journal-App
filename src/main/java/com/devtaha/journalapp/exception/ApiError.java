package com.devtaha.journalapp.exception;

import java.time.Instant;
import java.util.List;

/** The single error body returned by every failing request. */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }
}
