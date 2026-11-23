# Benchmarks

**These are single-machine local results, not production traffic.** The load generator, the app, MongoDB, Redis and
Kafka all ran on one laptop and competed for the same CPU and memory. Use the numbers to compare cache off against
cache on on this machine, not as a capacity claim.

Raw k6 summaries for every run are in `docs/benchmarks/raw/` (`*.json`). Each figure below comes from those files;
percentages and ratios are computed from them and rounded for display.

## Environment

| Item | Value |
|---|---|
| Machine | Laptop, AMD Ryzen 7 5800H, 16 logical cores, 13.9 GB RAM, Windows 11 |
| Docker | Docker Desktop 29.9.3 (Linux engine), given 16 CPUs and 7.19 GB RAM |
| App | Spring Boot 3.5, Java 17, `-Xmx384m`, container limit 640 MB |
| Images | `mongo:7.0` (single-node replica set, 0.25 GB WiredTiger cache), `redis:7.2-alpine` (64 MB, allkeys-lru), `apache/kafka:3.9.1`, `prom/prometheus:v2.55.1` |
| Load tool | k6 v2.4.0 from the `grafana/k6:latest` image, attached to the compose network (nothing installed on the host) |
| Read dataset | 20 users x 50 entries = 1,000 entries (about 330 characters of content each), seeded fresh on every run |
| Cache OFF / ON | `APP_CACHE_ENABLED=false` / `true` (the app container is recreated to switch) |

## How it was run

```bash
docker compose up -d --build
loadtest/bench-all.sh          # reads: 3 rounds x (cache off, cache on) x 50/200/400 virtual users; then writes
# single run, for example:
APP_CACHE_ENABLED=false docker compose up -d --force-recreate app
loadtest/run.sh read off 200 1     # kind, cache label, virtual users, run number
loadtest/run.sh write on 20 1
py -3 loadtest/summarize.py docs/benchmarks/raw/read-cache-on-vus200-run*.json   # medians
```

`loadtest/run.sh` runs `grafana/k6` with `--summary-export`, which writes the raw JSON. The measured window of each
run is 60 seconds at a fixed number of virtual users, after a 20 second warm-up at the same level that is excluded
from every number. Virtual users send requests back to back (no think time). Rounds alternate cache off and cache on
so slow drift on the laptop affects both.

## Read mix (70% `GET /entries/{id}`, 30% `GET /entries?page=0..2&size=20`)

Median of 3 runs for each metric (taken per metric, so a row can mix different runs). Requests per second is the
request count in the measured window divided by 60. Error rate was 0 in all 18 runs (under the 1% limit).

| Virtual users | Cache | Requests/s | p50 ms | p95 ms | p99 ms | Requests/s range over 3 runs |
|---:|---|---:|---:|---:|---:|---|
| 50 | off | 4,205 | 10.0 | 24.5 | 37.8 | 4,020 to 4,419 |
| 50 | on | 7,319 | 5.5 | 13.1 | 17.4 | 6,583 to 7,382 |
| 200 | off | 5,024 | 31.0 | 119.6 | 238.4 | 4,925 to 5,081 |
| 200 | on | 7,249 | 26.6 | 45.6 | 68.0 | 6,066 to 7,325 |
| 400 | off | 4,682 | 61.4 | 221.8 | 453.8 | 4,615 to 4,908 |
| 400 | on | 6,848 | 53.8 | 89.1 | 143.4 | 5,419 to 6,883 |

Files: `read-cache-{off,on}-vus{50,200,400}-run{1,2,3}.json`.

Highest level that held with under 1% errors: **400 virtual users, in both modes** (0 errors in every run). That is the
highest level tested, not a proven limit; higher levels were not tried.

### What the cache changed

- With the cache on, throughput was about 1.7x higher at 50 virtual users and about 1.4x to 1.5x higher at 200 and 400 (medians above).
- The biggest change is in the tail: at 200 virtual users p95 went from 119.6 ms to 45.6 ms and p99 from 238.4 ms to 68.0 ms.
- Throughput grows little between 50 and 200 virtual users (cache off 4,205 to 5,024; cache on 7,319 to 7,249) and falls slightly at 400; extra virtual users mostly add queueing delay (p50 roughly doubles each time the user count does or more). The system was already saturated at 50 virtual users on this machine.
- In all three cache-on levels the third run was slower than the first two (see the ranges). I did not investigate the cause; thermal or background load on the laptop is a possibility, and it is why the ranges are shown.

### Caveats

- The dataset is tiny (1,000 entries). MongoDB serves it from memory, so the cache-off case is close to the best case for the database. A larger dataset or a remote database would likely widen the gap, but that was not measured here.
- Only reads were measured for the cache comparison. Writes empty every cached page (see README), so a write-heavy mix would benefit less; the write runs below were done with the cache on and not compared with it off.
- Load generator, app and databases share one machine, and the app has a 384 MB heap. Results from another machine will differ.
- No think time and a fixed user pool: this is a closed-loop stress test, not a model of real user behaviour.

## Write mix and Kafka events

Each of 20 virtual users owns one account and loops: create an entry, update it, and delete every fifth entry. Every
write publishes one Kafka event, so published events per second equals the write request rate. Cache on. Window as
above (20 s warm-up, 60 s measured). After the load stops, the script waits until the stats endpoint matches the
entries actually stored and records the time. Consumer lag was sampled every 3 seconds with `kafka-consumer-groups.sh`;
each run started with lag at 0 and ended with `final-lag 0`.

| Run | Events published/s (measured window) | Write p50 ms | p95 ms | p99 ms | Error rate | Peak sampled lag | Time to drain after load stops |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1,063 | 17.7 | 26.9 | 36.4 | 0 | 81,053 | 598.1 s |
| 2 | 963 | 19.3 | 31.3 | 42.9 | 0 | 71,829 | 563.9 s |
| 3 | 819 | 21.8 | 41.0 | 61.0 | 0 | 63,055 | 486.0 s |
| **Median** | **963** | **19.3** | **31.3** | **42.9** | **0** | **71,829** | **563.9 s** |

Files: `write-cache-on-vus20-run{1,2,3}.json` (metrics, including `verify_*` and `events_*`) and
`write-cache-on-vus20-run{1,2,3}.kafka-lag.txt` (lag samples).

Correctness check after each run (events sent by the script, taken over the whole run including warm-up):

| Run | Created | Deleted | Created minus deleted | Entries stored (list total) | Stats `entryCount` (sum of users) | Match |
|---|---:|---:|---:|---:|---:|---|
| 1 | 39,051 | 7,799 | 31,252 | 31,252 | 31,252 | yes |
| 2 | 34,623 | 6,910 | 27,713 | 27,713 | 27,713 | yes |
| 3 | 30,397 | 6,060 | 24,337 | 24,337 | 24,337 | yes |

Updates (39,051 / 34,623 / 30,397) change no counts. No event was lost or counted twice in these runs.

### What this shows

- The API published about 1,000 events per second, far faster than the stats consumer can apply them. The consumer handles one Kafka record at a time, and each record is a MongoDB transaction (insert the processed-event marker, read and save the summary). Lag therefore grew during the load and took 486 to 598 seconds to drain after it stopped.
- Counts are eventually consistent: for several minutes after a heavy burst, `GET /users/me/stats` was behind the entries actually stored. The numbers were correct once the backlog drained.
- This was the baseline. The fix (batch processing plus one listener thread per partition) is measured in the next section.
- Writes ran at 20 virtual users only; no higher write level was tried.

## Write mix after the stats consumer fix (v2)

Same test, same machine, same settings (20 virtual users, cache on, 20 s warm-up, 60 s measured, 3 runs, base compose
stack only). The consumer now runs 3 listener threads (one per partition) and applies a whole poll in one Mongo
transaction (see `event/EntryEventConsumer.java`). Lag samples every 3 s; each run started at lag 0 and ended with `final-lag 0`.

| Run | Events published/s | Write p50 ms | p95 ms | p99 ms | Error rate | Peak sampled lag | Time to drain after load stops |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 981 | 17.8 | 36.9 | 52.6 | 0 | 140 | 0.06 s |
| 2 | 1,139 | 15.9 | 28.0 | 43.5 | 0 | 133 | 0.03 s |
| 3 | 995 | 18.1 | 29.4 | 42.9 | 0 | 110 | 0.02 s |
| **Median** | **995** | **17.8** | **29.4** | **43.5** | **0** | **133** | **0.03 s** |

Files: `write-v2-cache-on-vus20-run{1,2,3}.json`, `.console.txt` and `.kafka-lag.txt` in `docs/benchmarks/raw/`.

| Run | Created | Deleted | Created minus deleted | Entries stored | Stats `entryCount` | Match |
|---|---:|---:|---:|---:|---:|---|
| 1 | 31,432 | 6,273 | 25,159 | 25,159 | 25,159 | yes |
| 2 | 42,401 | 8,468 | 33,933 | 33,933 | 33,933 | yes |
| 3 | 35,861 | 7,159 | 28,702 | 28,702 | 28,702 | yes |

### Before and after (medians of 3 runs)

| | Before (`write-cache-on-vus20-run*`) | After (`write-v2-cache-on-vus20-run*`) |
|---|---:|---:|
| Events published/s | 963 | 995 |
| Write p95 | 31.3 ms | 29.4 ms |
| Peak sampled lag | 71,829 | 133 |
| Drain time after load stops | 563.9 s | 0.03 s |
| Stats count = entries stored | yes, 3 of 3 | yes, 3 of 3 |

Mapping: before = rows of the first table above (runs 1, 2, 3 of `write-cache-on-vus20`); after = `write-v2-cache-on-vus20-run{1,2,3}.json` (`events_*`, `verify_*`, `http_req_duration{scenario:measure}`) and the lag files. Medians are per metric.

Honest notes: the publish rate and write latency are within run-to-run noise of the baseline (the API was never the limit, and
the load generator shares the laptop); the change affects the consumer, so the gain shows in lag and drain time. Batch
processing and concurrency were deployed together, so how much each contributes was not measured separately. The
stats are now close to real time under this load (peak lag of about 130 events, sampled every 3 s).

## Honest notes on the process

- An earlier write attempt (visible in `docs/benchmarks/bench-all.log`) was discarded: k6's default 60 s teardown limit expired while waiting for the consumer, and lag from one run carried into the next. The script was fixed (teardown limit, tokens refreshed during the wait, lag back to 0 before each run) and all three write runs were redone. Only the redone runs are in `raw/`.
- Short calibration runs used to choose the virtual-user levels were deleted and are not reported.
