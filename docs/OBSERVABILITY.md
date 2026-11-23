# Observability

The app exposes Prometheus metrics at `/actuator/prometheus`. `docker compose up -d` also starts Prometheus
(`prom/prometheus:v2.55.1`, config in `observability/prometheus.yml`, scrape interval 5 s). Open
`http://localhost:9090` and paste a query into the Graph tab. There is no Grafana.

The five metrics worth watching, each checked against this stack while it was under load:

| # | What to watch | PromQL | Read it as |
|---|---|---|---|
| 1 | Request rate | `sum(rate(http_server_requests_seconds_count[1m]))` | Requests per second the app is serving. |
| 2 | Server error ratio | `(sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) or vector(0)) / sum(rate(http_server_requests_seconds_count[1m]))` | Share of responses that are 5xx. Should stay at 0. |
| 3 | Mean request latency | `sum(rate(http_server_requests_seconds_sum{uri!~"/actuator.*"}[1m])) / sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))` | Average seconds per API request. Also useful: `max(http_server_requests_seconds_max{uri!~"/actuator.*"})` for the slowest recent request. |
| 4 | JVM heap use | `sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"})` | Fraction of the heap in use (the heap is capped at 384 MB). Sustained values near 1 with rising `rate(jvm_gc_pause_seconds_sum[1m])` mean memory pressure. |
| 5 | Kafka consumer lag | `max(kafka_consumer_fetch_manager_records_lag_max)` | Records the stats consumer is behind. It rises during write bursts and should return to 0; if it does not, stats are going stale. |

Two more that explained results during the benchmark:

- MongoDB connection pool: `max(mongodb_driver_pool_checkedout)` and `max(mongodb_driver_pool_waitqueuesize)`. A non-zero wait queue means requests are waiting for a database connection.
- Redis activity: `sum(rate(lettuce_command_completion_seconds_count[1m]))`. Empty when the cache is switched off.

Notes and limits

- Latency percentiles are not available here: the app does not publish histogram buckets, so the queries above give an average and a maximum only. The percentiles in `docs/BENCHMARKS.md` come from the load generator, not from Prometheus.
- `cache_gets_total` exists but stays at 0 because Redis cache statistics are not enabled in the app. Get the real hit and miss counts from Redis: `docker compose exec redis redis-cli info stats` (`keyspace_hits`, `keyspace_misses`).
- Kafka lag can show `NaN` while the consumer is idle and has nothing to report.
- The metrics and health endpoints are unauthenticated by design; keep them on an internal network.
