#!/usr/bin/env python3
"""Generate IEEE39 day-ahead PowerWorld TSS auxiliary artifacts from PlanMaintainModel JSON."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

import pw_tss_common as common

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "dayahead"
PLAN_JSON = (
    Path(__file__).resolve().parents[4].parent
    / "ipss-core/ipss.test.core/testdata/json/ieee39_dayahead_plan_maintain_plan.json"
)

START = datetime(2026, 6, 27, 0, 0, 0)
INTERVAL_MIN = 15
NUM_POINTS = 96
PREFIX = "ieee39_dayahead"


def write_run_aux() -> None:
    content = "\n".join(
        [
            "SCRIPT IEEE39DayAhead",
            "{",
            '  SetScheduleWindow("06/27/2026 00:00", "06/27/2026 23:45", 15, MINUTES);',
            f'  TimeStepLoadTSB("{PREFIX}.tsb");',
            '  LoadAux("../IEEE39bus_v30_labeled.aux", YES);',
            f'  LoadAux("{PREFIX}_schedules.aux", YES);',
            f'  ImportData("{PREFIX}_outages.csv", PWCSV, 1, NO);',
            '  ApplyScheduledActionsAt("06/27/2026 08:00", "06/27/2026 16:00", , NO);',
            '  TimeStepDoRun("2026-06-27T00:00:00", "2026-06-27T23:45:00");',
            "}",
            "",
        ]
    )
    (OUT_DIR / f"{PREFIX}_run.aux").write_text(content, encoding="utf-8")


def write_generate_tsb_aux() -> None:
    content = "\n".join(
        [
            "// One-time helper: configure TSS Summary in Simulator, then run this script to save the .tsb.",
            "// Manual setup (Time Step Simulation dialog):",
            "//   Start: 06/27/2026 00:00, End: 06/27/2026 23:45, Resolution: 15 minutes",
            "//   Solution type: Single Solution for all 96 points",
            "SCRIPT GenerateIEEE39DayAheadTSB",
            "{",
            "  TimeStepDeleteAll;",
            f'  // Add 96 time points via TSS Summary UI (or paste {PREFIX}_timepoints.csv).',
            f'  TimeStepSaveTSB("{PREFIX}.tsb");',
            "}",
            "",
        ]
    )
    (OUT_DIR / f"{PREFIX}_generate_tsb.aux").write_text(content, encoding="utf-8")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with PLAN_JSON.open(encoding="utf-8") as fh:
        plan = json.load(fh)
    raw_text = common.RAW_FILE.read_text(encoding="utf-8")
    branches = common.parse_raw_branches(raw_text)
    line_labels = common.line_branch_names()

    common.write_labeled_aux(ROOT, branches, line_labels)
    common.write_golden_reference(ROOT, START)
    common.write_schedules_aux(
        plan,
        OUT_DIR / f"{PREFIX}_schedules.aux",
        "// IEEE39 day-ahead gen/load MW schedules and branch maintenance status schedules.",
    )
    common.write_outages_csv(plan, OUT_DIR / f"{PREFIX}_outages.csv")
    common.write_timepoints_csv(
        OUT_DIR / f"{PREFIX}_timepoints.csv", START, INTERVAL_MIN, NUM_POINTS
    )
    common.write_tsb_placeholder(
        OUT_DIR / f"{PREFIX}.tsb",
        PREFIX,
        START,
        INTERVAL_MIN,
        NUM_POINTS,
        f"{PREFIX}_generate_tsb.aux",
        f"{PREFIX}_timepoints.csv",
    )
    write_run_aux()
    write_generate_tsb_aux()
    print(f"Generated PowerWorld TSS day-ahead artifacts under {OUT_DIR}")


if __name__ == "__main__":
    main()
