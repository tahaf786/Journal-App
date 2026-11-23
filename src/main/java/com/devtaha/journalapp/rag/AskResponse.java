package com.devtaha.journalapp.rag;

import java.util.List;

public record AskResponse(String answer, List<Source> sources) {

    public record Source(String entryId, String title, double score) {
    }
}
