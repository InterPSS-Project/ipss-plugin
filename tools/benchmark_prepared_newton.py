"""Matched RustPower/public InterPSS benchmark on one verified performance CPU.

Run through run_bounded.py, using the benchmark environment. Build and test the
paired source reactor first. The input classpath must come from that build.
All input preparation, compilation, validation and receipt hashing are outside
the timers. Output directories are append-protected: choose a new one each run.
"""
import argparse
import ctypes
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import rustpower_benchmark as shared

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT.parent / "core"


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def digest(path):
    if path.resolve() in (ROOT.resolve(), CORE.resolve(), ROOT.parent.resolve()):
        raise RuntimeError("Refusing to fingerprint a repository root as a runtime directory")
    if path.is_file():
        return {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}
    tree = hashlib.sha256()
    files = sorted(p for p in path.rglob("*") if p.is_file())
    for file in files:
        tree.update(file.relative_to(path).as_posix().encode())
        tree.update(hashlib.sha256(file.read_bytes()).digest())
    return {"tree_sha256": tree.hexdigest(), "files": len(files), "bytes": sum(p.stat().st_size for p in files)}


class Affinity:
    def __init__(self, requested):
        if os.name != "nt":
            raise RuntimeError("This receipt currently requires Windows processor topology APIs")
        from ctypes import wintypes
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        self.api.GetCurrentProcess.restype = wintypes.HANDLE
        self.api.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
        self.api.SetProcessAffinityMask.restype = wintypes.BOOL
        self.api.GetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(ctypes.c_size_t)]
        self.api.GetProcessAffinityMask.restype = wintypes.BOOL
        self.api.GetLogicalProcessorInformationEx.argtypes = [wintypes.DWORD, ctypes.c_void_p, ctypes.POINTER(wintypes.DWORD)]
        self.api.GetLogicalProcessorInformationEx.restype = wintypes.BOOL
        size = wintypes.DWORD()
        self.api.GetLogicalProcessorInformationEx(0, None, ctypes.byref(size))
        buffer = ctypes.create_string_buffer(size.value)
        if not self.api.GetLogicalProcessorInformationEx(0, buffer, ctypes.byref(size)):
            raise ctypes.WinError(ctypes.get_last_error())
        offset = 0
        self.topology = []
        while offset < size.value:
            _, length = struct.unpack_from("<II", buffer.raw, offset)
            flags, efficiency = struct.unpack_from("<BB", buffer.raw, offset + 8)
            groups = struct.unpack_from("<H", buffer.raw, offset + 30)[0]
            if groups != 1:
                raise RuntimeError("Explicit multi-group handling is required")
            mask, group = struct.unpack_from("<QH", buffer.raw, offset + 32)
            self.topology.append({"efficiency_class": efficiency, "smt": bool(flags & 1), "mask": mask, "group": group})
            offset += length
        allowed = self.get()
        highest = max(c["efficiency_class"] for c in self.topology)
        candidates = [c for c in self.topology if c["efficiency_class"] == highest and c["group"] == 0 and c["mask"] & allowed]
        if requested is None:
            # Prefer the second performance core, leaving the first core's usual
            # system activity alone; selection never depends on measured times.
            selected = candidates[min(1, len(candidates) - 1)]["mask"] & allowed
            self.mask = selected & -selected
        else:
            self.mask = 1 << requested
        if not self.mask & allowed:
            raise RuntimeError("Requested CPU is outside the process affinity mask")
        if not self.api.SetProcessAffinityMask(self.api.GetCurrentProcess(), self.mask):
            raise ctypes.WinError(ctypes.get_last_error())
        self.verify()

    def get(self, handle=None):
        process = ctypes.c_size_t()
        system = ctypes.c_size_t()
        handle = self.api.GetCurrentProcess() if handle is None else handle
        if not self.api.GetProcessAffinityMask(handle, ctypes.byref(process), ctypes.byref(system)):
            raise ctypes.WinError(ctypes.get_last_error())
        return process.value

    def verify(self, handle=None):
        actual = self.get(handle)
        if actual != self.mask:
            raise RuntimeError(f"Affinity changed: expected {self.mask:#x}, observed {actual:#x}")
        return actual

    def run(self, command):
        self.verify()
        start = time.monotonic()
        child = subprocess.Popen(command, stdin=subprocess.DEVNULL)
        observations = 0
        try:
            while child.poll() is None:
                # GetProcessAffinityMask can race with a process that just
                # exited. A terminal exit is checked before accepting that race.
                try:
                    self.verify(child._handle)
                    observations += 1
                except OSError:
                    if child.poll() is None:
                        raise
                time.sleep(.05)
            code = child.wait()
        finally:
            self.verify()
        if code:
            raise RuntimeError(f"Child exited {code}: {command[0:3]}")
        return {"pid": child.pid, "affinity_mask": self.mask, "observations": observations,
                "elapsed_seconds": time.monotonic() - start, "command": command}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--test-report", type=Path, default=ROOT / ".verification-build/prepared-jacobian-20261030/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.aclf.PreparedNewtonLoadflowTest.xml")
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--small-warmup", type=int, default=50000)
    parser.add_argument("--small-repeats", type=int, default=10000)
    parser.add_argument("--large-warmup", type=int, default=50)
    parser.add_argument("--large-repeats", type=int, default=100)
    parser.add_argument("--cpu", type=int)
    parser.add_argument("--include-generic", action="store_true")
    parser.add_argument("--initial-solve-is-warmup", action="store_true",
                        help="Count Java's required initialization solve in the requested warmup count")
    parser.add_argument("--required-ratio", type=float, default=1.2)
    parser.add_argument("--cases", nargs="+", choices=shared.CASES, default=list(shared.STANDARD_CASES))
    parser.add_argument("--case-directory", type=Path, help="Qualified large-case snapshots from benchmark_large_cases.py")
    args = parser.parse_args()
    if args.initial_solve_is_warmup and min(args.small_warmup, args.large_warmup) < 1:
        parser.error("Counting initialization as warmup requires at least one warmup")
    args.output = args.output.resolve()
    if args.output.exists() and any(args.output.iterdir()):
        raise RuntimeError("Choose a new output directory; existing evidence will not be overwritten")
    args.output.mkdir(parents=True, exist_ok=True)
    if any(name in shared.LARGE_CASES for name in args.cases):
        if not all(name in shared.LARGE_CASES for name in args.cases) or args.case_directory is None:
            raise RuntimeError("Run large-case snapshots as a separate cohort with --case-directory")
        for name in args.cases:
            qualification = json.loads((args.case_directory / (name + "-qualification.json")).read_text())
            for filename, sha256 in qualification["qualified_files_sha256"].items():
                if hashlib.sha256((args.case_directory / filename).read_bytes()).hexdigest() != sha256:
                    raise RuntimeError("Qualified input changed: " + filename)
            for suffix in (".m", "-ybus.npz", "-reference.npz", "-model.json", "-bus-mapping.csv"):
                shutil.copy2(args.case_directory / (name + suffix), args.output / (name + suffix))
            shutil.copy2(args.case_directory / (name + "-qualification.json"), args.output / (name + "-qualification.json"))
            start_file = args.case_directory / (name + "-start.csv")
            if start_file.exists():
                shutil.copy2(start_file, args.output / start_file.name)
    affinity = Affinity(args.cpu)
    test_cp = next(p.attrib["value"] for p in ET.parse(args.test_report).getroot().findall("properties/property")
                   if p.attrib["name"] == "java.class.path")
    entries = [p.strip() for p in test_cp.split(os.pathsep) if p.strip()]
    excluded = [p for p in entries if Path(p).name == "test-classes" or p.endswith("-tests.jar")]
    cp = os.pathsep.join(p for p in entries if p not in excluded)
    (args.output / "classpath.txt").write_text(cp)
    classes = args.output / "runner-classes"
    classes.mkdir()
    source = ROOT / "tools/benchmarks/PreparedNewtonApiBenchmark.java"
    affinity.run(["javac", "-proc:none", "-J-Xmx512m", "-cp", cp, "-d", str(classes), str(source)])
    config = args.output / "log4j2.xml"
    config.write_text('<Configuration status="OFF"><Loggers><Root level="OFF"/></Loggers></Configuration>')
    environment = {"settings": {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()},
                   "cpu_topology": affinity.topology, "affinity_mask": affinity.mask,
                   "python": sys.version, "packages": {p: importlib.metadata.version(p) for p in
                       ("rustpower", "pandapower", "numpy", "scipy", "numba")},
                   "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
                   "excluded_test_classpath": excluded,
                   "runtime": {p: digest(Path(p)) for p in cp.split(os.pathsep)},
                   "source_commits": {}, "processes": []}
    for label, repo in (("core", CORE), ("plugin", ROOT)):
        environment["source_commits"][label] = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
    dump(args.output / "environment.json", environment)
    core_sources = [CORE / ("ipss.core_EMF/src/main/java/com/interpss/core/" + p) for p in (
        "algo/LoadflowAlgorithm.java", "algo/INrSolver.java", "algo/impl/LoadflowAlgorithmImpl.java",
        "algo/impl/solver/DefaultNrSolver.java", "algo/impl/solver/PreparedPolarJacobian.java",
        "algo/impl/solver/FixedPatternLu.java", "algo/impl/calc/LfCalcMismatchHelper.java",
        "algo/impl/calc/LfCalcIterOrchestrator.java",
        "aclf/IAclfBusFunc.java", "aclf/impl/BaseAclfBusImpl.java", "algo/impl/calc/LfCalcNrEngine.java", "algo/impl/LfAdjustAlgorithmImpl.java",
        "algo/impl/calc/LfCalcHvdcOuterController.java", "sparse/impl/klu/KLUSparseEqnMatrix2x2Impl.java",
        "sparse/solver/klu/BaseKLUEqnDoubleSolver.java",
        "sparse/solver/klu/KLUSquareMatrixEqnDoubleSolver.java",
        "aclf/IAclfBusFunc.java", "aclf/impl/BaseAclfBusImpl.java")]
    for file in core_sources + [Path(__file__), source, ROOT / "tools/rustpower_benchmark.py",
            ROOT / "tools/benchmark_large_cases.py", ROOT / "tools/benchmarks/LargePowerflowCaseExporter.java"]:
        destination = args.output / "sources" / ("core" if file in core_sources else "plugin") / file.relative_to(CORE if file in core_sources else ROOT)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, destination)
    for fork in range(args.forks):
        phases = ["external", "prepared"] if fork % 2 == 0 else ["prepared", "external"]
        if args.include_generic:
            phases.append("generic")
        for phase in phases:
            print(f"Starting fork {fork} {phase}, affinity {affinity.mask:#x}", flush=True)
            if phase == "external":
                command = [sys.executable, "-u", str(ROOT / "tools/rustpower_benchmark.py"), "--phase", "external",
                           "--output", str(args.output), "--fork", str(fork), "--small-warmup", str(args.small_warmup),
                           "--small-repeats", str(args.small_repeats), "--large-warmup", str(args.large_warmup),
                           "--large-repeats", str(args.large_repeats), "--cases", *args.cases]
            else:
                command = ["java", "-Xms256m", "-Xmx2g", "-Dlog4j2.configurationFile=" + str(config),
                           "-Dipss.benchmark.initialSolveIsWarmup=" + str(args.initial_solve_is_warmup).lower(),
                           "-cp", str(classes) + os.pathsep + cp, "org.interpss.benchmark.PreparedNewtonApiBenchmark",
                           str(args.output), phase, str(fork), str(args.small_warmup), str(args.small_repeats),
                           str(args.large_warmup), str(args.large_repeats), *args.cases]
            environment["processes"].append(affinity.run(command))
            dump(args.output / "environment.json", environment)
    shared.summarize(argparse.Namespace(output=args.output, cases=args.cases))
    validation = json.loads((args.output / "interpss-validation.json").read_text())
    if max(v["max_complex_voltage_error_pu"] for v in validation.values()) > 1e-7:
        raise RuntimeError("Voltage agreement exceeded the qualification threshold")
    summary = json.loads((args.output / "summary.json").read_text())
    lookup = {(r["case"], r["backend"]): r for r in summary}
    ratios = {}
    for name in args.cases:
        rust = lookup[(name, "rustpower-klu")]
        prepared = lookup[(name, "prepared")]
        expected = args.forks * (args.large_repeats if name in ("case9241pegase", *shared.LARGE_CASES) else args.small_repeats)
        if rust["samples"] != expected or prepared["samples"] != expected:
            raise RuntimeError("Incomplete repetitions")
        ratios[name] = {"mean_ratio": prepared["mean_ms"] / rust["mean_ms"],
                        "per_fork_ratios": [p["mean_ms"] / r["mean_ms"] for p, r in zip(prepared["forks"], rust["forks"])]}
    receipt = {"required_ratio": args.required_ratio, "cases": ratios,
               "target_met": all(r["mean_ratio"] <= args.required_ratio for r in ratios.values()),
               "affinity_before_and_after": affinity.verify()}
    dump(args.output / "target-verification.json", receipt)
    files = sorted(p for p in args.output.rglob("*") if p.is_file())
    dump(args.output / "sha256.json", {p.relative_to(args.output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    print(json.dumps(receipt, indent=2), flush=True)
    return 0 if receipt["target_met"] else 2


if __name__ == "__main__":
    sys.exit(main())
