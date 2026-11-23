package com.devtaha.journalapp.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantVectorStoreTest {

    private MockRestServiceServer server;
    private QdrantVectorStore store;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://qdrant:6333");
        server = MockRestServiceServer.bindTo(builder).build();
        store = new QdrantVectorStore(builder.build(), "journal_entries");
    }

    @Test
    void pointIdIsStablePerEntryAndDiffersBetweenEntries() {
        assertThat(QdrantVectorStore.pointId("abc")).isEqualTo(QdrantVectorStore.pointId("abc"));
        assertThat(QdrantVectorStore.pointId("abc")).isNotEqualTo(QdrantVectorStore.pointId("abd"));
        assertThat(QdrantVectorStore.pointId("abc")).matches("[0-9a-f-]{36}");
    }

    @Test
    void userFilterIsAMustMatchOnUserId() {
        assertThat(QdrantVectorStore.userFilter("u1")).isEqualTo(java.util.Map.of("must",
                List.of(java.util.Map.of("key", "userId", "match", java.util.Map.of("value", "u1")))));
    }

    @Test
    void searchSendsTheUserFilterInsideTheQueryAndParsesHits() {
        server.expect(requestTo("http://qdrant:6333/collections/journal_entries/points/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.filter.must[0].key").value("userId"))
                .andExpect(jsonPath("$.filter.must[0].match.value").value("alice"))
                .andExpect(jsonPath("$.limit").value(3))
                .andRespond(withSuccess("{\"result\":[{\"id\":\"x\",\"score\":0.8,\"payload\":{\"userId\":\"alice\","
                        + "\"entryId\":\"e1\",\"title\":\"Hike\",\"date\":\"2026-03-05T10:00:00Z\"}}]}",
                        MediaType.APPLICATION_JSON));

        List<VectorStore.Hit> hits = store.search("alice", new float[]{0.1f, 0.2f}, 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).entryId()).isEqualTo("e1");
        assertThat(hits.get(0).score()).isEqualTo(0.8);
        server.verify();
    }

    @Test
    void serverErrorBecomesRagUnavailable() {
        server.expect(requestTo("http://qdrant:6333/collections/journal_entries/points/search"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> store.search("alice", new float[]{1f}, 4))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void deletingFromAMissingCollectionIsANoOp() {
        server.expect(requestTo("http://qdrant:6333/collections/journal_entries/points/delete?wait=true"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        store.delete("e1");

        server.verify();
    }
}
