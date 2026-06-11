#!/usr/bin/env python3
"""Compare DSS-Python and InterPSS QSTS branch-power exports."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


PHASE_BY_CONDUCTOR = {"1": "A", "2": "B", "3": "C", "A": "A", "B": "B", "C": "C"}


@dataclass(frozen=True)
class BranchPower:
    p_kw: float
    q_kvar: float


def normalize_class(value: str) -> str:
    return (value or "").strip().lower()


def normalize_element(element_class: str, element: str) -> str:
    normalized_class = normalize_class(element_class)
    normalized = (element or "").strip().lower()
    if "." in normalized:
        prefix, suffix = normalized.split(".", 1)
        if prefix in {"line", "transformer", "reactor"}:
            normalized = suffix
    return f"{normalized_class}.{normalized}"


def phase_from_row(row: dict[str, str], source: str) -> str | None:
    if source == "dss":
        token = (row.get("conductor") or "").strip().upper()
    else:
        token = (row.get("phase") or "").strip().upper()
    return PHASE_BY_CONDUCTOR.get(token)


def read_branch_powers(path: Path, source: str, zero_threshold: float) -> dict[tuple, BranchPower]:
    powers: dict[tuple, BranchPower] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            element_class = normalize_class(row.get("class", ""))
            if element_class not in {"line", "transformer", "reactor"}:
                continue
            phase = phase_from_row(row, source)
            if phase is None:
                continue
            p_kw = float(row.get("p_kw") or 0.0)
            q_kvar = float(row.get("q_kvar") or 0.0)
            if abs(p_kw) + abs(q_kvar) <= zero_threshold:
                continue
            key = (
                int(row.get("step") or 0),
                element_class,
                normalize_element(element_class, row.get("element", "")),
                int(row.get("terminal") or 0),
                phase,
            )
            powers[key] = BranchPower(p_kw, q_kvar)
    return powers


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


def compare(
    dss: dict[tuple, BranchPower],
    interpss: dict[tuple, BranchPower],
    p_tolerance_kw: float,
    q_tolerance_kvar: float,
) -> int:
    common = sorted(set(dss) & set(interpss))
    dss_only = sorted(set(dss) - set(interpss))
    interpss_only = sorted(set(interpss) - set(dss))
    max_p_delta = -1.0
    max_p_key = None
    max_q_delta = -1.0
    max_q_key = None
    p_failures = 0
    q_failures = 0
    for key in common:
        p_delta = abs(dss[key].p_kw - interpss[key].p_kw)
        q_delta = abs(dss[key].q_kvar - interpss[key].q_kvar)
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
    if max_p_delta < 0.0:
        max_p_delta = 0.0
    if max_q_delta < 0.0:
        max_q_delta = 0.0
    print(
        "QSTS_BRANCH_POWER_COMPARE "
        f"commonKeys={len(common)} dssOnly={len(dss_only)} interpssOnly={len(interpss_only)} "
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
    parser.add_argument("--dss-branch-power", type=Path, required=True)
    parser.add_argument("--interpss-branch-power", type=Path, required=True)
    parser.add_argument("--p-tolerance-kw", type=float, default=5.0)
    parser.add_argument("--q-tolerance-kvar", type=float, default=5.0)
    parser.add_argument("--zero-threshold-kw", type=float, default=1.0e-9)
    parser.add_argument("--require-control-tag", default="controls_static")
    args = parser.parse_args()

    validate_control_tags(args.dss_branch_power, args.interpss_branch_power,
                          args.require_control_tag)
    dss = read_branch_powers(args.dss_branch_power, "dss", args.zero_threshold_kw)
    interpss = read_branch_powers(args.interpss_branch_power, "interpss", args.zero_threshold_kw)
    return compare(dss, interpss, args.p_tolerance_kw, args.q_tolerance_kvar)


if __name__ == "__main__":
    raise SystemExit(main())
