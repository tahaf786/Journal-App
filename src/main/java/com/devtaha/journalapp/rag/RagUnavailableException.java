package com.devtaha.journalapp.rag;

/** The model server or the vector store could not be reached, or answered with an error. */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RagUnavailableException(String message) {
        super(message);
    }
}
