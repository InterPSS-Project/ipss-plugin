"""Replay the frozen Case 2 performance baseline without mutable build directories.

Run through tools/run_bounded.py. The private executable archive, retained inputs,
and matching local Maven dependencies are required. This is a performance
baseline, not an independent-reference accuracy qualification or a source build.
"""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import statistics
import subprocess
import zipfile


def digest(path: Path) -> dict:
    with path.open("rb") as stream:
        return {"bytes": path.stat().st_size,
                "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}


def verified(path: Path, expected: dict) -> Path:
    if digest(path) != expected:
        raise ValueError(f"Checkpoint dependency differs: {path.name}")
    return path


def destination(root: Path, relative: str) -> Path:
    parts = PurePosixPath(relative)
    if parts.is_absolute() or ".." in parts.parts or "\\" in relative or ":" in relative:
        raise ValueError("Invalid checkpoint member path")
    result = root.joinpath(*parts.parts).resolve()
    if not result.is_relative_to(root.resolve()):
        raise ValueError("Checkpoint member escapes destination")
    return result


def install_overlay(repo: Path, root: Path, relative_index: str, base_archive: dict,
                    parent_archive: dict | None = None) -> tuple[dict, dict, int]:
    index_path = repo / relative_index
    index = json.loads(index_path.read_text())
    manifest_path = verified(repo / index["private_manifest"], index["private_manifest_digest"])
    manifest = json.loads(manifest_path.read_text())
    archive = verified(manifest_path.parent / "overlay.zip", index["archive"])
    if manifest["base_archive"] != base_archive:
        raise RuntimeError("Overlay requires a different base archive")
    if parent_archive is not None and manifest.get("parent_overlay_archive") != parent_archive:
        raise RuntimeError("Overlay requires a different parent overlay")
    with zipfile.ZipFile(archive) as bundle:
        members = [i for i in bundle.infolist() if i.filename.startswith("runtime/") and not i.is_dir()]
        if sum(i.file_size for i in members) > 4 * 1024**2:
            raise RuntimeError("Overlay exceeds its 4 MiB runtime budget")
        if len(members) != len(manifest["runtime_files"]) or {i.filename for i in members} != set(manifest["runtime_files"]):
            raise RuntimeError("Incomplete or duplicate overlay entries")
        for member in members:
            expected = manifest["runtime_files"][member.filename]
            payload = bundle.read(member)
            if len(payload) != expected["bytes"] or hashlib.sha256(payload).hexdigest() != expected["sha256"]:
                raise ValueError("Overlay class differs from manifest")
            if not member.filename.endswith(".class"):
                raise ValueError("Only classes may replace frozen runtime entries")
            target = destination(root, member.filename)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
    count = sum(1 for _ in (root / "runtime").rglob("*.class"))
    if count != index["frozen_directory_classes"]:
        raise RuntimeError("Unexpected overlay runtime class count")
    record = dict(index=relative_index, index_digest=digest(index_path),
                  manifest_digest=digest(manifest_path), archive_digest=digest(archive))
    return index, record, count


def run_single_cpu(command: list[str], repo: Path, root: Path,
                   cpu_index: int | None = None) -> None:
    if cpu_index is not None and not 0 <= cpu_index < ctypes.sizeof(ctypes.c_size_t) * 8:
        raise ValueError("CPU index must fit the current affinity group")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
    kernel.SetProcessAffinityMask.restype = wintypes.BOOL
    kernel.GetProcessAffinityMask.argtypes = [wintypes.HANDLE,
        ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(ctypes.c_size_t)]
    kernel.GetProcessAffinityMask.restype = wintypes.BOOL

    def affinity(handle):
        mask, system = ctypes.c_size_t(), ctypes.c_size_t()
        if not kernel.GetProcessAffinityMask(handle, ctypes.byref(mask), ctypes.byref(system)):
            raise ctypes.WinError(ctypes.get_last_error())
        return mask.value, system.value

    process = subprocess.Popen(command, cwd=repo)
    handle = wintypes.HANDLE(int(process._handle))
    try:
        allowed, system = affinity(handle)
        selected = (allowed & -allowed) if cpu_index is None else 1 << cpu_index
        if not selected & allowed:
            raise RuntimeError("Requested CPU is outside the allowed process affinity")
        if not kernel.SetProcessAffinityMask(handle, selected):
            raise ctypes.WinError(ctypes.get_last_error())
        observed, _ = affinity(handle)
        if observed != selected or observed.bit_count() != 1:
            raise RuntimeError("Single-CPU affinity not established")
        record = dict(pid=process.pid, requested_mask=selected,
                      cpu_index=selected.bit_length() - 1,
                      observed_mask_before=observed, system_mask=system)
        (root / "affinity.json").write_text(json.dumps(record, indent=2))
        (root / "affinity-ready").write_text("Single logical processor verified.\n")
        process.wait()
        observed, _ = affinity(handle)
        record.update(observed_mask_after=observed, exit_code=process.returncode)
        (root / "affinity.json").write_text(json.dumps(record, indent=2))
        if process.returncode or observed != selected:
            raise RuntimeError("Benchmark failed or CPU affinity changed")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True,
                        help="New output directory inside this repository's target directory")
    parser.add_argument("--machine-frame-reuse", action="store_true",
                        help="Replay the retained machine-frame variant with 24 warmups and 7 measurements")
    parser.add_argument("--electrical-endpoint", action="store_true",
                        help="Include machine-frame reuse and endpoint preparation, with 32 warmups and 7 measurements")
    parser.add_argument("--selected", action="store_true",
                        help="Replay the version selected in the working performance baseline index")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    if args.selected:
        if args.machine_frame_reuse or args.electrical_endpoint:
            parser.error("--selected cannot be combined with explicit version flags")
        selected_path = repo / "docs/benchmarks/checkpoints/case2-working-performance-baseline.json"
        selection = json.loads(selected_path.read_text())
        selected = selection["selected_checkpoint"]
        if selected == "docs/benchmarks/checkpoints/best-source-point-rollback-2026-10-23.json":
            from replay_best_source_checkpoint import main as replay_source
            replay_source([*selection.get("replay_args", ["--case", "2"]),
                           "--output", str(args.output)])
            return
        known = {
            "docs/benchmarks/checkpoints/case2-recovered-fast-2026-10-21.json": (False, False),
            "docs/benchmarks/checkpoints/case2-frozen-machine-frame-2026-10-21.json": (True, False),
            "docs/benchmarks/checkpoints/case2-frozen-endpoint-2026-10-21.json": (True, True),
        }
        if selected not in known:
            parser.error("Selected checkpoint is not supported by this replay tool")
        args.machine_frame_reuse, args.electrical_endpoint = known[selected]
    use_machine_frame = args.machine_frame_reuse or args.electrical_endpoint
    if os.name != "nt":
        parser.error("This checkpoint runner requires Windows CPU-affinity verification")
    root = (repo / args.output).resolve()
    if not root.is_relative_to((repo / "target").resolve()) or root.exists():
        parser.error("Output must be a new directory below target")
    if shutil.disk_usage(repo).free < 3 * 1024**3:
        raise RuntimeError("Need 3 GiB free before staging the bounded checkpoint replay")
    index_path = repo / "docs/benchmarks/checkpoints/case2-recovered-fast-2026-10-21.json"
    index = json.loads(index_path.read_text())
    manifest_path = verified(repo / index["private_manifest"], index["private_manifest_digest"])
    manifest = json.loads(manifest_path.read_text())
    archive = verified(manifest_path.parent / "executable-snapshot.zip", index["archive"])
    dependencies = []
    for item in manifest["external_dependencies"]:
        path = destination(Path.home() / ".m2/repository", item["maven_repository_path"])
        dependencies.append(str(verified(path, {k: item[k] for k in ("bytes", "sha256")})))
    root.mkdir(parents=True)
    runtime = root / "runtime"
    archive_base = "target/retained-fast-stack-replay-v2-20261021/"
    runtime_count = 0
    with zipfile.ZipFile(archive) as bundle:
        members = [i for i in bundle.infolist() if i.filename.startswith("runtime/") and not i.is_dir()]
        if sum(i.file_size for i in members) > 256 * 1024**2:
            raise RuntimeError("Checkpoint runtime exceeds the reviewed 256 MiB staging budget")
        for member in members:
            expected = manifest["files"][member.filename]
            payload = bundle.read(member)
            if len(payload) != expected["bytes"] or hashlib.sha256(payload).hexdigest() != expected["sha256"]:
                raise ValueError("Archived runtime entry differs from manifest")
            target = destination(root, member.filename)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            runtime_count += member.filename.endswith(".class")
        for name in ("command.json", "plan.json"):
            key = archive_base + name
            payload = bundle.read(key)
            if hashlib.sha256(payload).hexdigest() != manifest["files"][key]["sha256"]:
                raise ValueError("Archived replay configuration differs")
            (root / ("original-command.json" if name == "command.json" else name)).write_bytes(payload)
    input_count = 0
    for relative, expected in manifest["retained_dependencies"].items():
        if not relative.startswith(archive_base + "inputs/"):
            continue
        source = verified(destination(repo, relative), expected)
        target = destination(root, relative[len(archive_base):])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        input_count += 1
    if runtime_count != index["summary"]["frozen_directory_classes"] or input_count != 4:
        raise RuntimeError("Incomplete checkpoint runtime or case inputs")
    overlay_record = endpoint_record = None
    warmup_runs, measured_runs = 12, 5
    if use_machine_frame:
        overlay_index, overlay_record, runtime_count = install_overlay(repo, root,
            "docs/benchmarks/checkpoints/case2-frozen-machine-frame-2026-10-21.json", index["archive"])
        warmup_runs, measured_runs = 24, 7
    if args.electrical_endpoint:
        _, endpoint_record, runtime_count = install_overlay(repo, root,
            "docs/benchmarks/checkpoints/case2-frozen-endpoint-2026-10-21.json", index["archive"], overlay_index["archive"])
        warmup_runs, measured_runs = 32, 7
    command = json.loads((root / "original-command.json").read_text())
    command[command.index("-cp") + 1] = ";".join([str(runtime), *dependencies])
    command[-2] = str(root / "plan.json")
    if use_machine_frame:
        command[command.index("-Dipss.experiment.primitiveCurrentHandoff=true")] = "-Dipss.experiment.primitiveCurrentHandoff=false"
        command[1:1] = ["-Dipss.dstab.disableCurrentArrays=true", "-Dipss.benchmark.frameReuse=true"]
    (root / "command.json").write_text(json.dumps(command, indent=2))
    (root / "checkpoint.json").write_text(json.dumps(dict(
        checkpoint=index_path.relative_to(repo).as_posix(), checkpoint_digest=digest(index_path),
        private_manifest=index["private_manifest"], private_manifest_digest=index["private_manifest_digest"],
        archive_digest=index["archive"], frozen_directory_classes=runtime_count,
        mutable_build_directories_on_classpath=False, source_checkout=False,
        machine_frame_overlay=overlay_record, electrical_endpoint_overlay=endpoint_record), indent=2))
    run_single_cpu(command, repo, root)
    runs = json.loads((root / "warm-statuses.json").read_text())
    if len(runs) != warmup_runs + measured_runs or len({r["pid"] for r in runs}) != 1:
        raise RuntimeError("Unexpected repetition count or JVM identity")
    expected_hash = index["summary"]["trace_sha256"]
    seen = set()
    for run_index, row in enumerate(runs):
        if row["run"] != run_index or row["warmup"] != (run_index < warmup_runs):
            raise RuntimeError("Unexpected warmup or measurement assignment")
        status = row["status"]
        if (status["status"] != "completed" or status["stop_s"] != 10
                or status["network_voltage_tolerance"] != 1e-5
                or abs(status["step_s"] - 1 / 240) >= 1e-15):
            raise RuntimeError("Replay did not retain requested simulation settings")
        if use_machine_frame and (status["buffered_current_solve_count"] != 0
                                         or status["frame_reuses"] <= 0):
            raise RuntimeError("Machine-frame replay did not use its declared execution paths")
        if args.electrical_endpoint and status["electrical_endpoint_preparations"] != 211376:
            raise RuntimeError("Endpoint replay did not use its declared preparation path")
        path = root / "actual/case2_bus_fault_01" / f"run-{row['run']}" / "full-network.bin"
        stat = path.stat()
        key = stat.st_dev, stat.st_ino
        if key not in seen:
            if digest(path)["sha256"] != expected_hash:
                raise RuntimeError("Complete channel recording differs from checkpoint")
            seen.add(key)
    times = [r["complete_case_wall_s"] for r in runs if not r["warmup"]]
    if len(times) != measured_runs:
        raise RuntimeError("Unexpected number of measured repetitions")
    selected_path = repo / "docs/benchmarks/checkpoints/case2-working-performance-baseline.json"
    replayed_index = ("docs/benchmarks/checkpoints/case2-frozen-endpoint-2026-10-21.json" if args.electrical_endpoint
                      else "docs/benchmarks/checkpoints/case2-frozen-machine-frame-2026-10-21.json"
                      if args.machine_frame_reuse else index_path.relative_to(repo).as_posix())
    selected = (selected_path.exists()
                and json.loads(selected_path.read_text()).get("selected_checkpoint") == replayed_index)
    summary = dict(status="completed", working_performance_baseline=selected,
        warmup_runs=warmup_runs, measured_runs=measured_runs, case_wall_s=times,
        median_case_wall_s=statistics.median(times), single_cpu_verified=True,
        full_recording_included=True, trace_sha256=expected_hash,
        frozen_directory_classes=runtime_count, mutable_build_directories_on_classpath=False,
        exact_historical_checkout=False, independent_accuracy_qualified=False,
        machine_frame_reuse=use_machine_frame, electrical_endpoint_preparation=args.electrical_endpoint)
    (root / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
