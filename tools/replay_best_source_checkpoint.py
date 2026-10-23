"""Rebuild and replay the restored two-case source checkpoint.

Run through tools/run_bounded.py. Compilation is outside the case timer. Default
measurement uses sixteen warmups and five alternating measured pairs; --verify-only
runs two complete optimized simulations without making a warmed timing claim.
"""
import argparse
import json
from pathlib import Path
import shutil
import statistics
import subprocess
import zipfile

from replay_case2_fast_checkpoint import destination, digest, run_single_cpu, verified


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", type=int, choices=[1, 2], required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--cpu", type=int, choices=range(64), default=0,
                        metavar="0..63", help="Logical CPU in the current affinity group; historical default 0")
    parser.add_argument("--compiler-debug", choices=["historical", "none"], default="historical",
                        help="Match the historical -g build; none retains the earlier diagnostic rebuild")
    args = parser.parse_args(argv)
    repo = Path(__file__).resolve().parents[1]
    index_path = repo / "docs/benchmarks/checkpoints/best-source-point-rollback-2026-10-23.json"
    index = json.loads(index_path.read_text())
    manifest_path = verified(repo / index["private_manifest"], index["private_manifest_digest"])
    manifest = json.loads(manifest_path.read_text())
    dependency = manifest["existing_runtime_dependency"]
    old_path = verified(repo / dependency["manifest"], dependency["digest"])
    old = json.loads(old_path.read_text())
    root = (repo / args.output).resolve()
    allowed_roots = [(repo / "target").resolve(),
                     (repo / "ipss.test.plugin.core/target").resolve()]
    output_parent = next((path for path in allowed_roots
                          if root != path and root.is_relative_to(path)), None)
    if root.exists() or output_parent is None:
        parser.error("Output must be a new directory under target or ipss.test.plugin.core/target")
    if shutil.disk_usage(output_parent).free < 3 * 1024**3:
        raise RuntimeError("Need 3 GiB free before the bounded source replay")
    core = repo.parent / "core"
    for checkout, commit, path in [
        (repo, index["plugin_source_commit"], "ipss.plugin.core/src/main/java"),
        (core, index["core_recovery_commit"], "ipss.core_EMF/src/main/java"),
    ]:
        subprocess.run(["git", "-C", str(checkout), "diff", "--exit-code", commit, "--", path], check=True)
        if subprocess.check_output(["git", "-C", str(checkout), "ls-files", "--others",
                                    "--exclude-standard", "--", path], text=True).strip():
            raise RuntimeError("Untracked main sources would change the checkpoint")
    libraries = [str(verified(destination(Path.home() / ".m2/repository", item["maven_repository_path"]),
                              {k: item[k] for k in ("bytes", "sha256")}))
                 for item in old["external_dependencies"]]
    if any(item["maven_repository_path"].startswith("com/interpss/")
           for item in old["external_dependencies"]):
        raise RuntimeError("Main-source compilation must not use archived engine libraries")
    root.mkdir(parents=True)
    outputs = []
    for name, source in [("core", core / "ipss.core_EMF/src/main/java"),
                         ("plugin", repo / "ipss.plugin.core/src/main/java")]:
        files = sorted(source.rglob("*.java"))
        if len(files) != index["main_source_counts"][name]:
            raise RuntimeError("Main source count changed")
        output = root / (name + "-classes")
        output.mkdir()
        # In this JDK, debug information also disables javac lambda deduplication.
        # Preserve the measured compiler mode, not just equivalent source text.
        debug_option = "-g" if args.compiler_debug == "historical" else "-g:none"
        arguments = ["-proc:none", debug_option, "-encoding", "UTF-8", "-cp",
                     ";".join([*map(str, reversed(outputs)), *libraries]), "-d", str(output), *map(str, files)]
        argfile = root / (name + "-javac.args")
        argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in arguments), encoding="utf-8")
        subprocess.run(["javac", "-J-Xmx1g", "@" + str(argfile)], check=True)
        outputs.append(output)
    cp = ";".join([*map(str, reversed(outputs)), *libraries])
    archive = verified(old_path.parent / "restoration-evidence.zip", old["restoration_evidence"])
    with zipfile.ZipFile(archive) as bundle:
        source = bundle.read("source/NumericFrameTimingRun.java").decode("utf-8-sig")
    if args.verify_only:
        source = source.replace("runIndex<26", "runIndex<2").replace("runIndex<16", "runIndex<0")
        start = source.index("            boolean candidate=")
        end = source.index(";", start) + 1
        source = source[:start] + "            boolean candidate=true;" + source[end:]
    harness = root / "harness"
    harness.mkdir()
    runtime = verified(old_path.parent / "runtime.jar", old["runtime"])
    fixture_names = {"BenchmarkOutputGuard", "Texas2kFullNetworkRecorder", "Texas2kSelectedRecorder",
                     "Texas2kNumericalOptions", "Texas2kPowerflowPreparation", "GeneratorFirstShuntLoadflow",
                     "Texas2kInitializationAudit"}
    fixture_count = 0
    with zipfile.ZipFile(runtime) as bundle:
        for info in bundle.infolist():
            if info.is_dir():
                continue
            if info.filename.endswith(".class"):
                package, _, filename = info.filename.rpartition("/")
                top = filename.removesuffix(".class").split("$")[0]
                if not ((package == "org/interpss/core/dstab/reference" and top in fixture_names)
                        or (package == "org/interpss/test" and top == "TestConfig")):
                    continue
                fixture_count += 1
            if info.file_size > 1024**2:
                raise RuntimeError("Unexpected runtime resource budget")
            path = destination(harness, info.filename)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(bundle.read(info))
    if not 7 <= fixture_count <= 64:
        raise RuntimeError("Unexpected benchmark support class inventory")
    java = root / "NumericFrameTimingRun.java"
    java.write_text(source, encoding="utf-8")
    subprocess.run(["javac", "-J-Xmx512m", "-proc:none", "-cp", str(harness) + ";" + cp,
                    "-d", str(harness), str(java)], check=True)
    case = old["cases"][str(args.case)]
    for name, expected in case["inputs"].items():
        target = destination(root, name)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(verified(destination(old_path.parent, name), expected), target)
    (root / "plan.json").write_text(json.dumps(case["plan"], indent=2))
    reference = old_path.parent / "reference-baselines" / f"case{args.case}" / "actual" / case["event_key"] / "run-1"
    for name, expected in case["recordings"].items():
        verified(reference / name, expected)
    command = ["java", *old["jvm_arguments"], "-Dipss.experiment.expectedRecording=" + str(reference),
               "-cp", str(harness) + ";" + cp, "org.interpss.core.dstab.reference.NumericFrameTimingRun",
               str(root / "plan.json"), case["event_key"]]
    (root / "command.json").write_text(json.dumps(command, indent=2))
    run_single_cpu(command, repo, root, args.cpu)
    affinity = json.loads((root / "affinity.json").read_text())
    if (affinity["observed_mask_before"] != 1 << args.cpu
            or affinity["observed_mask_after"] != 1 << args.cpu):
        raise RuntimeError("Requested CPU affinity was not preserved")
    rows = json.loads((root / "warm-statuses.json").read_text())
    count = 2 if args.verify_only else 26
    if len(rows) != count or len({row["pid"] for row in rows}) != 1:
        raise RuntimeError("Incomplete source replay")
    seen = set()
    for i, row in enumerate(rows):
        expected_candidate = True if args.verify_only else ((i & 1) == 1 if i < 16 else (i - 16) % 2 == (1 if ((i - 16) // 2) % 2 == 0 else 0))
        status = row["status"]
        if (row["run"] != i or row["warmup"] != (not args.verify_only and i < 16)
                or row["candidate"] != expected_candidate or status["status"] != "completed"
                or status["stop_s"] != 10 or status["sparse_solver"] != "JAVA_KLU"
                or status["network_voltage_tolerance"] != 1e-5
                or abs(status["step_s"] - 1 / 240) > 1e-15):
            raise RuntimeError("Replay changed the historical protocol or numerical settings")
        for name, expected in case["recordings"].items():
            path = root / "actual" / case["event_key"] / f"run-{i}" / name
            stat = path.stat()
            identity = stat.st_dev, stat.st_ino, expected["sha256"]
            if identity not in seen:
                verified(path, expected)
                seen.add(identity)
    measured = [row for row in rows if not row["warmup"] and row["candidate"]]
    summary = dict(case=args.case, status="completed", complete_recordings_identical=count,
                   verify_only=args.verify_only, performance_benchmark=not args.verify_only,
                   compiler_debug=args.compiler_debug,
                   cpu=args.cpu, affinity_verified=True,
                   times_s=[row["complete_case_wall_s"] for row in measured],
                   median_s=None if args.verify_only else statistics.median(row["complete_case_wall_s"] for row in measured),
                   median_cpu_s=statistics.median(row["process_cpu_s"] for row in measured),
                   median_setup_s=statistics.median(row["status"]["setup_initialization_wall_s"] for row in measured),
                   median_simulation_output_s=statistics.median(row["status"]["simulation_output_wall_s"] for row in measured),
                   measured_compilation_ms=[row["compilation_time_ms"] for row in measured],
                   measured_gc_ms=[row["gc_time_ms"] for row in measured],
                   compiled_main_sources=index["main_source_counts"], archived_main_classes_on_classpath=False,
                   pinned_benchmark_support_classes=fixture_count,
                   independent_reference_accuracy_qualified=False, checkpoint_digest=digest(index_path))
    (root / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
