#!/usr/bin/env python3
"""Generate IEEE39 day-ahead PowerWorld TSS auxiliary artifacts from PlanMaintainModel JSON."""

from __future__ import annotations

import json
import re
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PLAN_JSON = (
    ROOT.parent.parent / "psse/v30/ieee39_dayahead_plan_maintain_plan.json"
)
IEEE39_JSON = (
    Path(__file__).resolve().parents[4].parent
    / "ipss-core/ipss.test.core/testdata/json/ieee39.json"
)
RAW_FILE = ROOT.parent.parent / "psse/v30/IEEE39bus_v30.raw"

START = datetime(2026, 6, 27, 0, 0, 0)
INTERVAL_MIN = 15
NUM_POINTS = 96


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


def write_labeled_aux(
    branches: list[tuple[int, int, str]], line_labels: list[str]
) -> None:
    gen_rows = [
        f'  {bus} "1" "Bus{bus}-G1"' for bus in range(30, 40)
    ]
    load_buses = [3, 4, 7, 8, 12, 15, 16, 18, 20, 21, 23, 24, 25, 26, 27, 28, 29, 31, 39]
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
    (ROOT / "IEEE39bus_v30_labeled.aux").write_text(content, encoding="utf-8")


def write_golden_reference() -> None:
    content = "\n".join(
        [
            "// Golden reference: minimal TSSchedule + TSScheduleSub examples for PowerWorld validation.",
            "// Syntax inferred from Auxiliary File Format (TSSchedule/SchedPoint) and Schedule Dialog fields.",
            "// Re-export one schedule + subscription from Simulator SaveData and diff if load fails.",
            "",
            "TSSchedule (ScheduleName, ValueType, Interpolate, ApplyAsEvents, RepeatDays, RepeatHours, RepeatMinutes, RepeatSeconds, ValidFromActive, ValidFrom, ValidUntilActive, ValidUntil)",
            "{",
            schedule_block(
                "Sched_Gen_Bus39-G1",
                "Numeric",
                [(START, 0, 1000.0)],
            ),
            schedule_block(
                "Sched_Load_Bus15-L1",
                "Numeric",
                [(START, 0, 320.0)],
            ),
            schedule_block(
                "Sched_Maint_Bus29_to_Bus26_cirId_1",
                "Yes/No",
                [
                    (START, 1, "CLOSED"),
                    (datetime(2026, 6, 27, 8, 0), 1, "OPEN"),
                    (datetime(2026, 6, 27, 11, 0), 1, "CLOSED"),
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
    (ROOT / "golden_tsschedule_reference.aux").write_text(content, encoding="utf-8")


def write_schedules_aux(plan: dict) -> None:
    t0 = plan["timePeriodRecList"][0]["timePointRecList"][0]
    gen_map = t0["genMap"]
    load_map = t0["loadMap"]

    schedule_blocks: list[str] = []
    for name in sorted(gen_map):
        mw = gen_map[name]["p"]
        schedule_blocks.append(
            schedule_block(f"Sched_Gen_{name}", "Numeric", [(START, 0, mw)])
        )
    for name in sorted(load_map):
        mw = load_map[name]["p"]
        schedule_blocks.append(
            schedule_block(f"Sched_Load_{name}", "Numeric", [(START, 0, mw)])
        )

    maint_specs = [
        (
            "Bus29_to_Bus26_cirId_1",
            datetime(2026, 6, 27, 8, 0),
            datetime(2026, 6, 27, 11, 0),
        ),
        (
            "Bus26_to_Bus25_cirId_1",
            datetime(2026, 6, 27, 14, 0),
            datetime(2026, 6, 27, 16, 0),
        ),
    ]
    for label, start, end in maint_specs:
        schedule_blocks.append(
            schedule_block(
                f"Sched_Maint_{label}",
                "Yes/No",
                [
                    (START, 1, "CLOSED"),
                    (start, 1, "OPEN"),
                    (end, 1, "CLOSED"),
                ],
            )
        )

    sub_rows: list[str] = []
    for name in sorted(gen_map):
        sub_rows.append(
            subscription_row("Gen", name, "Gen MW", f"Sched_Gen_{name}")
        )
    for name in sorted(load_map):
        sub_rows.append(
            subscription_row("Load", name, "Load MW", f"Sched_Load_{name}")
        )
    for label, _, _ in maint_specs:
        sub_rows.append(
            subscription_row(
                "Branch",
                label,
                "Line Status",
                f"Sched_Maint_{label}",
            )
        )

    content = "\n".join(
        [
            "// IEEE39 day-ahead gen/load MW schedules and branch maintenance status schedules.",
            "// Source: ieee39_dayahead_plan_maintain_plan.json (96 x 15-min points, flat MW).",
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
    (ROOT / "ieee39_dayahead_plan_schedules.aux").write_text(content, encoding="utf-8")


def write_outages_csv(plan: dict) -> None:
    rows = [
        "OutageID,Description,BranchLabel,Action,StartTime,EndTime,Status",
    ]
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
    (ROOT / "ieee39_dayahead_plan_outages.csv").write_text(
        "\n".join(rows) + "\n", encoding="utf-8"
    )


def write_run_aux() -> None:
    content = "\n".join(
        [
            "SCRIPT IEEE39DayAhead",
            "{",
            '  SetScheduleWindow("06/27/2026 00:00", "06/27/2026 23:45", 15, MINUTES);',
            '  TimeStepLoadTSB("ieee39_dayahead_plan.tsb");',
            '  LoadAux("IEEE39bus_v30_labeled.aux", YES);',
            '  LoadAux("ieee39_dayahead_plan_schedules.aux", YES);',
            '  ImportData("ieee39_dayahead_plan_outages.csv", PWCSV, 1, NO);',
            '  ApplyScheduledActionsAt("06/27/2026 08:00", "06/27/2026 16:00", , NO);',
            '  TimeStepDoRun("2026-06-27T00:00:00", "2026-06-27T23:45:00");',
            "}",
            "",
        ]
    )
    (ROOT / "ieee39_dayahead_plan_run.aux").write_text(content, encoding="utf-8")


def write_generate_tsb_aux() -> None:
    content = "\n".join(
        [
            "// One-time helper: configure TSS Summary in Simulator, then run this script to save the .tsb.",
            "// Manual setup (Time Step Simulation dialog):",
            "//   Start: 06/27/2026 00:00, End: 06/27/2026 23:45, Resolution: 15 minutes",
            "//   Solution type: Single Solution for all 96 points",
            "// Then execute:",
            "SCRIPT GenerateIEEE39TSB",
            "{",
            "  TimeStepDeleteAll;",
            "  // Add 96 time points via TSS Summary UI (or paste ieee39_dayahead_plan_timepoints.csv).",
            '  TimeStepSaveTSB("ieee39_dayahead_plan.tsb");',
            "}",
            "",
        ]
    )
    (ROOT / "ieee39_dayahead_plan_generate_tsb.aux").write_text(
        content, encoding="utf-8"
    )


def write_timepoints_csv() -> None:
    rows = ["Index,ISO8601,DisplayTime,SolutionType"]
    for i in range(NUM_POINTS):
        dt = START + timedelta(minutes=INTERVAL_MIN * i)
        rows.append(
            f"{i},{dt.isoformat()},{pw_datetime(dt)},Single Solution"
        )
    (ROOT / "ieee39_dayahead_plan_timepoints.csv").write_text(
        "\n".join(rows) + "\n", encoding="utf-8"
    )


def write_tsb_placeholder() -> None:
    # PowerWorld .tsb is a proprietary binary; commit a manifest until generated in Simulator.
    lines = [
        "# ieee39_dayahead_plan.tsb manifest",
        "# Binary TSB cannot be hand-authored. Generate once in PowerWorld Simulator:",
        "#   1. Open IEEE39 case + ieee39_dayahead_plan_generate_tsb.aux instructions",
        "#   2. Configure 96 TSS points from ieee39_dayahead_plan_timepoints.csv",
        "#   3. Run SCRIPT GenerateIEEE39TSB to write ieee39_dayahead_plan.tsb",
        "#",
        f"# Horizon: {NUM_POINTS} points, {INTERVAL_MIN}-min, {START.isoformat()} ..",
        f"# {(START + timedelta(minutes=INTERVAL_MIN * (NUM_POINTS - 1))).isoformat()}",
        "# SolutionType: Single Solution",
        "",
    ]
    for i in range(NUM_POINTS):
        dt = START + timedelta(minutes=INTERVAL_MIN * i)
        lines.append(f"T{i:02d}\t{dt.isoformat()}\tSingle Solution")
    (ROOT / "ieee39_dayahead_plan.tsb").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    with PLAN_JSON.open(encoding="utf-8") as fh:
        plan = json.load(fh)
    raw_text = RAW_FILE.read_text(encoding="utf-8")
    branches = parse_raw_branches(raw_text)
    line_labels = line_branch_names()

    write_labeled_aux(branches, line_labels)
    write_golden_reference()
    write_schedules_aux(plan)
    write_outages_csv(plan)
    write_run_aux()
    write_generate_tsb_aux()
    write_timepoints_csv()
    write_tsb_placeholder()
    print(f"Generated PowerWorld TSS artifacts under {ROOT}")


if __name__ == "__main__":
    main()
