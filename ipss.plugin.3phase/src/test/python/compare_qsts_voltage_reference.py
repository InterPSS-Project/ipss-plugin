#!/usr/bin/env python3
"""Compare InterPSS and DSS-Python per-step QSTS voltage CSV exports."""

from __future__ import annotations

import argparse
import csv
import math
from dataclasses import dataclass
from pathlib import Path


PHASE_BY_NODE = {"1": "A", "2": "B", "3": "C", "a": "A", "b": "B", "c": "C"}


@dataclass(frozen=True)
class Voltage:
    magnitude: float
    angle: float


def read_dss_python(path: Path, zero_threshold: float) -> dict[tuple[int, str, str], Voltage]:
    values: dict[tuple[int, str, str], Voltage] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            phase = PHASE_BY_NODE.get(row["node"].strip().lower())
            if phase is None:
                continue
            magnitude = float(row["vmag_pu"])
            if abs(magnitude) <= zero_threshold:
                continue
            key = (int(row["step"]), normalize_bus(row["bus"]), phase)
            values[key] = Voltage(magnitude, float(row["angle_deg"]))
    return values


def read_interpss(path: Path) -> dict[tuple[int, str, str], Voltage]:
    values: dict[tuple[int, str, str], Voltage] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            phase = row["phase"].strip().upper()
            if phase not in {"A", "B", "C"}:
                continue
            magnitude = parse_float(row["vmag_pu"])
            angle = parse_float(row["angle_deg"])
            if magnitude is None or angle is None:
                continue
            key = (int(row["step"]), normalize_bus(row["bus"]), phase)
            values[key] = Voltage(magnitude, angle)
    return values


def normalize_bus(value: str) -> str:
    return value.strip().lower()


def parse_float(value: str) -> float | None:
    value = value.strip()
    if not value:
        return None
    parsed = float(value)
    return parsed if math.isfinite(parsed) else None


def angle_delta_degrees(left: float, right: float) -> float:
    delta = (left - right + 180.0) % 360.0 - 180.0
    return abs(delta)


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dss-voltage", type=Path, required=True)
    parser.add_argument("--interpss-voltage", type=Path, required=True)
    parser.add_argument("--magnitude-tolerance", type=float, default=1.0e-3)
    parser.add_argument("--angle-tolerance", type=float, default=1.0)
    parser.add_argument("--dss-zero-threshold", type=float, default=1.0e-9)
    parser.add_argument("--require-control-tag", default="controls_static")
    args = parser.parse_args()

    validate_control_tags(args.dss_voltage, args.interpss_voltage, args.require_control_tag)
    dss_values = read_dss_python(args.dss_voltage, args.dss_zero_threshold)
    interpss_values = read_interpss(args.interpss_voltage)
    common_keys = sorted(set(dss_values).intersection(interpss_values))
    if not common_keys:
        raise RuntimeError("No common step/bus/phase voltage keys found")

    missing_in_interpss = sorted(set(dss_values).difference(interpss_values))
    missing_in_dss = sorted(set(interpss_values).difference(dss_values))
    max_mag_key = common_keys[0]
    max_angle_key = common_keys[0]
    max_mag_delta = -1.0
    max_angle_delta = -1.0
    mag_failures = 0
    angle_failures = 0

    for key in common_keys:
        expected = dss_values[key]
        actual = interpss_values[key]
        mag_delta = abs(actual.magnitude - expected.magnitude)
        angle_delta = angle_delta_degrees(actual.angle, expected.angle)
        if mag_delta > max_mag_delta:
            max_mag_delta = mag_delta
            max_mag_key = key
        if angle_delta > max_angle_delta:
            max_angle_delta = angle_delta
            max_angle_key = key
        if mag_delta > args.magnitude_tolerance:
            mag_failures += 1
        if angle_delta > args.angle_tolerance:
            angle_failures += 1

    print(
        "QSTS_VOLTAGE_COMPARE "
        f"commonKeys={len(common_keys)} "
        f"dssOnly={len(missing_in_interpss)} "
        f"interpssOnly={len(missing_in_dss)} "
        f"maxMagDelta={max_mag_delta:.12g} maxMagKey={format_key(max_mag_key)} "
        f"maxAngleDelta={max_angle_delta:.12g} maxAngleKey={format_key(max_angle_key)} "
        f"magFailures={mag_failures} angleFailures={angle_failures} "
        f"magTolerance={args.magnitude_tolerance:.12g} "
        f"angleTolerance={args.angle_tolerance:.12g}",
        flush=True,
    )

    if mag_failures or angle_failures:
        raise RuntimeError("Voltage comparison exceeded tolerance")


def format_key(key: tuple[int, str, str]) -> str:
    return f"{key[0]}:{key[1]}:{key[2]}"


if __name__ == "__main__":
    main()
