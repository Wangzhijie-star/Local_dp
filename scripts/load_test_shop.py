#!/usr/bin/env python3
import argparse
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed


def percentile(sorted_values, percent):
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return sorted_values[0]
    index = (len(sorted_values) - 1) * percent
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return sorted_values[int(index)]
    lower_value = sorted_values[lower]
    upper_value = sorted_values[upper]
    return lower_value + (upper_value - lower_value) * (index - lower)


def single_request(url, timeout):
    start = time.perf_counter()
    request = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            elapsed_ms = (time.perf_counter() - start) * 1000
            result = {
                "ok": True,
                "http_status": response.getcode(),
                "elapsed_ms": elapsed_ms,
                "business_success": None,
                "message": None,
            }
            try:
                payload = json.loads(body)
                if isinstance(payload, dict):
                    result["business_success"] = payload.get("success")
                    result["message"] = payload.get("errorMsg")
            except json.JSONDecodeError:
                pass
            return result
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        elapsed_ms = (time.perf_counter() - start) * 1000
        result = {
            "ok": False,
            "http_status": exc.code,
            "elapsed_ms": elapsed_ms,
            "business_success": None,
            "message": body[:200],
        }
        try:
            payload = json.loads(body)
            if isinstance(payload, dict):
                result["business_success"] = payload.get("success")
                result["message"] = payload.get("errorMsg")
        except json.JSONDecodeError:
            pass
        return result
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return {
            "ok": False,
            "http_status": "EXCEPTION",
            "elapsed_ms": elapsed_ms,
            "business_success": None,
            "message": str(exc),
        }


def run_benchmark(url, total_requests, concurrency, timeout, warmup):
    for _ in range(warmup):
        single_request(url, timeout)

    started_at = time.perf_counter()
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(single_request, url, timeout) for _ in range(total_requests)]
        for future in as_completed(futures):
            results.append(future.result())
    total_elapsed = time.perf_counter() - started_at
    return results, total_elapsed


def print_report(args, url, results, total_elapsed):
    latencies = sorted(item["elapsed_ms"] for item in results)
    http_counter = Counter(str(item["http_status"]) for item in results)
    business_counter = Counter(str(item["business_success"]) for item in results)
    message_counter = Counter(item["message"] for item in results if item["message"])
    request_count = len(results)
    ok_count = sum(1 for item in results if item["ok"])
    error_count = request_count - ok_count

    print("=== Load Test Summary ===")
    print(f"url: {url}")
    print(f"total_requests: {request_count}")
    print(f"concurrency: {args.concurrency}")
    print(f"warmup: {args.warmup}")
    print(f"timeout_seconds: {args.timeout}")
    print(f"total_elapsed_seconds: {total_elapsed:.3f}")
    print(f"throughput_rps: {request_count / total_elapsed:.2f}" if total_elapsed else "throughput_rps: inf")
    print(f"http_ok_count: {ok_count}")
    print(f"http_error_count: {error_count}")
    print(f"http_status_distribution: {dict(http_counter)}")
    print(f"business_success_distribution: {dict(business_counter)}")
    print(f"latency_avg_ms: {statistics.mean(latencies):.2f}" if latencies else "latency_avg_ms: 0.00")
    print(f"latency_min_ms: {latencies[0]:.2f}" if latencies else "latency_min_ms: 0.00")
    print(f"latency_p50_ms: {percentile(latencies, 0.50):.2f}" if latencies else "latency_p50_ms: 0.00")
    print(f"latency_p95_ms: {percentile(latencies, 0.95):.2f}" if latencies else "latency_p95_ms: 0.00")
    print(f"latency_p99_ms: {percentile(latencies, 0.99):.2f}" if latencies else "latency_p99_ms: 0.00")
    print(f"latency_max_ms: {latencies[-1]:.2f}" if latencies else "latency_max_ms: 0.00")
    if message_counter:
        print(f"top_messages: {dict(message_counter.most_common(5))}")


def parse_args():
    parser = argparse.ArgumentParser(description="High concurrency load test for GET /shop/{id}")
    parser.add_argument("--base-url", default="http://127.0.0.1:8081", help="Base URL of the running service")
    parser.add_argument("--shop-id", type=int, required=True, help="Shop id used in the request path")
    parser.add_argument("--requests", type=int, default=1000, help="Total number of requests")
    parser.add_argument("--concurrency", type=int, default=100, help="Number of worker threads")
    parser.add_argument("--timeout", type=float, default=5.0, help="Single request timeout in seconds")
    parser.add_argument("--warmup", type=int, default=3, help="Warmup requests sent before the test starts")
    return parser.parse_args()


def main():
    args = parse_args()
    if args.requests <= 0:
        print("--requests must be greater than 0", file=sys.stderr)
        return 1
    if args.concurrency <= 0:
        print("--concurrency must be greater than 0", file=sys.stderr)
        return 1

    url = f"{args.base_url.rstrip('/')}/shop/{args.shop_id}"
    results, total_elapsed = run_benchmark(
        url=url,
        total_requests=args.requests,
        concurrency=args.concurrency,
        timeout=args.timeout,
        warmup=args.warmup,
    )
    print_report(args, url, results, total_elapsed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
