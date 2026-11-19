"""Prepare and independently qualify fixed-control large-case nodal benchmarks.

The Java exporter initializes the original input. Canonical MATPOWER snapshots
preserve its Ybus, specified injections and equation masks, rather than device
control or individual generator representations. Never use these for branch-flow
or control-validation claims. Run verbose preparation through run_bounded.py.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.sparse import bmat, coo_matrix, diags, save_npz
from scipy.sparse.linalg import spsolve


def load_export(directory):
    info = json.loads((directory / "model.json").read_text())
    with (directory / "bus.csv").open() as stream:
        records = list(csv.DictReader(stream))
    bus = {key: np.array([float(r[key]) for r in records])
           for key in records[0] if key != "original_id"}
    if not np.array_equal(bus["index"], np.arange(len(records))):
        raise ValueError("Exported bus indexing is not contiguous")
    edge = np.genfromtxt(directory / "branch.csv", delimiter=",", names=True, ndmin=1)
    f, t = edge["from"].astype(int), edge["to"].astype(int)
    a = edge["yft_real"] + 1j * edge["yft_imag"]
    b = edge["ytf_real"] + 1j * edge["ytf_imag"]
    n = len(records)
    diagonal = bus["yii_real"] + 1j * bus["yii_imag"]
    y = coo_matrix((np.r_[diagonal, a, b],
                   (np.r_[np.arange(n), f, t], np.r_[np.arange(n), t, f])), (n, n)).tocsr()
    s = bus["specified_p"] + 1j * bus["specified_q"]
    if not np.isfinite(y.data).all() or not np.isfinite(s).all():
        raise ValueError("Nonfinite nodal model")
    return info, records, bus, (f, t, a, b), y, s


def residual(y, s, v, pv, pq):
    mis = v * np.conj(y @ v) - s
    return np.r_[mis[np.r_[pv, pq]].real, mis[pq].imag]


def jacobian(y, v, pv, pq):
    current = y @ v
    dv = diags(v)
    unit = diags(v / np.abs(v))
    angle = 1j * dv @ (diags(current) - y @ dv).conjugate()
    magnitude = dv @ (y @ unit).conjugate() + diags(current.conjugate()) @ unit
    p = np.r_[pv, pq]
    return bmat([[angle.real[p][:, p], magnitude.real[p][:, pq]],
                 [angle.imag[pq][:, p], magnitude.imag[pq][:, pq]]], format="csc")


def reference_solve(y, s, v0, pv, pq, max_iterations=20):
    v = v0.copy()
    history = []
    p = np.r_[pv, pq]
    for iteration in range(max_iterations + 1):
        r = residual(y, s, v, pv, pq)
        norm = float(np.max(np.abs(r)))
        history.append(norm)
        print("reference", iteration, "mismatch_pu", norm, flush=True)
        if norm <= 1e-8:
            return v, history
        if iteration == max_iterations:
            break
        correction = spsolve(jacobian(y, v, pv, pq), -r, use_umfpack=False)
        vm, va = np.abs(v), np.angle(v)
        va[p] += correction[:len(p)]
        vm[pq] += correction[len(p):]
        v = vm * np.exp(1j * va)
        if not np.isfinite(v).all():
            raise RuntimeError("Independent Newton reference became nonfinite")
    raise RuntimeError(f"Independent flat-start reference failed: {history}")


def canonical_tables(bus, edge, s, base):
    f, t, a, b = edge
    if np.any(abs(a) == 0) or np.max(abs(abs(a) - abs(b))) > 1e-9:
        raise ValueError("Unsupported asymmetric two-port magnitudes")
    phase = .5 * np.angle(a / b)
    series = -a * np.exp(-1j * phase)
    z = 1 / series
    diagonal = bus["yii_real"] + 1j * bus["yii_imag"]
    shunt = diagonal.copy()
    np.add.at(shunt, f, -series)
    np.add.at(shunt, t, -series)
    n = len(s)
    buses = np.zeros((n, 13))
    buses[:, 0] = np.arange(n)
    buses[:, 1] = bus["type"]
    buses[:, 2:4] = np.c_[-s.real * base, -s.imag * base]
    buses[:, 4:6] = np.c_[shunt.real * base, shunt.imag * base]
    buses[:, 6] = buses[:, 10] = 1
    buses[:, 7] = np.where(bus["type"] > 1, bus["desired_vm"], 1.)
    # The normalized two-port has unit tap; equal nominal bases prevent a
    # parser from introducing a second nominal-voltage ratio.
    buses[:, 9] = 100
    buses[:, 11:13] = (2., .1)
    generators = np.zeros((int(np.sum(bus["type"] > 1)), 21))
    mask = bus["type"] > 1
    generators[:, 0] = np.flatnonzero(mask)
    generators[:, 3:5] = (1e9, -1e9)
    generators[:, 5] = bus["desired_vm"][mask]
    generators[:, 6:8] = (base, 1)
    generators[:, 8:10] = (1e9, -1e9)
    branches = np.zeros((len(f), 13))
    branches[:, :4] = np.c_[f, t, z.real, z.imag]
    branches[:, 9] = np.rad2deg(phase)
    branches[:, 10:13] = (1, -360, 360)
    return buses, generators, branches


def prepare(source, output, name, start="flat"):
    output.mkdir(parents=True, exist_ok=True)
    if (output / (name + ".m")).exists():
        raise ValueError("Existing evidence will not be overwritten")
    info, records, bus, edge, y, s = load_export(source)
    pq, pv, ref = (np.flatnonzero(bus["type"] == kind) for kind in (1, 2, 3))
    if len(ref) != 1 or info["active_buses"] != info["original_buses"]:
        raise ValueError("Requires all buses and one reference")
    v0 = np.ones(len(s), dtype=complex)
    v0[np.r_[pv, ref]] = bus["desired_vm"][np.r_[pv, ref]]
    if start == "saved":
        angles = bus["saved_va"] - bus["saved_va"][ref[0]]
        magnitudes = bus["saved_vm"].copy()
        magnitudes[np.r_[pv, ref]] = bus["desired_vm"][np.r_[pv, ref]]
        v0 = magnitudes * np.exp(1j * angles)
    v, history = reference_solve(y, s, v0, pv, pq)
    tables = canonical_tables(bus, edge, s, info["base_mva"])
    with (output / (name + ".m")).open("w", encoding="ascii") as stream:
        stream.write(f"function mpc = {name}\nmpc.version = '2';\nmpc.baseMVA = {info['base_mva']:.17g};\n")
        for key, table in zip(("bus", "gen", "branch"), tables):
            stream.write(f"mpc.{key} = [\n")
            np.savetxt(stream, table, fmt="%.17g", delimiter=" ", newline=";\n")
            stream.write("];\n")
    save_npz(output / (name + "-ybus.npz"), y)
    np.savez(output / (name + "-reference.npz"), v=v, s=s, pq=pq, pv=pv, ref=ref,
             v0=v0, bus_ids=np.arange(len(s)))
    if start != "flat":
        np.savetxt(output / (name + "-start.csv"), np.c_[np.abs(v0), np.angle(v0)],
                   delimiter=",", header="vm_pu,va_rad", comments="", fmt="%.17g")
    with (output / (name + "-bus-mapping.csv")).open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(("canonical_bus", "original_id", "original_base_kv"))
        writer.writerows((r["index"], r["original_id"], r["base_kv"]) for r in records)
    info.update(reference_mismatch_history_pu=history, start=start,
                representation="fixed-control canonical nodal model; aggregated net injections; unit tap magnitude and equivalent bus shunts",
                reference_solver="independent SciPy sparse polar Newton, full steps, tolerance 1e-8 pu",
                input_sha256=hashlib.sha256(Path(info["input"]).read_bytes()).hexdigest())
    (output / (name + "-model.json")).write_text(json.dumps(info, indent=2) + "\n")


def verify(source, roundtrip):
    _, _, a, _, y1, s1 = load_export(source)
    info, _, b, _, y2, s2 = load_export(roundtrip)
    delta = y1 - y2
    error = float(np.max(abs(delta.data))) if delta.nnz else 0.
    result = {"ybus_max_error": error, "sbus_max_error": float(np.max(abs(s1 - s2))),
              "equation_masks_equal": bool(np.array_equal(a["type"], b["type"])),
              "desired_vm_max_error": float(np.max(abs(a["desired_vm"][a["type"] > 1] - b["desired_vm"][b["type"] > 1]))) }
    print(json.dumps(result), flush=True)
    if error > 1e-9 or result["sbus_max_error"] > 1e-10 or not result["equation_masks_equal"] or result["desired_vm_max_error"] > 1e-12:
        raise RuntimeError("Canonical roundtrip changed the nodal model")
    (roundtrip / "roundtrip-validation.json").write_text(json.dumps(result, indent=2) + "\n")
    case_file = Path(info["input"])
    files = [case_file] + [case_file.with_name(case_file.stem + suffix) for suffix in
            ("-ybus.npz", "-reference.npz", "-model.json", "-bus-mapping.csv")]
    start_file = case_file.with_name(case_file.stem + "-start.csv")
    if start_file.exists():
        files.append(start_file)
    result["qualified_files_sha256"] = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
    case_file.with_name(case_file.stem + "-qualification.json").write_text(json.dumps(result, indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("prepare", "verify"))
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--name", choices=("activsg25k", "activsg70k"))
    parser.add_argument("--start", choices=("flat", "saved"), default="flat")
    args = parser.parse_args()
    if args.phase == "prepare":
        prepare(args.source, args.output, args.name, args.start)
    else:
        verify(args.source, args.output)
