"""Audit isolated source-reactor test identities before and after NR consolidation."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

MODULES = ("ipss.core.test", "ipss.plugin.core", "ipss.plugin.3phase", "ipss.test.plugin.core")


def collect(root, status):
    process = json.loads(status.read_text())
    if process["state"] != "completed" or process["exit_code"] != 0:
        raise RuntimeError("The source-reactor process did not complete successfully")
    tests, reports, totals = {}, [], Counter()
    for module in MODULES:
        files = sorted((root / module / "surefire-reports").glob("TEST-*.xml"))
        if not files:
            raise RuntimeError("Missing executed module: " + module)
        occurrences = Counter()
        for file in files:
            suite = ET.parse(file).getroot()
            reports.append(dict(path=str(file), sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
            for case in suite.findall("testcase"):
                identity = module + ":" + case.get("classname", "") + "#" + case.get("name", "")
                occurrence = occurrences[identity]
                occurrences[identity] += 1
                identity += ":" + str(occurrence)
                outcome = "passed"
                detail = ""
                exception = ""
                for kind in ("skipped", "failure", "error"):
                    node = case.find(kind)
                    if node is not None:
                        outcome = kind
                        exception = node.get("type", "")
                        detail = node.get("message", "") + "\n" + (node.text or "")
                        break
                totals[outcome] += 1
                tests[identity] = dict(outcome=outcome, exception=exception, detail=detail,
                    source_frames=re.findall(r"at ((?:com\.interpss|org\.interpss)[^\n]+\.java:\d+\))", detail)[:5])
    dumps = [str(p) for p in root.glob("*/surefire-reports/*") if ".dump" in p.name]
    return dict(totals=dict(totals), tests=tests, reports=reports, fork_dumps=dumps,
                process_status=process, run_root=str(root.resolve()))


def compare(current, baseline):
    previous, present = baseline["tests"], current["tests"]
    result = dict(missing=[], new_failures=[], new_skips=[], changed_failures=[], added=[])
    for identity, old in previous.items():
        new = present.get(identity)
        if new is None:
            result["missing"].append(identity)
        elif new["outcome"] in ("error", "failure"):
            if old["outcome"] not in ("error", "failure"):
                result["new_failures"].append(identity)
            elif any(new[k] != old[k] for k in ("exception", "source_frames", "detail")):
                result["changed_failures"].append(identity)
        elif new["outcome"] == "skipped" and old["outcome"] != "skipped":
            result["new_skips"].append(identity)
    for identity in sorted(present.keys() - previous.keys()):
        result["added"].append(identity)
        if present[identity]["outcome"] in ("error", "failure"):
            result["new_failures"].append(identity)
        elif present[identity]["outcome"] == "skipped":
            result["new_skips"].append(identity)
    result["requires_review"] = any(result[k] for k in ("missing", "new_failures", "new_skips", "changed_failures"))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-root", required=True, type=Path)
    parser.add_argument("--status-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args()
    audit = collect(args.run_root, args.status_file)
    if args.baseline:
        audit["comparison"] = compare(audit, json.loads(args.baseline.read_text()))
    audit["executed_tests"] = len(audit["tests"])
    audit["status"] = "completed_with_baseline_failures" if audit["totals"].get("error", 0) + audit["totals"].get("failure", 0) else "completed"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, indent=2) + "\n")
    print(json.dumps({k: audit[k] for k in ("status", "executed_tests", "totals", "fork_dumps")}, indent=2))
    if "comparison" in audit:
        print(json.dumps(audit["comparison"], indent=2))
    if audit["fork_dumps"] or audit.get("comparison", {}).get("requires_review"):
        raise SystemExit(2)
