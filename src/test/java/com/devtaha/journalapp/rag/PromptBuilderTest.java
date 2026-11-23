package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.entry.JournalEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private static JournalEntry entry(String title, String content) {
        return new JournalEntry("u1", title, content, Instant.parse("2026-03-05T10:00:00Z"));
    }

    @Test
    void numbersEntriesAndEndsWithTheQuestion() {
        String prompt = PromptBuilder.userPrompt("  Where did I hike?  ",
                List.of(entry("Hike", "Walked to the lake."), entry("Cooking", "Baked bread.")), 4000);

        assertThat(prompt).contains("Entry 1 (2026-03-05) Hike", "Walked to the lake.",
                "Entry 2 (2026-03-05) Cooking", "Baked bread.");
        assertThat(prompt).endsWith("Question: Where did I hike?");
        assertThat(prompt.indexOf("Entry 1")).isLessThan(prompt.indexOf("Entry 2"));
    }

    @Test
    void systemPromptRestrictsTheModelToTheEntries() {
        assertThat(PromptBuilder.SYSTEM).contains("ONLY", "I could not find that in your journal.");
    }

    @Test
    void longEntriesAreCutToTheLimit() {
        String text = PromptBuilder.entryText("Title", "x".repeat(500), 100);
        assertThat(text).hasSize(100).startsWith("Title\n");
    }

    @Test
    void shortEntriesAreKeptWhole() {
        assertThat(PromptBuilder.entryText("Title", "body", 100)).isEqualTo("Title\nbody");
        assertThat(PromptBuilder.entryText("Title", null, 100)).isEqualTo("Title\n");
    }
}
