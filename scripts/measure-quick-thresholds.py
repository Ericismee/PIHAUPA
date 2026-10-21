"""Đo tuần tự toàn bộ transactions.txt; timeout chỉ hủy JVM benchmark do script tạo."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("root", type=Path)
parser.add_argument("--java", default="java")
parser.add_argument("--timeout", type=float, default=30)
parser.add_argument("--upper-fraction", type=float, default=0.99)
parser.add_argument("--lower-fraction", type=float, default=0.98)
parser.add_argument("--output", type=Path, default=Path("target/quick-thresholds.json"))
args = parser.parse_args()
workspace = Path(__file__).resolve().parent.parent
results = []
for file in sorted(args.root.glob("*/transactions.txt")):
    start = time.perf_counter()
    try:
        run = subprocess.run(
            [args.java, "-Xmx2g", "-cp", str(workspace / "target/classes") + ";" + str(workspace / "target/bench"),
             "QuickThresholdBench", str(file.resolve()), str(args.upper_fraction), str(args.lower_fraction)],
            cwd=workspace, text=True, encoding="utf-8", errors="replace",
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=args.timeout,
        )
        if run.returncode:
            result = {"name": file.parent.name, "state": "ERROR", "error": run.stderr[-2000:]}
        else:
            result = json.loads(run.stdout.strip().splitlines()[-1])
            result["state"] = "COMPLETED"
    except subprocess.TimeoutExpired:
        result = {"name": file.parent.name, "state": "TIMEOUT", "budgetSeconds": args.timeout}
    result["processSeconds"] = time.perf_counter() - start
    results.append(result)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({
        "configuration": {"upperFraction": args.upper_fraction, "lowerFraction": args.lower_fraction,
                          "policy": "TIGHT", "dataPercent": 100, "batchRatios": [80, 10, 10],
                          "repetitions": 1, "warmups": 0, "heap": "2g", "timeoutSeconds": args.timeout},
        "results": results,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    fields = list(dict.fromkeys(key for row in results for key in row))
    with args.output.with_suffix(".csv").open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(results)
    print(json.dumps(result, ensure_ascii=False), flush=True)
if not results or any(r["state"] != "COMPLETED" for r in results):
    raise SystemExit(1)
