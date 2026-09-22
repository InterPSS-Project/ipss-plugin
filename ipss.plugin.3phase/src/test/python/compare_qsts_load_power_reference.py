#!/usr/bin/env python3
"""Compare DSS-Python and InterPSS QSTS load-power exports."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class LoadPower:
    p_kw: float
    q_kvar: float


def normalize_device(value: str) -> str:
    normalized = (value or "").strip().lower()
    if normalized.startswith("load."):
        return normalized[5:]
    return normalized


def control_tag(path: Path) -> str | None:
    parts = path.stem.split("_")
    for index, part in enumerate(parts):
        if part == "controls" and index + 1 < len(parts):
            tag_parts = parts[index:index + 2]
            cursor = index + 2
            while cursor < len(parts) and parts[cursor] in {"noreg", "nocap"}:
                tag_parts.append(parts[cursor])
                cursor += 1
            return "_".join(tag_parts)
    return None


def validate_control_tags(dss_path: Path, interpss_path: Path, required_tag: str) -> None:
    dss_tag = control_tag(dss_path)
    interpss_tag = control_tag(interpss_path)
    if dss_tag != interpss_tag:
        raise RuntimeError(
            f"Control-tag mismatch: DSS-Python file has {dss_tag}, InterPSS file has {interpss_tag}"
        )
    if required_tag.lower() != "any" and dss_tag != required_tag:
        raise RuntimeError(
            f"Expected QSTS comparison control tag {required_tag}, found {dss_tag}. "
            "Use --require-control-tag any only for an intentional diagnostic comparison."
        )


def read_load_powers(path: Path, zero_threshold_kw: float) -> dict[tuple[int, str, str], LoadPower]:
    values: dict[tuple[int, str, str], LoadPower] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            phase = (row.get("phase") or "").strip().upper()
            if phase not in {"A", "B", "C"}:
                continue
            p_kw = float(row.get("p_kw") or 0.0)
            q_kvar = float(row.get("q_kvar") or 0.0)
            if abs(p_kw) + abs(q_kvar) <= zero_threshold_kw:
                continue
            key = (
                int(row.get("step") or 0),
                normalize_device(row.get("device", "")),
                phase,
            )
            values[key] = LoadPower(p_kw, q_kvar)
    return values


def compare(
    dss: dict[tuple[int, str, str], LoadPower],
    interpss: dict[tuple[int, str, str], LoadPower],
    p_tolerance_kw: float,
    q_tolerance_kvar: float,
) -> int:
    common = sorted(set(dss) & set(interpss))
    dss_only = sorted(set(dss) - set(interpss))
    interpss_only = sorted(set(interpss) - set(dss))
    max_p_delta = 0.0
    max_p_key = None
    max_q_delta = 0.0
    max_q_key = None
    p_failures = 0
    q_failures = 0
    dss_p_total = 0.0
    dss_q_total = 0.0
    interpss_p_total = 0.0
    interpss_q_total = 0.0
    for key in common:
        dss_power = dss[key]
        interpss_power = interpss[key]
        dss_p_total += dss_power.p_kw
        dss_q_total += dss_power.q_kvar
        interpss_p_total += interpss_power.p_kw
        interpss_q_total += interpss_power.q_kvar
        p_delta = abs(dss_power.p_kw - interpss_power.p_kw)
        q_delta = abs(dss_power.q_kvar - interpss_power.q_kvar)
        if p_delta > max_p_delta:
            max_p_delta = p_delta
            max_p_key = key
        if q_delta > max_q_delta:
            max_q_delta = q_delta
            max_q_key = key
        if p_delta > p_tolerance_kw:
            p_failures += 1
        if q_delta > q_tolerance_kvar:
            q_failures += 1
    print(
        "QSTS_LOAD_POWER_COMPARE "
        f"commonKeys={len(common)} dssOnly={len(dss_only)} interpssOnly={len(interpss_only)} "
        f"dssPTotalKw={dss_p_total:.12g} interpssPTotalKw={interpss_p_total:.12g} "
        f"dssQTotalKvar={dss_q_total:.12g} interpssQTotalKvar={interpss_q_total:.12g} "
        f"maxPDelta={max_p_delta:.12g} maxPKey={max_p_key} "
        f"maxQDelta={max_q_delta:.12g} maxQKey={max_q_key} "
        f"pFailures={p_failures} qFailures={q_failures} "
        f"pToleranceKw={p_tolerance_kw:.12g} qToleranceKvar={q_tolerance_kvar:.12g}"
    )
    for label, rows in (("dssOnly", dss_only[:10]), ("interpssOnly", interpss_only[:10])):
        if rows:
            print(f"{label}Sample={rows}")
    return 0 if p_failures == 0 and q_failures == 0 and not dss_only else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dss-load-power", type=Path, required=True)
    parser.add_argument("--interpss-load-power", type=Path, required=True)
    parser.add_argument("--p-tolerance-kw", type=float, default=5.0)
    parser.add_argument("--q-tolerance-kvar", type=float, default=5.0)
    parser.add_argument("--zero-threshold-kw", type=float, default=1.0e-9)
    parser.add_argument("--require-control-tag", default="controls_static")
    args = parser.parse_args()

    validate_control_tags(args.dss_load_power, args.interpss_load_power,
                          args.require_control_tag)
    dss = read_load_powers(args.dss_load_power, args.zero_threshold_kw)
    interpss = read_load_powers(args.interpss_load_power, args.zero_threshold_kw)
    return compare(dss, interpss, args.p_tolerance_kw, args.q_tolerance_kvar)


if __name__ == "__main__":
    raise SystemExit(main())
