"""Run the maintained complete Texas7k benchmark with verified single-CPU affinity."""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = "org.interpss.core.dstab.benchmark.Texas7kSingleCpuBenchmark"
REPORT = ROOT / "ipss.test.plugin.core/target/surefire-reports/TEST-org.interpss.core.dstab.EndpointAlignedStateMonitorTest.xml"


def signature(path: Path) -> dict:
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"bytes": path.stat().st_size, "sha256": digest}


def portable(path: Path) -> str:
    for label, base in [("repo", ROOT), ("m2", Path.home() / ".m2")]:
        try:
            return label + "/" + path.absolute().relative_to(base).as_posix()
        except ValueError:
            pass
    return "external/" + path.name


def source_reference(repo: Path) -> str:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    dirty = subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True).strip()
    return commit + ("-dirty" if dirty else "")


def run_windows(command: list[str], cwd: Path, cpu: int) -> dict:
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    kernel.GetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(ctypes.c_size_t)]
    kernel.GetProcessAffinityMask.restype = wintypes.BOOL
    kernel.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
    kernel.SetProcessAffinityMask.restype = wintypes.BOOL

    def mask(handle):
        value, system = ctypes.c_size_t(), ctypes.c_size_t()
        if not kernel.GetProcessAffinityMask(handle, ctypes.byref(value), ctypes.byref(system)):
            raise ctypes.WinError(ctypes.get_last_error())
        return value.value, system.value

    parent = kernel.GetCurrentProcess()
    original, system = mask(parent)
    requested = 1 << cpu
    if requested & original != requested:
        raise ValueError("The selected logical CPU is outside the permitted affinity mask")
    if not kernel.SetProcessAffinityMask(parent, requested):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        # The JVM inherits this mask before its first thread executes.
        process = subprocess.Popen(command, cwd=cwd)
    finally:
        if not kernel.SetProcessAffinityMask(parent, original):
            raise ctypes.WinError(ctypes.get_last_error())
    try:
        observed, _ = mask(wintypes.HANDLE(int(process._handle)))
        if observed != requested:
            raise RuntimeError("The JVM did not inherit single-CPU affinity")
        process.wait()
        final, _ = mask(wintypes.HANDLE(int(process._handle)))
        record = {"pid": process.pid, "requested_mask": requested, "observed_mask_before": observed,
                  "observed_mask_after": final, "system_mask": system, "exit_code": process.returncode}
        if process.returncode or final != requested:
            raise RuntimeError("Benchmark failed or JVM affinity changed: " + json.dumps(record))
        return record
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Supplied case directory containing RAW, DYR and preparation files")
    parser.add_argument("--output", type=Path, help="New result directory; existing output is never overwritten")
    parser.add_argument("--classpath-report", type=Path, default=REPORT, help="Successful maintained endpoint-recorder test report")
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("texas7k_single_cpu.json"), help="Explicit benchmark configuration fixture")
    parser.add_argument("--cpu", type=int, default=6, help="Logical Windows CPU index (default: 6)")
    parser.add_argument("--warmups", type=int, default=4)
    parser.add_argument("--measurements", type=int, default=5)
    parser.add_argument("--java", type=Path, help="Java 21 executable; defaults to JAVA_HOME or PATH")
    parser.add_argument("--bounded-child", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if os.name != "nt":
        parser.error("This launcher verifies Windows process affinity")
    if args.cpu < 0 or args.cpu >= 8 * ctypes.sizeof(ctypes.c_size_t) or args.warmups < 1 or args.measurements < 1:
        parser.error("CPU index must be valid and warmup/measurement counts positive")
    inputs = args.input.absolute()
    for name in ["Texas7k_20210804.RAW", "Texas7k_20210804.dyr", "Texas7k_20210804_basegen.bsg"]:
        if not (inputs / name).is_file():
            parser.error("Required case input is missing: " + name)
    output = (args.output or ROOT / "target" / ("texas7k-benchmark-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))).absolute()
    if output.exists():
        parser.error("Refusing to overwrite an existing output directory")
    output.parent.mkdir(parents=True, exist_ok=True)
    if not args.bounded_child:
        child_args = [sys.executable, str(Path(__file__).resolve()), *sys.argv[1:], "--bounded-child",
                      "--input", str(inputs), "--output", str(output),
                      "--classpath-report", str(args.classpath_report.absolute()), "--config", str(args.config.absolute())]
        if args.java is not None:
            child_args += ["--java", str(args.java.absolute())]
        return subprocess.call([sys.executable, str(ROOT / "tools/run_bounded.py"), "--log",
                                str(ROOT / "target" / (output.name + ".log")), "--", *child_args], cwd=ROOT)
    report = args.classpath_report.absolute()
    if not report.is_file():
        parser.error("Run the maintained EndpointAlignedStateMonitorTest build before benchmarking")
    suite = ET.parse(report).getroot()
    if int(suite.get("tests", "0")) < 1 or any(int(suite.get(key, "0")) for key in ["errors", "failures", "skipped"]):
        parser.error("The classpath report must contain passing, non-skipped tests")
    cp = next((p.get("value") for p in suite.findall(".//property") if p.get("name") == "java.class.path"), None)
    if not cp:
        parser.error("The report has no runtime classpath")
    entries = [Path(value) for value in cp.split(os.pathsep) if value]
    class_file = Path(*MAIN.split(".")).with_suffix(".class")
    if not any(p.is_dir() and (p / class_file).is_file() for p in entries):
        parser.error("Rebuild integration test classes to compile the maintained benchmark")
    java_home = os.environ.get("JAVA_HOME")
    java = args.java or (Path(java_home) / "bin/java.exe" if java_home else Path(shutil.which("java") or "java"))
    refs = "plugin:" + source_reference(ROOT) + ";core:" + source_reference(ROOT.parent / "core")
    config = args.config.absolute()
    flags = json.loads(config.read_text())["optimization_properties"]
    command = [str(java), "-Xmx2g", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC", "-Xbatch",
               *["-D" + key + "=" + value for key, value in flags.items()], "-cp",
               os.pathsep.join(map(str, entries)), MAIN, str(inputs), str(output), refs, str(args.warmups), str(args.measurements), str(config)]
    record = run_windows(command, ROOT / "ipss.test.plugin.core", args.cpu)
    dependencies = [{"path": portable(p), **signature(p)} for p in entries if p.is_file()]
    data = {"source_reference": refs, "affinity": record, "configuration": portable(config), "configuration_signature": signature(config), "classpath_report": portable(report), "classpath_report_signature": signature(report),
            "classpath_entries": [portable(p) for p in entries], "dependency_jars": dependencies,
            "inputs": {p.name: signature(p) for p in inputs.iterdir() if p.is_file() and p.suffix.lower() in [".raw", ".dyr", ".bsg", ".idv"]}}
    (output / "launch.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    summary = json.loads((output / "summary.json").read_text())
    print(json.dumps({"status": "completed", "output": portable(output), "logical_cpu": args.cpu,
                      "warmups": args.warmups, "measurements": args.measurements,
                      "median_outer_seconds": summary["measured_median_outer_seconds"],
                      "outer_range_seconds": summary["measured_outer_range_seconds"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
