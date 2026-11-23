package com.devtaha.journalapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntryAccessControlIT extends AbstractIntegrationTest {

    @Test
    void userCannotReadUpdateOrDeleteAnotherUsersEntry() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String entryId = createEntry(alice, "Private", "Only for Alice").get("id").asText();
        String url = "/api/v1/entries/" + entryId;

        getAs(url, bob).andExpect(status().isNotFound());
        putAs(url, bob, Map.of("title", "Hijacked", "content", "x")).andExpect(status().isNotFound());
        deleteAs(url, bob).andExpect(status().isNotFound());

        JsonNode stillThere = getJson(url, alice);
        assertThat(stillThere.get("title").asText()).isEqualTo("Private");
    }

    @Test
    void listContainsOnlyTheCallersEntries() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        createEntry(alice, "Alice entry", "a");

        assertThat(getJson("/api/v1/entries", alice).get("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/api/v1/entries", bob).get("totalElements").asLong()).isZero();
    }

    @Test
    void entryCrudRoundTrip() throws Exception {
        String token = newUserToken();
        String id = createEntry(token, "First", "one").get("id").asText();

        putAs("/api/v1/entries/" + id, token, Map.of("title", "Renamed", "content", "two"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));
        getAs("/api/v1/entries/" + id, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("two"));

        deleteAs("/api/v1/entries/" + id, token).andExpect(status().isNoContent());
        getAs("/api/v1/entries/" + id, token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        String token = newUserToken();

        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/entries"), token,
                Map.of("title", " ", "content", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("title"));
    }

    @Test
    void listSupportsPaginationAndSorting() throws Exception {
        String token = newUserToken();
        createEntry(token, "Charlie", "c");
        createEntry(token, "Alpha", "a");
        createEntry(token, "Bravo", "b");

        JsonNode firstPage = getJson("/api/v1/entries?size=2&sort=title,asc", token);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("content").get(0).get("title").asText()).isEqualTo("Alpha");

        JsonNode secondPage = getJson("/api/v1/entries?size=2&page=1&sort=title,asc", token);
        assertThat(secondPage.get("content")).hasSize(1);
        assertThat(secondPage.get("content").get(0).get("title").asText()).isEqualTo("Charlie");
    }

    @Test
    void sortingByAnUnknownFieldIsRejected() throws Exception {
        String token = newUserToken();

        getAs("/api/v1/entries?sort=userId,asc", token).andExpect(status().isBadRequest());
    }

    @Test
    void searchMatchesTitleAndContentOfTheCallersEntriesOnly() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        createEntry(alice, "Trip notes", "We visited the lighthouse");
        createEntry(alice, "Groceries", "milk and bread");
        createEntry(bob, "Lighthouse", "Bob also likes lighthouses");

        JsonNode result = getJson("/api/v1/entries?q=lighthouse", alice);

        assertThat(result.get("totalElements").asLong()).isEqualTo(1);
        assertThat(result.get("content").get(0).get("title").asText()).isEqualTo("Trip notes");
    }
}
