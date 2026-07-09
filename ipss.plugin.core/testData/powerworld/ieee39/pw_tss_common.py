#!/usr/bin/env python3
"""Shared helpers for IEEE39 PlanMaintainModel → PowerWorld TSS artifact generation."""

from __future__ import annotations

import json
import re
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

IEEE39_JSON = (
    Path(__file__).resolve().parents[4].parent
    / "ipss-core/ipss.test.core/testdata/json/ieee39.json"
)
RAW_FILE = Path(__file__).resolve().parent.parent.parent / "psse/v30/IEEE39bus_v30.raw"


def parse_raw_branches(raw_text: str) -> list[tuple[int, int, str]]:
    branches: list[tuple[int, int, str]] = []
    in_branch = False
    for line in raw_text.splitlines():
        if "BEGIN NON-TRANSFORMER BRANCH DATA" in line:
            in_branch = True
            continue
        if in_branch and line.startswith("0 / END OF NON-TRANSFORMER BRANCH"):
            break
        if not in_branch or not line.strip() or line.startswith("0 /"):
            continue
        m = re.match(r"\s*(\d+),\s*(\d+),\s*'([^']*)'", line)
        if m:
            branches.append((int(m.group(1)), int(m.group(2)), m.group(3).strip()))
    return branches


def parse_branch_label(name: str) -> tuple[int, int, str] | None:
    m = re.fullmatch(r"Bus(\d+)_to_Bus(\d+)_cirId_(\d+)", name)
    if not m:
        return None
    return int(m.group(1)), int(m.group(2)), m.group(3)


def line_branch_names() -> list[str]:
    with IEEE39_JSON.open(encoding="utf-8") as fh:
        net = json.load(fh)
    names: list[str] = []
    for br in net.get("branchAry", []):
        if br.get("branchCode") != "LINE":
            continue
        name = br.get("name") or br.get("id")
        if name:
            names.append(name)
    return sorted(names)


def match_branch(
    label: str, branches: list[tuple[int, int, str]]
) -> tuple[int, int, str] | None:
    parsed = parse_branch_label(label)
    if parsed is None:
        return None
    fb, tb, ckt = parsed
    for b_from, b_to, b_ckt in branches:
        ckt_norm = b_ckt.strip()
        if {b_from, b_to} == {fb, tb} and ckt_norm.rstrip() in {ckt, ckt + " "}:
            return b_from, b_to, b_ckt
    return None


def pw_datetime(dt: datetime) -> str:
    hour = dt.hour % 12 or 12
    am_pm = "AM" if dt.hour < 12 else "PM"
    return f"{dt.month:02d}/{dt.day:02d}/{dt.year} {hour:02d}:{dt.minute:02d}:00 {am_pm}"


def pw_datetime_short(dt: datetime) -> str:
    return f"{dt.month:02d}/{dt.day:02d}/{dt.year} {dt.hour:02d}:{dt.minute:02d}"


def schedule_block(
    name: str,
    value_type: str,
    points: list[tuple[datetime, int, float | str]],
) -> str:
    lines = [
        f'  "{name}" "{value_type}" "NO " "NO " 0 0 0 0 "NO " "" "NO " ""',
        "  <SUBDATA SchedPoint>",
        "    //Date Hour PointType NValue BValue TValue AValue",
    ]
    for dt, ptype, value in points:
        if ptype == 0:
            lines.append(
                f"    {pw_datetime(dt)} {ptype} {value:.6f} NO \"\" \"\""
            )
        else:
            lines.append(f'    {pw_datetime(dt)} {ptype} 0.0 {value} "" ""')
    lines.append("  </SUBDATA>")
    return "\n".join(lines)


def subscription_row(
    obj_type: str,
    obj_id: str,
    field: str,
    schedule_name: str,
) -> str:
    return (
        f'  "{obj_type}" "{obj_id}" "{field}" "{schedule_name}" '
        f'"YES" "NO " 1.0 0.0 "NO " 0 0 0 0'
    )


def flatten_time_points(plan: dict[str, Any]) -> list[dict[str, Any]]:
    points: list[dict[str, Any]] = []
    for period in plan["timePeriodRecList"]:
        points.extend(period["timePointRecList"])
    return points


def plan_start(plan: dict[str, Any]) -> datetime:
    return datetime.fromisoformat(plan["planDataTimeStamp"])


def time_point_datetimes(plan: dict[str, Any]) -> list[datetime]:
    start = plan_start(plan)
    interval_min = plan.get("timePointIntervalMin", 15)
    count = len(flatten_time_points(plan))
    return [start + timedelta(minutes=interval_min * i) for i in range(count)]


def compress_step_hold(
    datetimes: list[datetime],
    values: list[float | str],
    point_type: int,
) -> list[tuple[datetime, int, float | str]]:
    if not datetimes:
        return []
    points: list[tuple[datetime, int, float | str]] = []
    prev: float | str | None = None
    for dt, value in zip(datetimes, values):
        if prev is None or value != prev:
            points.append((dt, point_type, value))
            prev = value
    return points


def build_maint_schedule_points(
    plan_start_dt: datetime,
    outage_start: datetime,
    outage_end: datetime,
) -> list[tuple[datetime, int, str]]:
    return [
        (plan_start_dt, 1, "CLOSED"),
        (outage_start, 1, "OPEN"),
        (outage_end, 1, "CLOSED"),
    ]


def write_labeled_aux(
    output_dir: Path,
    branches: list[tuple[int, int, str]],
    line_labels: list[str],
) -> None:
    gen_rows = [f'  {bus} "1" "Bus{bus}-G1"' for bus in range(30, 40)]
    load_buses = [
        3, 4, 7, 8, 12, 15, 16, 18, 20, 21, 23, 24, 25, 26, 27, 28, 29, 31, 39,
    ]
    load_rows = [f'  {bus} "1 " "Bus{bus}-L1"' for bus in load_buses]

    branch_rows: list[str] = []
    for label in line_labels:
        match = match_branch(label, branches)
        if match is None:
            raise RuntimeError(f"Could not map branch label {label} to PSSE raw")
        fb, tb, ckt = match
        branch_rows.append(f'  {fb} {tb} "{ckt}" "{label}"')

    content = "\n".join(
        [
            "// IEEE39 labeled ObjectID references for PowerWorld TSS and contingency scripts.",
            "// Load after importing IEEE39bus_v30.raw into Simulator.",
            "// Labels match InterPSS PlanMaintainModel device names.",
            "",
            "Gen (BusNum, GenID, Label)",
            "{",
            *gen_rows,
            "}",
            "",
            "Load (BusNum, LoadID, Label)",
            "{",
            *load_rows,
            "}",
            "",
            "Branch (BusNum, BusNum:1, LineCircuit, Label)",
            "{",
            *branch_rows,
            "}",
            "",
        ]
    )
    (output_dir / "IEEE39bus_v30_labeled.aux").write_text(content, encoding="utf-8")


def write_golden_reference(output_dir: Path, start: datetime) -> None:
    content = "\n".join(
        [
            "// Golden reference: minimal TSSchedule + TSScheduleSub examples for PowerWorld validation.",
            "// Syntax inferred from Auxiliary File Format (TSSchedule/SchedPoint) and Schedule Dialog fields.",
            "// Re-export one schedule + subscription from Simulator SaveData and diff if load fails.",
            "",
            "TSSchedule (ScheduleName, ValueType, Interpolate, ApplyAsEvents, RepeatDays, RepeatHours, RepeatMinutes, RepeatSeconds, ValidFromActive, ValidFrom, ValidUntilActive, ValidUntil)",
            "{",
            schedule_block("Sched_Gen_Bus39-G1", "Numeric", [(start, 0, 1000.0)]),
            schedule_block("Sched_Load_Bus15-L1", "Numeric", [(start, 0, 320.0)]),
            schedule_block(
                "Sched_Maint_Bus29_to_Bus26_cirId_1",
                "Yes/No",
                [
                    (start, 1, "CLOSED"),
                    (datetime(start.year, start.month, start.day, 8, 0), 1, "OPEN"),
                    (datetime(start.year, start.month, start.day, 11, 0), 1, "CLOSED"),
                ],
            ),
            "}",
            "",
            "TSScheduleSub (ObjectType, ObjectIdentifier, ObjectField, ScheduleName, Active, Relative, Multiplier, ValueShift, DelayAdvance, DayShift, HourShift, MinuteShift, SecondShift)",
            "{",
            subscription_row("Gen", "Bus39-G1", "Gen MW", "Sched_Gen_Bus39-G1"),
            subscription_row("Load", "Bus15-L1", "Load MW", "Sched_Load_Bus15-L1"),
            subscription_row(
                "Branch",
                "Bus29_to_Bus26_cirId_1",
                "Line Status",
                "Sched_Maint_Bus29_to_Bus26_cirId_1",
            ),
            "}",
            "",
        ]
    )
    (output_dir / "golden_tsschedule_reference.aux").write_text(content, encoding="utf-8")


def write_schedules_aux(
    plan: dict[str, Any],
    output_path: Path,
    header_comment: str,
) -> None:
    time_points = flatten_time_points(plan)
    datetimes = time_point_datetimes(plan)
    start_dt = plan_start(plan)

    gen_names = sorted(time_points[0]["genMap"])
    load_names = sorted(time_points[0]["loadMap"])

    schedule_blocks: list[str] = []
    for name in gen_names:
        values = [tp["genMap"][name]["p"] for tp in time_points]
        points = compress_step_hold(datetimes, values, 0)
        schedule_blocks.append(
            schedule_block(f"Sched_Gen_{name}", "Numeric", points)
        )
    for name in load_names:
        values = [tp["loadMap"][name]["p"] for tp in time_points]
        points = compress_step_hold(datetimes, values, 0)
        schedule_blocks.append(
            schedule_block(f"Sched_Load_{name}", "Numeric", points)
        )

    for rec in plan["originalMaintainEquipemnts"]:
        label = rec["name"]
        outage_start = datetime.fromisoformat(rec["startTime"])
        outage_end = datetime.fromisoformat(rec["endTime"])
        schedule_blocks.append(
            schedule_block(
                f"Sched_Maint_{label}",
                "Yes/No",
                build_maint_schedule_points(start_dt, outage_start, outage_end),
            )
        )

    sub_rows: list[str] = []
    for name in gen_names:
        sub_rows.append(subscription_row("Gen", name, "Gen MW", f"Sched_Gen_{name}"))
    for name in load_names:
        sub_rows.append(
            subscription_row("Load", name, "Load MW", f"Sched_Load_{name}")
        )
    for rec in plan["originalMaintainEquipemnts"]:
        label = rec["name"]
        sub_rows.append(
            subscription_row(
                "Branch", label, "Line Status", f"Sched_Maint_{label}"
            )
        )

    content = "\n".join(
        [
            header_comment,
            "",
            "TSSchedule (ScheduleName, ValueType, Interpolate, ApplyAsEvents, RepeatDays, RepeatHours, RepeatMinutes, RepeatSeconds, ValidFromActive, ValidFrom, ValidUntilActive, ValidUntil)",
            "{",
            ",\n".join(schedule_blocks),
            "}",
            "",
            "TSScheduleSub (ObjectType, ObjectIdentifier, ObjectField, ScheduleName, Active, Relative, Multiplier, ValueShift, DelayAdvance, DayShift, HourShift, MinuteShift, SecondShift)",
            "{",
            *sub_rows,
            "}",
            "",
        ]
    )
    output_path.write_text(content, encoding="utf-8")


def write_outages_csv(plan: dict[str, Any], output_path: Path) -> None:
    rows = ["OutageID,Description,BranchLabel,Action,StartTime,EndTime,Status"]
    for idx, rec in enumerate(plan["originalMaintainEquipemnts"], start=1):
        start = datetime.fromisoformat(rec["startTime"])
        end = datetime.fromisoformat(rec["endTime"])
        rows.append(
            ",".join(
                [
                    f"OUT{idx:03d}",
                    rec["name"],
                    rec["name"],
                    "OpenLine",
                    pw_datetime_short(start),
                    pw_datetime_short(end),
                    "Planned",
                ]
            )
        )
    output_path.write_text("\n".join(rows) + "\n", encoding="utf-8")


def write_timepoints_csv(
    output_path: Path,
    start: datetime,
    interval_min: int,
    num_points: int,
) -> None:
    rows = ["Index,ISO8601,DisplayTime,SolutionType"]
    for i in range(num_points):
        dt = start + timedelta(minutes=interval_min * i)
        rows.append(f"{i},{dt.isoformat()},{pw_datetime(dt)},Single Solution")
    output_path.write_text("\n".join(rows) + "\n", encoding="utf-8")


def write_tsb_placeholder(
    output_path: Path,
    prefix: str,
    start: datetime,
    interval_min: int,
    num_points: int,
    generate_aux_name: str,
    timepoints_csv_name: str,
) -> None:
    end = start + timedelta(minutes=interval_min * (num_points - 1))
    lines = [
        f"# {prefix}.tsb manifest",
        "# Binary TSB cannot be hand-authored. Generate once in PowerWorld Simulator:",
        f"#   1. Open IEEE39 case + {generate_aux_name} instructions",
        f"#   2. Configure TSS points from {timepoints_csv_name}",
        f"#   3. Run SCRIPT to write {prefix}.tsb",
        "#",
        f"# Horizon: {num_points} points, {interval_min}-min, {start.isoformat()} .. {end.isoformat()}",
        "# SolutionType: Single Solution",
        "",
    ]
    for i in range(num_points):
        dt = start + timedelta(minutes=interval_min * i)
        lines.append(f"T{i:03d}\t{dt.isoformat()}\tSingle Solution")
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
