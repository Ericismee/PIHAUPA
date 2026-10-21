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
parser.add_argument("--dataset", help="Tên thư mục cần đo riêng; mặc định tất cả tệp")
parser.add_argument("--repetitions", type=int, default=1)
parser.add_argument("--warmups", type=int, default=0)
parser.add_argument("--plan", type=Path, help="JSON ngưỡng riêng theo tên thư mục dữ liệu")
parser.add_argument("--output", type=Path, default=Path("target/quick-thresholds.json"))
args = parser.parse_args()
if args.timeout <= 0 or args.repetitions < 1 or args.warmups < 0:
    parser.error("timeout và repetitions phải dương; warmups không âm")
workspace = Path(__file__).resolve().parent.parent
plan = {key.lower(): value for key, value in json.loads(args.plan.read_text(encoding="utf-8")).items()} if args.plan else None
results = []
for file in sorted(args.root.glob("*/transactions.txt")):
    if args.dataset and file.parent.name.lower() != args.dataset.lower():
        continue
    if plan is not None and file.parent.name.lower() not in plan:
        continue
    fractions = plan[file.parent.name.lower()] if plan is not None else {
        "upperFraction": args.upper_fraction, "lowerFraction": args.lower_fraction}
    start = time.perf_counter()
    try:
        log = args.output.parent / (args.output.stem + "-" + file.parent.name + ".log")
        log.parent.mkdir(parents=True, exist_ok=True)
        with log.open("w", encoding="utf-8") as log_file:
            run = subprocess.run(
                [args.java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                 "-Dbench.repetitions=" + str(args.repetitions), "-Dbench.warmups=" + str(args.warmups),
                 "-cp", str(workspace / "target/classes") + ";" + str(workspace / "target/bench"),
                 "QuickThresholdBench", str(file.resolve()), str(fractions["upperFraction"]), str(fractions["lowerFraction"])],
                cwd=workspace, text=True, encoding="utf-8", errors="replace",
                stdout=subprocess.PIPE, stderr=log_file, timeout=args.timeout,
            )
        if run.returncode:
            result = {"name": file.parent.name, "state": "ERROR", "error": log.read_text(encoding="utf-8", errors="replace")[-2000:]}
        else:
            result = json.loads(run.stdout.strip().splitlines()[-1])
            result["state"] = "COMPLETED"
    except subprocess.TimeoutExpired:
        result = {"name": file.parent.name, "state": "TIMEOUT", "budgetSeconds": args.timeout}
    result["processSeconds"] = time.perf_counter() - start
    result.update(fractions)
    results.append(result)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({
        "configuration": {"upperFraction": args.upper_fraction if plan is None else None,
                          "lowerFraction": args.lower_fraction if plan is None else None,
                          "policy": "TIGHT", "dataPercent": 100, "batchRatios": [80, 10, 10],
                          "repetitions": args.repetitions, "warmups": args.warmups, "heap": "2g", "timeoutSeconds": args.timeout,
                          "plan": args.plan.name if args.plan else None},
        "results": results,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    fields = list(dict.fromkeys(key for row in results for key in row))
    with args.output.with_suffix(".csv").open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(results)
    print(json.dumps(result, ensure_ascii=False), flush=True)
if not results:
    raise SystemExit("Không tìm thấy transactions.txt phù hợp với thư mục, dataset hoặc plan đã chọn.")
if any(r["state"] != "COMPLETED" for r in results):
    raise SystemExit(1)
