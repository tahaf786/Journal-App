package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.entry.JournalEntry;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Builds the grounded prompt: the model may use only the numbered entries it is given. */
public final class PromptBuilder {

    static final String SYSTEM = "You answer questions about a person's private journal. "
            + "Use ONLY the journal entries provided by the user message. "
            + "If the entries do not contain the answer, reply exactly: "
            + "\"I could not find that in your journal.\" Do not use outside knowledge. "
            + "Answer briefly and mention the entry number you used.";

    private PromptBuilder() {
    }

    /** Text that is embedded for an entry: title and content together, cut to {@code maxChars}. */
    public static String entryText(String title, String content, int maxChars) {
        String text = title + "\n" + (content == null ? "" : content);
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    public static String userPrompt(String question, List<JournalEntry> entries, int maxChars) {
        StringBuilder sb = new StringBuilder("Journal entries:\n\n");
        for (int i = 0; i < entries.size(); i++) {
            JournalEntry e = entries.get(i);
            String date = e.getDate() == null ? "unknown date"
                    : LocalDate.ofInstant(e.getDate(), ZoneOffset.UTC).toString();
            sb.append("Entry ").append(i + 1).append(" (").append(date).append(") ").append(e.getTitle())
                    .append("\n").append(entryText("", e.getContent(), maxChars).strip()).append("\n\n");
        }
        sb.append("Question: ").append(question.strip());
        return sb.toString();
    }
}
