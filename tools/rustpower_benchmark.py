"""Compare InterPSS AC load flow with the cases in RustPower's published table.

Run through tools/run_bounded.py. Dependencies: pandapower 3.5.4, rustpower
0.5.2, numba. Preparation and correctness checks are
outside the timers. Every timed solve resets the voltages to a compensated
flat start and must perform at least one Newton iteration.
"""
import argparse
import csv
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
import warnings

ROOT = Path(__file__).resolve().parents[1]
STANDARD_CASES = ("case39", "case118", "case9241pegase")
LARGE_CASES = ("activsg25k", "activsg70k")
CASES = STANDARD_CASES + LARGE_CASES
TOL = 1e-6


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def stats(values):
    import numpy as np
    return {"samples": len(values), "mean_ms": float(np.mean(values)),
            "median_ms": float(np.median(values)), "min_ms": float(np.min(values)),
            "p95_ms": float(np.percentile(values, 95)), "max_ms": float(np.max(values))}


def external(args):
    if all(name in LARGE_CASES for name in args.cases):
        return external_snapshots(args)
    import numpy as np
    import pandapower as pp
    import pandapower.networks as pn
    import rustpower as rp
    from rustpower.solver import NewtonSolver
    from scipy.sparse import save_npz
    warnings.filterwarnings("ignore", category=FutureWarning)
    if not {"klu"}.issubset(rp.features()):
        raise RuntimeError("The published benchmark requires the RustPower KLU feature")
    pp.runpp(pn.case39(), numba=True, lightsim2grid=False)
    rows = []
    validation = {}
    for name in args.cases:
        net = getattr(pn, name)()
        pp.runpp(net, algorithm="nr", init="flat", numba=True, lightsim2grid=False,
                 tolerance_mva=1e-6, max_iteration=20, enforce_q_lims=False)
        ppc = net._ppc
        internal = ppc["internal"]
        ybus = internal["Ybus"].tocsr()
        sbus = internal["Sbus"].astype(np.complex128)
        pq, pv, ref = (internal[x] for x in ("pq", "pv", "ref"))
        v0 = np.ones(len(sbus), dtype=np.complex128)
        for _, row in net.ext_grid.iterrows():
            v0[int(row.bus)] = row.vm_pu
        for _, row in net.gen.iterrows():
            v0[int(row.bus)] = row.vm_pu
        p = np.concatenate([pq, pv, ref]).astype(np.int64)
        inv = np.empty_like(p)
        inv[p] = np.arange(len(p))
        reference = internal["V"].copy()
        if len(ref) != 1:
            raise RuntimeError("This benchmark expects exactly one reference bus")
        # The upstream compensated flat start uses a zero reference angle.
        reference *= np.exp(-1j * np.angle(reference[ref[0]]))
        # Preserve the exact nodal equations. Fixed branch-end charging and
        # conductance can be represented by equivalent bus shunts at fixed taps.
        # This also retains pandapower's transformer T-equivalent terms.
        from pandapower.pypower.idx_brch import BR_G, BR_G_ASYM, BR_B_ASYM, BR_R_ASYM, BR_X_ASYM
        for column in (BR_R_ASYM, BR_X_ASYM):
            if np.max(np.abs(ppc["branch"][:, column])) > 1e-14:
                raise RuntimeError(f"{name}: nonstandard branch terms in column {column}")
        export_bus = ppc["bus"].copy()
        export_branch = ppc["branch"].copy()
        export_gen = ppc["gen"].copy()
        # Missing machine ratings do not affect static PF; use the system base.
        export_gen[~np.isfinite(export_gen[:, 6]) | (export_gen[:, 6] <= 0), 6] = ppc["baseMVA"]
        for branch in export_branch:
            if not branch[10]:
                continue
            tap = branch[8] if branch[8] else 1.0
            yf = complex(branch[BR_G], branch[4]) / (2 * tap * tap)
            yt = complex(branch[BR_G] + branch[BR_G_ASYM], branch[4] + branch[BR_B_ASYM]) / 2
            for bus, shunt in ((int(branch[0]), yf), (int(branch[1]), yt)):
                export_bus[bus, 4] += ppc["baseMVA"] * shunt.real
                export_bus[bus, 5] += ppc["baseMVA"] * shunt.imag
        export_branch[:, [4, BR_G, BR_G_ASYM, BR_B_ASYM]] = 0
        export_branch[(export_branch[:, 8] == 1) & (export_branch[:, 9] == 0), 8] = 0
        from pandapower.pypower.makeYbus import makeYbus
        exported_y, _, _ = makeYbus(ppc["baseMVA"], export_bus, export_branch)
        delta_y = exported_y - ybus
        export_error = float(np.max(np.abs(delta_y.data))) if delta_y.nnz else 0.0
        if export_error > 1e-9:
            raise RuntimeError(f"{name}: export changed Ybus by {export_error}")
        if args.fork == 0:
            with (args.output / (name + ".m")).open("w", encoding="ascii") as out:
                out.write(f"function mpc = {name}\nmpc.version = '2';\nmpc.baseMVA = {ppc['baseMVA']:.17g};\n")
                for key, columns in (("bus", 13), ("gen", 21), ("branch", 13)):
                    out.write(f"mpc.{key} = [\n")
                    table = {"bus": export_bus, "gen": export_gen, "branch": export_branch}[key]
                    if not np.isfinite(table[:, :columns]).all():
                        raise RuntimeError(f"{name}: nonfinite exported {key} data")
                    np.savetxt(out, np.real(table[:, :columns]), fmt="%.17g", delimiter=" ", newline=";\n")
                    out.write("];\n")
            save_npz(args.output / (name + "-ybus.npz"), ybus)
            np.savez(args.output / (name + "-reference.npz"), v=reference, s=sbus,
                     pq=pq, pv=pv, ref=ref, v0=v0, bus_ids=ppc["bus"][:, 0].astype(int))
        solver = NewtonSolver()
        solver.setup_context(ybus.indptr, ybus.indices, ybus.data, sbus, v0,
                             p, inv, len(pv), len(pq))
        warmups, repeats = ((args.large_warmup, args.large_repeats) if len(sbus) > 1000
                            else (args.small_warmup, args.small_repeats))
        for backend in ("rustpower-klu",):
            samples, iterations = [], []
            for i in range(-warmups, repeats):
                begin = time.perf_counter_ns()
                solver.set_v_init(v0)
                solver.solve(20, TOL)
                _ = solver.get_scalc()  # Included by the upstream script.
                elapsed = (time.perf_counter_ns() - begin) / 1e6
                success = solver.converged
                count = solver.get_iterations()
                if not success or count < 1:
                    raise RuntimeError(f"{name} {backend}: failed or zero-iteration solve")
                if i >= 0:
                    rows.append({"case": name, "backend": backend, "fork": args.fork,
                                 "repeat": i, "elapsed_ms": elapsed, "iterations": count})
                    samples.append(elapsed)
                    iterations.append(count)
            voltage = np.asarray(solver.get_voltage())
            mis = voltage * np.conj(ybus @ voltage) - sbus
            residual = float(np.max(np.abs(np.r_[mis[np.r_[pv, pq]].real, mis[pq].imag])))
            error = float(np.max(np.abs(voltage - reference)))
            if residual > 1.01 * TOL or error > 2e-5:
                raise RuntimeError(f"{name} {backend}: residual={residual}, voltage error={error}")
            validation[name + "/" + backend] = {"residual_pu": residual,
                    "max_complex_voltage_error_pu": error, "iterations": sorted(set(iterations))}
            print(name, backend, stats(samples), flush=True)
        validation[name + "/model"] = {"buses": len(sbus), "branches": len(ppc["branch"]),
                "pv": len(pv), "pq": len(pq), "ref": len(ref), "base_mva": ppc["baseMVA"],
                "export_ybus_max_error": export_error,
                "pandapower_iterations_at_1e6_mva": int(ppc["iterations"])}
    with (args.output / f"external-{args.fork}.csv").open("w", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    dump(args.output / f"external-validation-{args.fork}.json", validation)


def external_snapshots(args):
    """Use already qualified fixed-control nodal snapshots, without importing a converter."""
    import numpy as np
    import rustpower as rp
    from rustpower.solver import NewtonSolver
    from scipy.sparse import load_npz
    if "klu" not in rp.features():
        raise RuntimeError("RustPower KLU feature required")
    rows, validation = [], {}
    for name in args.cases:
        y = load_npz(args.output / (name + "-ybus.npz"))
        data = np.load(args.output / (name + "-reference.npz"))
        s, v0 = data["s"], data["v0"]
        pq, pv, ref = (data[k] for k in ("pq", "pv", "ref"))
        p = np.r_[pq, pv, ref].astype(np.int64)
        inv = np.empty_like(p)
        inv[p] = np.arange(len(p))
        solver = NewtonSolver()
        solver.setup_context(y.indptr, y.indices, y.data, s, v0, p, inv, len(pv), len(pq))
        counts, samples = [], []
        for i in range(-args.large_warmup, args.large_repeats):
            begin = time.perf_counter_ns()
            solver.set_v_init(v0)
            solver.solve(20, TOL)
            _ = solver.get_scalc()
            elapsed = (time.perf_counter_ns() - begin) / 1e6
            count = solver.get_iterations()
            if not solver.converged or count < 1:
                raise RuntimeError(f"{name}: failed or zero-iteration RustPower solve")
            if i >= 0:
                rows.append(dict(case=name, backend="rustpower-klu", fork=args.fork,
                                 repeat=i, elapsed_ms=elapsed, iterations=count))
                counts.append(count)
                samples.append(elapsed)
        v = np.asarray(solver.get_voltage())
        mis = v * np.conj(y @ v) - s
        residual = float(np.max(abs(np.r_[mis[np.r_[pv, pq]].real, mis[pq].imag])))
        error = float(np.max(abs(v - data["v"])))
        validation[name + "/rustpower-klu"] = dict(residual_pu=residual,
                max_complex_voltage_error_pu=error, iterations=sorted(set(counts)),
                warmup_solves=args.large_warmup, measured_solves=args.large_repeats)
        if residual > 1.01 * TOL or error > 1e-7:
            raise RuntimeError(f"{name}: residual={residual}, voltage error={error}")
        print(name, "rustpower-klu", stats(samples), flush=True)
    with (args.output / f"external-{args.fork}.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    dump(args.output / f"external-validation-{args.fork}.json", validation)


def java(args):
    cp = args.classpath.read_text().strip()
    classes = args.output / "classes"
    classes.mkdir(exist_ok=True)
    sources = [ROOT / "tools/benchmarks/RustPowerLoadflowBenchmark.java",
               ROOT / "ipss.plugin.core/src/main/java/org/interpss/fadapter/matpower/MatpowerDirectParser.java",
               ROOT / "ipss.plugin.core/src/main/java/org/interpss/fadapter/builder/AclfNetworkBuilder.java"]
    subprocess.run(["javac", "-proc:none", "-J-Xmx512m", "-cp", cp, "-d", str(classes), *map(str, sources)], check=True)
    log_config = args.output / "log4j2.xml"
    log_config.write_text('<Configuration status="OFF"><Appenders><Console name="Console"><PatternLayout '
                         'pattern="%level %logger{1}: %msg%n"/></Console></Appenders><Loggers>'
                         '<Root level="WARN"><AppenderRef ref="Console"/></Root></Loggers></Configuration>')
    for backend in ("java-klu", "csj"):
        subprocess.run(["java", "-Xms256m", "-Xmx2g", "-Dlog4j2.configurationFile=" + str(log_config),
                        "-cp", str(classes) + os.pathsep + cp, "org.interpss.benchmark.RustPowerLoadflowBenchmark",
                        str(args.output), backend, str(args.fork), str(args.small_warmup),
                        str(args.small_repeats), str(args.large_warmup), str(args.large_repeats), *args.cases], check=True)


def summarize(args):
    import numpy as np
    from scipy.sparse import load_npz
    groups = {}
    for pattern in ("external-*.csv", "interpss-*.csv"):
        for path in sorted(args.output.glob(pattern)):
            for row in csv.DictReader(path.open()):
                groups.setdefault((row["case"], row["backend"]), []).append(row)
    summary = []
    validation = {}
    for (name, backend), rows in groups.items():
        forks = sorted({int(r["fork"]) for r in rows})
        summary.append({"case": name, "backend": backend,
                        **stats([float(r["elapsed_ms"]) for r in rows]),
                        "iterations": sorted({int(r["iterations"]) for r in rows}),
                        "forks": [{"fork": f, **stats([float(r["elapsed_ms"]) for r in rows
                                                     if int(r["fork"]) == f])} for f in forks]})
    for name in args.cases:
        ref = np.load(args.output / (name + "-reference.npz"))
        ybus = load_npz(args.output / (name + "-ybus.npz"))
        for path in sorted(args.output.glob(name + "-interpss-*-voltage.csv")):
            table = np.genfromtxt(path, delimiter=",", names=True)
            if not np.array_equal(table["bus"].astype(int), ref["bus_ids"]):
                raise RuntimeError("Bus ordering mismatch: " + str(path))
            v = table["vm_pu"] * np.exp(1j * table["va_rad"])
            mis = v * np.conj(ybus @ v) - ref["s"]
            residual = float(np.max(np.abs(np.r_[mis[np.r_[ref["pv"], ref["pq"]]].real, mis[ref["pq"]].imag])))
            error = float(np.max(np.abs(v - ref["v"])))
            validation[path.name] = {"independent_mismatch_pu": residual,
                                     "max_complex_voltage_error_pu": error}
            if residual > 1.01 * TOL or error > 2e-5:
                dump(args.output / "interpss-validation.json", validation)
                raise RuntimeError(f"{path.name}: independent residual={residual}, voltage error={error}")
    dump(args.output / "interpss-validation.json", validation)
    dump(args.output / "summary.json", summary)
    for row in summary:
        print(row["case"], row["backend"], "mean_ms", row["mean_ms"], "iterations", row["iterations"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--classpath", type=Path, default=ROOT / "ipss.test.plugin.core/target/rustpower-classpath-20261030.txt")
    parser.add_argument("--phase", choices=("all", "external", "java", "summarize"), default="all")
    parser.add_argument("--fork", type=int, default=0)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--small-warmup", type=int, default=300)
    parser.add_argument("--small-repeats", type=int, default=1000)
    parser.add_argument("--large-warmup", type=int, default=30)
    parser.add_argument("--large-repeats", type=int, default=60)
    parser.add_argument("--cases", nargs="+", choices=CASES, default=list(STANDARD_CASES))
    args = parser.parse_args()
    if any(name in LARGE_CASES for name in args.cases):
        if not all(name in LARGE_CASES for name in args.cases):
            parser.error("Run standard and large-case snapshots as separate cohorts")
        if args.phase not in ("external", "summarize"):
            parser.error("Use benchmark_prepared_newton.py for qualified large-case Java comparisons")
    args.output = args.output.resolve()
    args.output.mkdir(exist_ok=True, parents=True)
    if args.phase != "all":
        globals()[args.phase](args)
        return
    environment = {"python": sys.version, "platform": platform.platform(), "processor": platform.processor(),
                   "packages": {name: importlib.metadata.version(name) for name in
                                ("rustpower", "pandapower", "numpy", "scipy", "numba")},
                   "settings": {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()},
                   "plugin_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                   "worktree_status": subprocess.check_output(["git", "status", "--short"], cwd=ROOT, text=True),
                   "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr}
    if sys.platform == "win32":
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"HARDWARE\DESCRIPTION\System\CentralProcessor\0") as key:
            environment["cpu_name"] = winreg.QueryValueEx(key, "ProcessorNameString")[0].strip()
    environment["rustpower_features"] = __import__("rustpower").features()
    environment["runtime_jar_sha256"] = {p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
                                          for p in args.classpath.read_text().strip().split(os.pathsep)}
    (args.output / "requirements-freeze.txt").write_text(subprocess.check_output(
        [sys.executable, "-m", "pip", "freeze"], text=True), encoding="utf-8")
    dump(args.output / "environment.json", environment)
    for fork in range(args.forks):
        common = ["--output", str(args.output), "--classpath", str(args.classpath), "--fork", str(fork),
                  "--small-warmup", str(args.small_warmup), "--small-repeats", str(args.small_repeats),
                  "--large-warmup", str(args.large_warmup), "--large-repeats", str(args.large_repeats),
                  "--cases", *args.cases]
        for phase in ("external", "java"):
            subprocess.run([sys.executable, str(Path(__file__).resolve()), "--phase", phase, *common], check=True)
    summarize(args)
    paths = [p for p in args.output.iterdir() if p.is_file() and p.name != "sha256.json"]
    paths += [Path(__file__), ROOT / "tools/benchmarks/RustPowerLoadflowBenchmark.java", args.classpath]
    paths += list((args.output / "classes").rglob("*.class"))
    dump(args.output / "sha256.json", {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths})


if __name__ == "__main__":
    main()
