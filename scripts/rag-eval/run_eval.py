"""Retrieval check for POST /api/v1/ask against a running stack (docker compose --profile rag).

Registers a fresh user, creates the synthetic entries from dataset.json, waits until every entry is searchable,
then asks every question once with topK=4 and records which entries came back, in order, and how long /ask took.

Usage: py -3 scripts/rag-eval/run_eval.py --out docs/benchmarks/raw/rag-eval-run1.json [--base http://localhost:8080]
"""
import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def call(base, method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    data = json.loads((Path(__file__).parent / "dataset.json").read_text(encoding="utf-8"))
    username = "rag-eval-" + uuid.uuid4().hex[:8]
    password = "eval-password-" + uuid.uuid4().hex[:8]
    assert call(args.base, "POST", "/api/v1/auth/register", {"username": username, "password": password})[0] == 201
    status, tokens = call(args.base, "POST", "/api/v1/auth/login", {"username": username, "password": password})
    token = tokens["accessToken"]

    key_by_id = {}
    for e in data["entries"]:
        status, created = call(args.base, "POST", "/api/v1/entries",
                               {"title": e["title"], "content": e["content"]}, token)
        assert status == 201, (status, created)
        key_by_id[created["id"]] = e["key"]
    id_by_key = {v: k for k, v in key_by_id.items()}

    # Wait until every entry is searchable (indexing runs asynchronously from Kafka).
    start = time.time()
    pending = {e["key"]: e for e in data["entries"]}
    while pending and time.time() - start < 300:
        for key, e in list(pending.items()):
            status, res = call(args.base, "POST", "/api/v1/ask", {"question": e["title"] + " " + e["content"],
                                                                   "topK": 1}, token)
            if status == 200 and any(s["entryId"] == id_by_key[key] for s in res["sources"]):
                del pending[key]
        if pending:
            time.sleep(1)
    index_wait = time.time() - start
    assert not pending, "entries not indexed: " + ",".join(pending)

    # One uncounted warm-up call so the chat model is loaded in memory before timing.
    call(args.base, "POST", "/api/v1/ask", {"question": data["questions"][0]["question"], "topK": 4}, token)

    results = []
    for q in data["questions"]:
        t0 = time.perf_counter()
        status, res = call(args.base, "POST", "/api/v1/ask", {"question": q["question"], "topK": 4}, token)
        latency_ms = (time.perf_counter() - t0) * 1000
        assert status == 200, (status, res)
        ranked = [key_by_id.get(s["entryId"]) for s in res["sources"]]
        expected = set(q["expected"])
        results.append({
            "question": q["question"], "expected": q["expected"], "retrieved": ranked,
            "hit_at_1": bool(ranked) and ranked[0] in expected,
            "hit_at_4": any(r in expected for r in ranked[:4]),
            "answer": res["answer"],
            "answer_contains_fact": q["fact"].lower() in res["answer"].lower(),
            "latency_ms": round(latency_ms, 1),
        })

    n = len(results)
    summary = {
        "entries": len(data["entries"]), "questions": n,
        "recall_at_1": sum(r["hit_at_1"] for r in results) / n,
        "recall_at_4": sum(r["hit_at_4"] for r in results) / n,
        "answer_contains_fact": sum(r["answer_contains_fact"] for r in results) / n,
        "median_latency_ms": statistics.median(r["latency_ms"] for r in results),
        "max_latency_ms": max(r["latency_ms"] for r in results),
        "index_wait_seconds": round(index_wait, 1),
    }
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps({"summary": summary, "results": results}, indent=1), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
