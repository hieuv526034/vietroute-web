"""Đo baseline ngắn trên container CI local, không dùng URL hoặc credentials production."""

import concurrent.futures
import json
import statistics
import sys
import time
import urllib.request
from urllib.parse import urlsplit

base = sys.argv[1]
assert urlsplit(base).hostname in {"127.0.0.1", "localhost"}, "Only isolated local containers are allowed"


def call(_):
    start = time.perf_counter()
    with urllib.request.urlopen(base + "/api/v1/catalogs/VEHICLE_TYPE", timeout=15) as response:
        assert response.status == 200
        assert json.load(response)["success"]
    return (time.perf_counter() - start) * 1000


started = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    durations = sorted(pool.map(call, range(48)))
elapsed = time.perf_counter() - started
print(json.dumps({
    "requests": len(durations), "concurrency": 8, "errors": 0,
    "mean_ms": round(statistics.mean(durations), 2),
    "p95_ms": round(durations[int(len(durations) * 0.95) - 1], 2),
    "throughput_rps": round(len(durations) / elapsed, 2),
    "scope": "short isolated CI baseline; not production capacity",
}))
