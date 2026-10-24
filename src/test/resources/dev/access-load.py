"""Tải CI cố định, chỉ loopback hai cổng Docker đã biết; không env file, provider hoặc mutation."""
import concurrent.futures
import json
import math
import os
import statistics
import time
import urllib.error
import urllib.request

TOKEN = os.environ["CI_ACCESS_LOAD_TOKEN"]
ROUTES = (
    "/api/v1/admin/access/resources?size=10&kind=ENDPOINT",
    "/api/v1/admin/access/registry/roles/page?size=10&query=CI",
)


def worker(number):
    # Không dùng proxy của máy chạy CI để bảo đảm workload chỉ tới loopback được khai báo.
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    samples = []
    for index in range(20):
        port = (18080, 18081)[number % 2]
        request = urllib.request.Request(
            f"http://127.0.0.1:{port}{ROUTES[index % 2]}",
            headers={"Authorization": "Bearer " + TOKEN, "Accept": "application/json"},
        )
        start = time.perf_counter()
        status = 0
        try:
            with client.open(request, timeout=5) as response:
                status = response.status
                value = json.load(response)
                if "data" not in value or value["data"] is None:
                    status = 0
        except urllib.error.HTTPError as error:
            status = error.code
        except (OSError, ValueError):
            status = 0
        samples.append(((time.perf_counter() - start) * 1000, status))
    return samples


start = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
    samples = [item for result in executor.map(worker, range(4)) for item in result]
duration = time.perf_counter() - start
latencies = sorted(item[0] for item in samples)
errors = sum(status != 200 for _, status in samples)
summary = {
    "workload": "4 workers x 20 GET; resource page + registry search; two CI nodes",
    "requests": len(samples),
    "durationSeconds": round(duration, 3),
    "rps": round(len(samples) / duration, 2),
    "meanMs": round(statistics.mean(latencies), 3),
    "p95Ms": round(latencies[math.ceil(len(samples) * 0.95) - 1], 3),
    "p99Ms": round(latencies[math.ceil(len(samples) * 0.99) - 1], 3),
    "errors": errors,
    "serverErrors": sum(status >= 500 for _, status in samples),
    "clientErrors": sum(400 <= status < 500 for _, status in samples),
}
print(json.dumps(summary))
if errors or summary["p95Ms"] > 2500:
    raise SystemExit("CI workload không đạt: yêu cầu 0 lỗi và p95 <= 2500ms, không suy ra tải production")
