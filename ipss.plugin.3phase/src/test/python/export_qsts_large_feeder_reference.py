#!/usr/bin/env python3
"""Export DSS-Python per-step references for large-feeder QSTS comparisons."""

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


def compile_case(
    feeder: FeederCase,
    mode: str,
    step_size_hours: float,
    control_mode: str,
    max_control_iterations: int,
    reg_controls_enabled: bool,
    cap_controls_enabled: bool,
) -> None:
    dss.Basic.ClearAll()
    previous_cwd = Path.cwd()
    try:
        os.chdir(feeder.folder)
        dss.Text.Command(f'compile "{feeder.master_file}"')
        dss.Text.Command(f"set controlmode={control_mode}")
        dss.Text.Command(f"set maxcontroliter={max_control_iterations}")
        if not reg_controls_enabled:
            dss.Text.Command("batchedit regcontrol..* enabled=no")
        if not cap_controls_enabled:
            dss.Text.Command("batchedit capcontrol..* enabled=no")
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


def with_master_file(feeder: FeederCase, master_file: str | None) -> FeederCase:
    if not master_file:
        return feeder
    return FeederCase(feeder.key, feeder.name, feeder.folder, master_file)


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


def safe_node_order(count: int) -> list[int]:
    try:
        nodes = list(dss.CktElement.NodeOrder())
    except Exception:
        nodes = []
    if len(nodes) >= count:
        return [int(node) for node in nodes[:count]]
    return [int(node) for node in nodes] + list(range(len(nodes) + 1, count + 1))


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
        node_order = safe_node_order(terminals * conductors)
        for terminal_index in range(terminals):
            terminal = terminal_index + 1
            terminal_bus = bus_names[terminal_index].lower() if terminal_index < len(bus_names) else ""
            for conductor_index in range(conductors):
                order_index = (terminal_index * conductors) + conductor_index
                value_index = 2 * order_index
                if value_index + 1 >= len(powers):
                    continue
                node = int(node_order[order_index]) if order_index < len(node_order) else conductor_index + 1
                yield [
                    feeder.name,
                    step,
                    f"{hour:.12g}",
                    element_class,
                    element_name.lower(),
                    terminal,
                    terminal_bus,
                    node,
                    f"{powers[value_index]:.12g}",
                    f"{powers[value_index + 1]:.12g}",
                ]


def load_power_rows(feeder: FeederCase, step: int, hour: float) -> Iterable[list[object]]:
    for load_name in dss.Loads.AllNames() or []:
        dss.Loads.Name(load_name)
        element_name = f"load.{load_name}".lower()
        dss.Circuit.SetActiveElement(element_name)
        powers = list(dss.CktElement.Powers())
        if not powers:
            continue
        conductors = int(dss.CktElement.NumConductors())
        bus_names = list(dss.CktElement.BusNames())
        node_order = safe_node_order(conductors)
        terminal_bus = bus_names[0].lower() if bus_names else ""
        for conductor_index in range(conductors):
            value_index = 2 * conductor_index
            if value_index + 1 >= len(powers):
                continue
            node = int(node_order[conductor_index]) if conductor_index < len(node_order) else conductor_index + 1
            phase = {1: "A", 2: "B", 3: "C"}.get(node)
            if phase is None:
                continue
            yield [
                feeder.name,
                step,
                f"{hour:.12g}",
                element_name,
                terminal_bus,
                phase,
                f"{powers[value_index]:.12g}",
                f"{powers[value_index + 1]:.12g}",
            ]


def regulator_tap_rows(feeder: FeederCase, step: int, hour: float) -> Iterable[list[object]]:
    for control_name in dss.RegControls.AllNames() or []:
        dss.RegControls.Name(control_name)
        yield [
            feeder.name,
            step,
            f"{hour:.12g}",
            control_name.lower(),
            dss.RegControls.Transformer().lower(),
            dss.RegControls.Winding(),
            dss.RegControls.TapWinding(),
            dss.RegControls.TapNumber(),
            f"{dss.RegControls.PTRatio():.12g}",
            f"{dss.RegControls.ForwardVreg():.12g}",
            f"{dss.RegControls.ForwardBand():.12g}",
            f"{dss.RegControls.VoltageLimit():.12g}",
        ]


def export_case(
    feeder: FeederCase,
    steps: int,
    output_dir: Path,
    mode: str,
    step_size_hours: float,
    control_mode: str,
    max_control_iterations: int,
    reg_controls_enabled: bool,
    cap_controls_enabled: bool,
    include_voltages: bool,
    include_branch_flows: bool,
    include_load_powers: bool,
) -> None:
    compile_case(feeder, mode, step_size_hours, control_mode, max_control_iterations,
                 reg_controls_enabled, cap_controls_enabled)
    output_dir.mkdir(parents=True, exist_ok=True)

    tag = control_tag(control_mode, reg_controls_enabled, cap_controls_enabled)
    voltage_file = output_dir / f"{feeder.key}_qsts_{tag}_dss_python_voltage_by_step.csv"
    branch_file = output_dir / f"{feeder.key}_qsts_{tag}_dss_python_branch_power_by_step.csv"
    load_file = output_dir / f"{feeder.key}_qsts_{tag}_dss_python_load_power_by_step.csv"
    regulator_file = output_dir / f"{feeder.key}_qsts_{tag}_dss_python_regulator_taps_by_step.csv"

    voltage_rows = 0
    branch_rows = 0
    load_rows = 0
    regulator_rows = 0
    max_iterations = 0
    converged = True

    voltage_handle = None
    branch_handle = None
    load_handle = None
    regulator_handle = None
    try:
        voltage_writer = None
        branch_writer = None
        load_writer = None
        regulator_handle = regulator_file.open("w", newline="", encoding="utf-8")
        regulator_writer = csv.writer(regulator_handle)
        regulator_writer.writerow([
            "case",
            "step",
            "hour",
            "control",
            "transformer",
            "winding",
            "tap_winding",
            "tap_number",
            "pt_ratio",
            "vreg",
            "band",
            "voltage_limit",
        ])
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
        if include_load_powers:
            load_handle = load_file.open("w", newline="", encoding="utf-8")
            load_writer = csv.writer(load_handle)
            load_writer.writerow([
                "case",
                "step",
                "hour",
                "device",
                "bus",
                "phase",
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
            if load_writer is not None:
                rows = list(load_power_rows(feeder, step, hour))
                load_writer.writerows(rows)
                load_rows += len(rows)
            rows = list(regulator_tap_rows(feeder, step, hour))
            regulator_writer.writerows(rows)
            regulator_rows += len(rows)
    finally:
        if voltage_handle is not None:
            voltage_handle.close()
        if branch_handle is not None:
            branch_handle.close()
        if load_handle is not None:
            load_handle.close()
        if regulator_handle is not None:
            regulator_handle.close()

    print(
        "DSSPY_QSTS_REFERENCE "
        f"feeder={feeder.name} steps={steps} mode={mode} stepSizeHours={step_size_hours:.12g} "
        f"controlMode={control_mode} maxControlIterations={max_control_iterations} "
        f"regControlsEnabled={str(reg_controls_enabled).lower()} "
        f"capControlsEnabled={str(cap_controls_enabled).lower()} "
        f"masterFile={feeder.master_file} "
        f"converged={str(converged).lower()} maxIterations={max_iterations} "
        f"voltageRows={voltage_rows} branchPowerRows={branch_rows} loadPowerRows={load_rows} "
        f"regulatorTapRows={regulator_rows} outputDir={output_dir}",
        flush=True,
    )
    if not converged:
        raise RuntimeError(f"DSS-Python QSTS reference did not converge for {feeder.name}")


def require_enabled_controls(
    control_mode: str,
    max_control_iterations: int,
    reg_controls_enabled: bool,
    cap_controls_enabled: bool,
    allow_disabled_controls: bool,
) -> None:
    if allow_disabled_controls:
        return
    if control_mode == "off":
        raise ValueError(
            "Large-feeder QSTS comparisons must run with controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if max_control_iterations < 100:
        raise ValueError(
            "Large-feeder QSTS comparisons must allow at least 100 control iterations; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if not reg_controls_enabled:
        raise ValueError(
            "Large-feeder QSTS comparisons must keep regulator controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if not cap_controls_enabled:
        raise ValueError(
            "Large-feeder QSTS comparisons must keep capacitor controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", action="append", choices=["all", "ckt24", "ieee8500", "8500"])
    parser.add_argument("--master-file", help="Override the selected feeder master file")
    parser.add_argument("--steps", type=int, default=24)
    parser.add_argument("--output-dir", type=Path, default=REPO_ROOT / "target" / "qsts-comparison")
    parser.add_argument("--mode", choices=["daily", "yearly", "snapshot"], default="daily")
    parser.add_argument("--step-size-hours", type=float, default=1.0)
    parser.add_argument("--control-mode", choices=["off", "static", "time", "event"], default="static")
    parser.add_argument("--max-control-iterations", type=int, default=100)
    parser.add_argument("--disable-reg-controls", action="store_true")
    parser.add_argument("--disable-cap-controls", action="store_true")
    parser.add_argument("--allow-disabled-controls", action="store_true")
    parser.add_argument("--skip-voltages", action="store_true")
    parser.add_argument("--skip-branch-flows", action="store_true")
    parser.add_argument("--skip-load-powers", action="store_true")
    args = parser.parse_args()

    include_voltages = not args.skip_voltages
    include_branch_flows = not args.skip_branch_flows
    include_load_powers = not args.skip_load_powers
    require_enabled_controls(
        args.control_mode,
        args.max_control_iterations,
        not args.disable_reg_controls,
        not args.disable_cap_controls,
        args.allow_disabled_controls,
    )
    if not include_voltages and not include_branch_flows and not include_load_powers:
        raise ValueError("At least one export must be enabled")

    for feeder in selected_cases(args.case or ["all"]):
        feeder = with_master_file(feeder, args.master_file)
        export_case(
            feeder,
            args.steps,
            args.output_dir,
            args.mode,
            args.step_size_hours,
            args.control_mode,
            args.max_control_iterations,
            not args.disable_reg_controls,
            not args.disable_cap_controls,
            include_voltages,
            include_branch_flows,
            include_load_powers,
        )


def control_tag(control_mode: str, reg_controls_enabled: bool, cap_controls_enabled: bool) -> str:
    tag = f"controls_{control_mode.lower()}"
    if not reg_controls_enabled:
        tag += "_noreg"
    if not cap_controls_enabled:
        tag += "_nocap"
    return tag


if __name__ == "__main__":
    main()
