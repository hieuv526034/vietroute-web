"""Staging Docker CI cố định; workload GET có trần, không env file, URL ngoài, provider hoặc mutation."""
import concurrent.futures
import json
import math
import os
import re
import statistics
import threading
import time
import urllib.request

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Chỉ container CI loopback; không chạy vào production")
TOKEN = os.environ["CI_BUSINESS_LOAD_TOKEN"]
METRICS_TOKEN = os.environ["CI_BUSINESS_METRICS_TOKEN"]
ROUTES = (
    "/api/v1/admin/reports?fromDate=2027-02-01&toDate=2027-02-28",
    "/api/v1/carriers?query=needlexyz&size=10",
    "/api/v1/admin/reports/carriers?query=needlexyz&size=10",
    "/api/v1/admin/vehicle-types?query=needlexyz&size=10",
)


def client():
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def worker(number, operations, start):
    http = client()
    port = (18080, 18081)[number % 2]
    samples = []
    start.wait()
    for index in range(operations):
        at = time.perf_counter()
        status = 0
        try:
            request = urllib.request.Request(
                f"http://127.0.0.1:{port}{ROUTES[index % len(ROUTES)]}",
                headers={"Authorization": "Bearer " + TOKEN, "Accept": "application/json"},
            )
            with http.open(request, timeout=5) as response:
                value = json.load(response).get("data")
                if index % len(ROUTES) == 0:
                    valid = isinstance(value, dict) and value.get("tripCount", 0) > 0 and value.get("bookingCount", 0) > 0
                else:
                    valid = isinstance(value, dict) and value.get("totalElements") == 20 and len(value.get("content", [])) == 10
                if valid:
                    status = response.status
        except (OSError, ValueError):
            pass
        samples.append(((time.perf_counter() - at) * 1000, status))
        time.sleep(0.01)
    return samples


stop = threading.Event()
observed = {port: {"active": 0, "waiting": 0, "maximum": 0} for port in (18080, 18081)}


def sample_pools():
    http = client()
    while not stop.is_set():
        for port, values in observed.items():
            try:
                request = urllib.request.Request(
                    f"http://127.0.0.1:{port}/internal/metrics",
                    headers={"Authorization": "Bearer " + METRICS_TOKEN},
                )
                with http.open(request, timeout=3) as response:
                    body = response.read().decode("utf-8")
                for key, metric in (("active", "vietroute_pool_active"), ("waiting", "vietroute_pool_waiting"), ("maximum", "vietroute_pool_maximum")):
                    for value in re.findall(r"^" + metric + r'(?:\{[^\n]*\})? ([0-9.eE+\-]+)$', body, re.MULTILINE):
                        values[key] = max(values[key], float(value))
            except (OSError, ValueError):
                pass
        stop.wait(0.1)


sampler = threading.Thread(target=sample_pools)
sampler.start()
try:
    for concurrency, operations in ((2, 25), (4, 25), (8, 75)):
        start = threading.Barrier(concurrency)
        began = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = [executor.submit(worker, number, operations, start) for number in range(concurrency)]
            samples = [item for future in futures for item in future.result()]
        latencies = sorted(value for value, _ in samples)
        errors = sum(status != 200 for _, status in samples)
        result = {"workload": "HTTP report/search; 30k trips/90k bookings; two Docker nodes", "concurrency": concurrency, "requests": len(samples), "durationSeconds": round(time.perf_counter() - began, 3), "p95Ms": round(latencies[math.ceil(len(samples) * .95) - 1], 3), "p99Ms": round(latencies[math.ceil(len(samples) * .99) - 1], 3), "meanMs": round(statistics.mean(latencies), 3), "errors": errors}
        print(json.dumps(result), flush=True)
        if errors or result["p95Ms"] > 2500:
            raise SystemExit("HTTP staging yêu cầu 0 lỗi, p95 <=2500ms; không suy SLA production")
finally:
    stop.set()
    sampler.join(timeout=8)
    if sampler.is_alive():
        raise SystemExit("Pool sampler chưa dừng")
print(json.dumps({"sampledPools": observed, "samplingSeconds": .1, "scope": "observed samples, not exact peaks"}))
if any(values["maximum"] != 2 or values["active"] > 2 for values in observed.values()):
    raise SystemExit("Hai node phải cấu hình pool2 và sampler phải đọc được metrics")
