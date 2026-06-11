#!/usr/bin/env python3
"""Export DSS-Python per-step references for large-feeder QSTS comparisons.

The cases and control settings intentionally match qsts_large_feeder_perf.py so
the performance smoke setup can also produce voltage and branch-flow evidence.
"""

from __future__ import annotations

import argparse
import csv
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import opendssdirect as dss


REPO_ROOT = Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class FeederCase:
    key: str
    name: str
    folder: Path
    master_file: str


CASES = {
    "ckt24": FeederCase(
        "ckt24",
        "Ckt24",
        REPO_ROOT / "testData" / "feeder" / "Ckt24",
        "master_ckt24_interpss.dss",
    ),
    "ieee8500": FeederCase(
        "ieee8500",
        "IEEE8500",
        REPO_ROOT / "testData" / "feeder" / "IEEE8500",
        "Master-InterPSS.dss",
    ),
}


def compile_case(feeder: FeederCase, mode: str, step_size_hours: float) -> None:
    dss.Basic.ClearAll()
    previous_cwd = Path.cwd()
    try:
        os.chdir(feeder.folder)
        dss.Text.Command(f'compile "{feeder.master_file}"')
        dss.Text.Command("set controlmode=off")
        dss.Text.Command("batchedit regcontrol..* enabled=no")
        dss.Text.Command("batchedit capcontrol..* enabled=no")
        dss.Text.Command("set maxcontroliter=100")
        dss.Text.Command(f"set mode={mode}")
        dss.Text.Command(f"set stepsize={step_size_hours}h")
        dss.Text.Command("set number=1")
    finally:
        os.chdir(previous_cwd)


def selected_cases(names: list[str]) -> list[FeederCase]:
    if not names or names == ["all"]:
        return [CASES["ckt24"], CASES["ieee8500"]]
    selected: list[FeederCase] = []
    for name in names:
        key = name.lower()
        if key == "8500":
            key = "ieee8500"
        selected.append(CASES[key])
    return selected


def bus_voltage_rows(feeder: FeederCase, step: int, hour: float) -> Iterable[list[object]]:
    for bus_name in dss.Circuit.AllBusNames():
        dss.Circuit.SetActiveBus(bus_name)
        nodes = list(dss.Bus.Nodes())
        values = list(dss.Bus.puVmagAngle())
        for index, node in enumerate(nodes):
            value_index = 2 * index
            if value_index + 1 >= len(values):
                continue
            yield [
                feeder.name,
                step,
                f"{hour:.12g}",
                bus_name.lower(),
                node,
                f"{values[value_index]:.12g}",
                f"{values[value_index + 1]:.12g}",
            ]


def branch_power_rows(feeder: FeederCase, step: int, hour: float) -> Iterable[list[object]]:
    for element_name in dss.Circuit.AllElementNames():
        element_class = element_name.split(".", 1)[0].lower()
        if element_class not in {"line", "transformer", "reactor"}:
            continue
        dss.Circuit.SetActiveElement(element_name)
        powers = list(dss.CktElement.Powers())
        if not powers:
            continue
        terminals = int(dss.CktElement.NumTerminals())
        conductors = int(dss.CktElement.NumConductors())
        bus_names = list(dss.CktElement.BusNames())
        for terminal_index in range(terminals):
            terminal = terminal_index + 1
            terminal_bus = bus_names[terminal_index].lower() if terminal_index < len(bus_names) else ""
            for conductor_index in range(conductors):
                value_index = 2 * ((terminal_index * conductors) + conductor_index)
                if value_index + 1 >= len(powers):
                    continue
                yield [
                    feeder.name,
                    step,
                    f"{hour:.12g}",
                    element_class,
                    element_name.lower(),
                    terminal,
                    terminal_bus,
                    conductor_index + 1,
                    f"{powers[value_index]:.12g}",
                    f"{powers[value_index + 1]:.12g}",
                ]


def export_case(
    feeder: FeederCase,
    steps: int,
    output_dir: Path,
    mode: str,
    step_size_hours: float,
    include_voltages: bool,
    include_branch_flows: bool,
) -> None:
    compile_case(feeder, mode, step_size_hours)
    output_dir.mkdir(parents=True, exist_ok=True)

    voltage_file = output_dir / f"{feeder.key}_qsts_dss_python_voltage_by_step.csv"
    branch_file = output_dir / f"{feeder.key}_qsts_dss_python_branch_power_by_step.csv"

    voltage_rows = 0
    branch_rows = 0
    max_iterations = 0
    converged = True

    voltage_handle = None
    branch_handle = None
    try:
        voltage_writer = None
        branch_writer = None
        if include_voltages:
            voltage_handle = voltage_file.open("w", newline="", encoding="utf-8")
            voltage_writer = csv.writer(voltage_handle)
            voltage_writer.writerow(["case", "step", "hour", "bus", "node", "vmag_pu", "angle_deg"])
        if include_branch_flows:
            branch_handle = branch_file.open("w", newline="", encoding="utf-8")
            branch_writer = csv.writer(branch_handle)
            branch_writer.writerow([
                "case",
                "step",
                "hour",
                "class",
                "element",
                "terminal",
                "bus",
                "conductor",
                "p_kw",
                "q_kvar",
            ])

        for step in range(steps):
            dss.Solution.Solve()
            hour = step * step_size_hours
            converged = converged and bool(dss.Solution.Converged())
            max_iterations = max(max_iterations, int(dss.Solution.Iterations()))
            if voltage_writer is not None:
                rows = list(bus_voltage_rows(feeder, step, hour))
                voltage_writer.writerows(rows)
                voltage_rows += len(rows)
            if branch_writer is not None:
                rows = list(branch_power_rows(feeder, step, hour))
                branch_writer.writerows(rows)
                branch_rows += len(rows)
    finally:
        if voltage_handle is not None:
            voltage_handle.close()
        if branch_handle is not None:
            branch_handle.close()

    print(
        "DSSPY_QSTS_REFERENCE "
        f"feeder={feeder.name} steps={steps} mode={mode} stepSizeHours={step_size_hours:.12g} "
        f"converged={str(converged).lower()} maxIterations={max_iterations} "
        f"voltageRows={voltage_rows} branchPowerRows={branch_rows} outputDir={output_dir}",
        flush=True,
    )
    if not converged:
        raise RuntimeError(f"DSS-Python QSTS reference did not converge for {feeder.name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", action="append", choices=["all", "ckt24", "ieee8500", "8500"])
    parser.add_argument("--steps", type=int, default=24)
    parser.add_argument("--output-dir", type=Path, default=REPO_ROOT / "target" / "qsts-comparison")
    parser.add_argument("--mode", choices=["daily", "yearly", "snapshot"], default="daily")
    parser.add_argument("--step-size-hours", type=float, default=1.0)
    parser.add_argument("--skip-voltages", action="store_true")
    parser.add_argument("--skip-branch-flows", action="store_true")
    args = parser.parse_args()

    include_voltages = not args.skip_voltages
    include_branch_flows = not args.skip_branch_flows
    if not include_voltages and not include_branch_flows:
        raise ValueError("At least one export must be enabled")

    for feeder in selected_cases(args.case or ["all"]):
        export_case(
            feeder,
            args.steps,
            args.output_dir,
            args.mode,
            args.step_size_hours,
            include_voltages,
            include_branch_flows,
        )


if __name__ == "__main__":
    main()
