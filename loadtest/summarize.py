"""Prints the headline numbers of k6 summary files (measured window only).

Usage: python loadtest/summarize.py docs/benchmarks/raw/read-*.json
Every value is read from the raw file; nothing is computed except the median across files.
"""
import json
import statistics
import sys


# k6 reports the rate of a tagged sub-metric over the whole test (warm-up included), so the
# measured-window rate is the sub-metric count divided by the length of the measured window.
MEASURE_SECONDS = 60


def pick(m, name):
    return m.get(name, {})


def summarize(path):
    with open(path, encoding="utf-8") as fh:
        metrics = json.load(fh)["metrics"]
    dur = pick(metrics, "http_req_duration{scenario:measure}")
    reqs = pick(metrics, "http_reqs{scenario:measure}")
    fail = pick(metrics, "http_req_failed{scenario:measure}")
    row = {
        "file": path.replace("\\", "/").split("/")[-1],
        "rps": reqs["count"] / MEASURE_SECONDS if "count" in reqs else None,
        "count": reqs.get("count"),
        "p50": dur.get("med"),
        "p95": dur.get("p(95)"),
        "p99": dur.get("p(99)"),
        "err": fail.get("value", fail.get("rate")),
    }
    for key in ("events_created", "events_updated", "events_deleted",
                "verify_entries_stored", "verify_stats_entry_count", "verify_drain_seconds", "verify_ok"):
        if key in metrics:
            row[key] = metrics[key].get("count", metrics[key].get("value"))
    sent = 0
    for key in ("events_created", "events_updated", "events_deleted"):
        sub = metrics.get(key + "{scenario:measure}")
        if sub:
            row[key + "_measure"] = sub.get("count")
            sent += sub.get("count", 0)
    if sent:
        row["events_per_second_measure"] = sent / MEASURE_SECONDS
    return row


def main(paths):
    rows = [summarize(p) for p in paths]
    for row in rows:
        print(json.dumps(row))
    if len(rows) > 1:
        for key in ("rps", "p50", "p95", "p99", "err"):
            values = [r[key] for r in rows if r.get(key) is not None]
            if values:
                print("median", key, statistics.median(values))


if __name__ == "__main__":
    main(sys.argv[1:])
