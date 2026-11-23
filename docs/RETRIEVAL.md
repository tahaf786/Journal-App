# Ask my journal: design and measured retrieval

`POST /api/v1/ask` answers a question from the caller's own journal entries. It is optional (`app.rag.enabled`, default false).

## Design

```
entry created/updated --Kafka--> consumer group journal-rag --embed--> Qdrant (collection journal_entries)
entry deleted ---------Kafka--> consumer group journal-rag ----------> point removed

POST /ask: embed question -> Qdrant search, filter userId == caller, top k
           -> entry text re-read from MongoDB (owner checked again)
           -> prompt "answer only from these entries" -> chat model -> {answer, sources}
```

- Models run in Ollama through its REST API; vectors live in Qdrant (REST API). Both are reached with plain `RestClient` behind three small interfaces (`EmbeddingClient`, `ChatClient`, `VectorStore`), so tests swap them for fakes.
- One point per entry (title and content together, cut at 4000 characters), cosine distance, id derived from the entry id so a replayed event overwrites instead of duplicating. Payload: `userId`, `entryId`, `title`, `date`. A keyword index on `userId` is created with the collection.
- The `userId` condition is part of the Qdrant search request, not a filter applied afterwards.
- Indexing retries twice and then dead-letters, using the same error handler as the stats consumer. `POST /api/v1/admin/rag/reindex` rebuilds points from MongoDB.
- Timeouts: embeddings 10 s, chat 120 s, Qdrant 5 s, connect 2 s. Any failure of Ollama or Qdrant becomes a 503 with a message.

## Models and images

| Item | Value |
|---|---|
| Model server image | `ollama/ollama:0.5.7` |
| Vector store image | `qdrant/qdrant:v1.12.4` |
| Embedding model | `nomic-embed-text` (274 MB) |
| Chat model (default) | `llama3.2:1b` (1.3 GB) |
| Chat model compared | `qwen2.5:0.5b` (397 MB) |

Why `llama3.2:1b`: the brief asked for the smaller of the two that answers coherently. `qwen2.5:0.5b` was faster but gave the fact in the answer for 9 of 20 questions against 11 of 20 for `llama3.2:1b` (same retrieval, three runs each, identical results every run). Neither is good at phrasing; `llama3.2:1b` was kept as the default because it was the better of the two on the same questions, at about 2.7 times the latency.

## Dataset and command

36 short synthetic entries (one user) and 20 questions, each with the entry that answers it. Questions are paraphrased, but many still share words with their entry. File: `scripts/rag-eval/dataset.json`.

```
APP_RAG_ENABLED=true docker compose --profile rag up -d          # RAG_CHAT_MODEL=qwen2.5:0.5b for the other model
py -3 scripts/rag-eval/run_eval.py --out docs/benchmarks/raw/rag-eval-llama3.2-1b-run1.json
```

Each run registers a fresh user, creates the entries, waits until every entry is searchable, makes one uncounted warm-up `/ask` call, then asks the 20 questions once each with `topK` 4. Three runs per model, new user each time.

## Results (raw files: `docs/benchmarks/raw/rag-eval-*.json`)

| Measure | `llama3.2:1b` (runs 1/2/3) | `qwen2.5:0.5b` (runs 1/2/3) |
|---|---|---|
| recall@1 | 1.00 / 1.00 / 1.00 | 1.00 / 1.00 / 1.00 |
| recall@4 | 1.00 / 1.00 / 1.00 | 1.00 / 1.00 / 1.00 |
| answer contains the expected fact (keyword check) | 0.55 / 0.55 / 0.55 | 0.45 / 0.45 / 0.45 |
| median `/ask` latency, ms | 2804.2 / 2870.85 / 2788.4 | 1005.5 / 1040.1 / 1098.85 |
| slowest `/ask`, ms | 4301.1 / 4317.3 / 4112.4 | 1182.8 / 1416.7 / 1270.6 |
| seconds until all 36 entries searchable | 114.8 / 105.7 / 102.9 | 27.8 / 21.8 / 23.1 |

Files: `rag-eval-llama3.2-1b-run{1,2,3}.json`, `rag-eval-qwen2.5-0.5b-run{1,2,3}.json`. Each has a `summary` object and per-question `results` (retrieved entry keys in rank order, answer text, latency).

Median latency across the three `llama3.2:1b` runs: 2804.2 ms (the middle of the three per-run medians). Latency covers question embedding, vector search, MongoDB reads and generation.

## What these numbers mean, and do not

- Retrieval found the right entry first for all 20 questions in every run. The set is small and easy (36 distinct topics, shared keywords), so a perfect score says the pipeline is wired correctly, not that it will hold on a real journal with years of similar entries.
- The weak part is generation. The fact check is a crude keyword match (for example "tube" was not in an answer that said the tyre was fixed), so 0.55 understates some answers and the true rate was not judged by hand. The small models often reply that they could not find the answer even when the right entry was supplied, and `llama3.2:1b` sometimes echoes the entry header.
- Synthetic data, written by the builder, one user, one laptop (CPU only, Docker limited to 7 GB, Ollama capped at 2 GB), no concurrent load, a single pass per question.
- The three runs gave identical retrieval and answer-quality figures because generation uses temperature 0; the runs are repeats, not independent samples.
- Isolation is tested separately: `RagIsolationIT` starts a real Qdrant container with a deterministic fake embedding model and shows user A never retrieves user B's entries, that deletion removes the point, and that an update replaces the vector.
