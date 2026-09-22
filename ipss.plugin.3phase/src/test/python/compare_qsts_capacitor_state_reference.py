#!/usr/bin/env python3
"""Compare DSS-Python and InterPSS QSTS capacitor-state exports."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class CapacitorState:
    closed: bool
    total_q_kvar: float


def normalize_capacitor(value: str) -> str:
    normalized = (value or "").strip().lower()
    if normalized.startswith("capacitor."):
        return normalized[10:]
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


def parse_bool(value: str) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes", "closed"}


def read_states(path: Path) -> dict[tuple[int, str], CapacitorState]:
    values: dict[tuple[int, str], CapacitorState] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            key = (
                int(row.get("step") or 0),
                normalize_capacitor(row.get("capacitor", "")),
            )
            values[key] = CapacitorState(
                parse_bool(row.get("closed", "")),
                float(row.get("total_q_kvar") or 0.0),
            )
    return values


def compare(
    dss: dict[tuple[int, str], CapacitorState],
    interpss: dict[tuple[int, str], CapacitorState],
    q_tolerance_kvar: float,
) -> int:
    common = sorted(set(dss) & set(interpss))
    dss_only = sorted(set(dss) - set(interpss))
    interpss_only = sorted(set(interpss) - set(dss))
    state_failures = 0
    q_failures = 0
    max_q_delta = 0.0
    max_q_key = None
    for key in common:
        dss_state = dss[key]
        interpss_state = interpss[key]
        if dss_state.closed != interpss_state.closed:
            state_failures += 1
        q_delta = abs(dss_state.total_q_kvar - interpss_state.total_q_kvar)
        if q_delta > max_q_delta:
            max_q_delta = q_delta
            max_q_key = key
        if q_delta > q_tolerance_kvar:
            q_failures += 1
    print(
        "QSTS_CAPACITOR_STATE_COMPARE "
        f"commonKeys={len(common)} dssOnly={len(dss_only)} interpssOnly={len(interpss_only)} "
        f"stateFailures={state_failures} qFailures={q_failures} "
        f"maxQDelta={max_q_delta:.12g} maxQKey={max_q_key} "
        f"qToleranceKvar={q_tolerance_kvar:.12g}"
    )
    for label, rows in (("dssOnly", dss_only[:10]), ("interpssOnly", interpss_only[:10])):
        if rows:
            print(f"{label}Sample={rows}")
    return 0 if state_failures == 0 and q_failures == 0 and not dss_only and not interpss_only else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dss-capacitor-state", type=Path, required=True)
    parser.add_argument("--interpss-capacitor-state", type=Path, required=True)
    parser.add_argument("--q-tolerance-kvar", type=float, default=5.0)
    parser.add_argument("--require-control-tag", default="controls_static")
    args = parser.parse_args()

    validate_control_tags(args.dss_capacitor_state, args.interpss_capacitor_state,
                          args.require_control_tag)
    dss = read_states(args.dss_capacitor_state)
    interpss = read_states(args.interpss_capacitor_state)
    return compare(dss, interpss, args.q_tolerance_kvar)


if __name__ == "__main__":
    raise SystemExit(main())
