package com.devtaha.journalapp.rag;

/** Turns text into a vector. Implementations throw {@link RagUnavailableException} when the model is unreachable. */
public interface EmbeddingClient {

    float[] embed(String text);
}
