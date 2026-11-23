package com.devtaha.journalapp.rag;

/** Sends one system and one user message to a chat model and returns the reply text. */
public interface ChatClient {

    String chat(String systemPrompt, String userPrompt);
}
