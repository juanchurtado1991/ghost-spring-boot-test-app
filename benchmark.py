import http.client
import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

def fetch(engine, op, mode):
    conn = http.client.HTTPConnection("localhost", 8081)
    try:
        conn.request("GET", f"/api/v1/benchmark/run?engine={engine}&operation={op}&mode={mode}")
        res = conn.getresponse()
        data = res.read()
        return json.loads(data)
    except Exception as e:
        return None
    finally:
        conn.close()

def run_benchmark():
    engines = ["jackson", "ghost"]
    operations = ["write", "read"]
    modes = ["string", "bytes", "stream"]
    iterations = 10000
    concurrency = 16

    results = []

    print(f"Starting Concurrent Benchmark... ({iterations} iterations per configuration, {concurrency} workers)")
    print("-" * 50)

    # Warmup
    print("Warming up JVM...")
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        list(executor.map(lambda _: fetch("jackson", "write", "string"), range(500)))

    for op in operations:
        for mode in modes:
            for engine in engines:
                print(f"Running {engine} - {op} - {mode}...", end=" ", flush=True)
                
                total_latency = 0
                total_garbage = 0
                payload_size = 0
                successful = 0
                
                start_time = time.time()
                
                with ThreadPoolExecutor(max_workers=concurrency) as executor:
                    futures = [executor.submit(fetch, engine, op, mode) for _ in range(iterations)]
                    for future in as_completed(futures):
                        data = future.result()
                        if data:
                            total_latency += data["latencyMs"]
                            total_garbage += data["garbageBytes"]
                            payload_size = data["payloadSize"]
                            successful += 1
                
                elapsed = time.time() - start_time
                if successful > 0:
                    avg_latency = total_latency / successful
                    avg_garbage = total_garbage / successful
                else:
                    avg_latency = 0
                    avg_garbage = 0
                
                # Avoid negative garbage from telemetry quirks
                if avg_garbage < 0:
                    avg_garbage = 0
                
                results.append({
                    "engine": engine.capitalize(),
                    "operation": op.capitalize(),
                    "mode": mode.capitalize(),
                    "avg_latency": f"{avg_latency:.2f} ms",
                    "avg_garbage": f"{avg_garbage / 1024:.2f} KB",
                    "ops_per_sec": int(successful / elapsed) if elapsed > 0 else 0,
                    "payload_size": f"{payload_size / 1024:.2f} KB"
                })
                print(f"Done. ({elapsed:.2f}s) ({successful}/{iterations} OK)")

    print("\n\n### 📊 Benchmark Results\n")
    print("| Engine | Operation | Mode | Payload Size | Avg Latency | Avg Memory (Waste) | Throughput (Ops/sec) |")
    print("|--------|-----------|------|--------------|-------------|--------------------|----------------------|")
    
    for r in results:
        print(f"| **{r['engine']}** | {r['operation']} | {r['mode']} | {r['payload_size']} | {r['avg_latency']} | {r['avg_garbage']} | {r['ops_per_sec']} |")

if __name__ == "__main__":
    run_benchmark()
