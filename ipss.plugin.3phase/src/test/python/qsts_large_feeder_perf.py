#!/usr/bin/env python3
"""DSS-Python benchmark matching the InterPSS larger-feeder QSTS benchmark."""

from __future__ import annotations

import argparse
import os
import statistics
import time
from dataclasses import dataclass
from pathlib import Path

import opendssdirect as dss


REPO_ROOT = Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class FeederCase:
    name: str
    folder: Path
    master_file: str


CASES = {
    "ckt24": FeederCase(
        "Ckt24",
        REPO_ROOT / "testData" / "feeder" / "Ckt24",
        "master_ckt24_interpss.dss",
    ),
    "ieee8500": FeederCase(
        "IEEE8500",
        REPO_ROOT / "testData" / "feeder" / "IEEE8500",
        "Master-InterPSS.dss",
    ),
}


@dataclass(frozen=True)
class RunSummary:
    feeder: str
    phase: str
    run: int
    requested_steps: int
    converged: bool
    elapsed_seconds: float
    max_iterations: int

    @property
    def elapsed_millis(self) -> float:
        return self.elapsed_seconds * 1000.0

    @property
    def millis_per_step(self) -> float:
        return self.elapsed_millis / max(1, self.requested_steps)

    def metric_line(self) -> str:
        return (
            "DSSPY_QSTS_PERF "
            f"feeder={self.feeder} phase={self.phase} run={self.run} "
            f"requestedSteps={self.requested_steps} converged={str(self.converged).lower()} "
            f"elapsedMillis={self.elapsed_millis:.3f} "
            f"msPerStep={self.millis_per_step:.6f} "
            f"maxIterations={self.max_iterations}"
        )


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


def run_case(
    feeder: FeederCase,
    steps: int,
    phase: str,
    run: int,
    mode: str,
    step_size_hours: float,
    control_mode: str,
    max_control_iterations: int,
    reg_controls_enabled: bool,
    cap_controls_enabled: bool,
) -> RunSummary:
    compile_case(feeder, mode, step_size_hours, control_mode, max_control_iterations,
                 reg_controls_enabled, cap_controls_enabled)
    converged = True
    max_iterations = 0
    start = time.perf_counter()
    for _ in range(steps):
        dss.Solution.Solve()
        converged = converged and bool(dss.Solution.Converged())
        max_iterations = max(max_iterations, int(dss.Solution.Iterations()))
    elapsed = time.perf_counter() - start
    summary = RunSummary(
        feeder=feeder.name,
        phase=phase,
        run=run,
        requested_steps=steps,
        converged=converged,
        elapsed_seconds=elapsed,
        max_iterations=max_iterations,
    )
    print(summary.metric_line(), flush=True)
    return summary


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
    return FeederCase(feeder.name, feeder.folder, master_file)


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
            "Large-feeder QSTS performance comparisons must run with controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if max_control_iterations < 100:
        raise ValueError(
            "Large-feeder QSTS performance comparisons must allow at least 100 control iterations; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if not reg_controls_enabled:
        raise ValueError(
            "Large-feeder QSTS performance comparisons must keep regulator controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )
    if not cap_controls_enabled:
        raise ValueError(
            "Large-feeder QSTS performance comparisons must keep capacitor controls enabled; "
            "use --allow-disabled-controls only for frozen-state diagnostics"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", action="append", choices=["all", "ckt24", "ieee8500", "8500"])
    parser.add_argument("--master-file", help="Override the selected feeder master file")
    parser.add_argument("--warmup-steps", type=int, default=24)
    parser.add_argument("--steps", type=int, default=240)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--mode", choices=["daily", "yearly", "snapshot", "duty"], default="daily")
    parser.add_argument("--step-size-hours", type=float, default=1.0)
    parser.add_argument("--control-mode", choices=["off", "static", "time", "event"], default="static")
    parser.add_argument("--max-control-iterations", type=int, default=100)
    parser.add_argument("--disable-reg-controls", action="store_true")
    parser.add_argument("--disable-cap-controls", action="store_true")
    parser.add_argument("--allow-disabled-controls", action="store_true")
    args = parser.parse_args()

    cases = selected_cases(args.case or ["all"])
    reg_controls_enabled = not args.disable_reg_controls
    cap_controls_enabled = not args.disable_cap_controls
    require_enabled_controls(
        args.control_mode,
        args.max_control_iterations,
        reg_controls_enabled,
        cap_controls_enabled,
        args.allow_disabled_controls,
    )
    print(
        "DSSPY_QSTS_PERF_CONFIG "
        f"cases={','.join(case.name for case in cases)} "
        f"warmupSteps={args.warmup_steps} measureSteps={args.steps} "
        f"repeats={args.repeats} mode={args.mode} stepSizeHours={args.step_size_hours:.12g} "
        f"controlMode={args.control_mode} "
        f"maxControlIterations={args.max_control_iterations} "
        f"regControlsEnabled={str(reg_controls_enabled).lower()} "
        f"capControlsEnabled={str(cap_controls_enabled).lower()}"
    )
    for feeder in cases:
        feeder = with_master_file(feeder, args.master_file)
        print(
            "DSSPY_QSTS_PERF_CASE "
            f"feeder={feeder.name} masterFile={feeder.master_file}",
            flush=True,
        )
        warmup = run_case(feeder, args.warmup_steps, "warmup", 0,
                          args.mode, args.step_size_hours,
                          args.control_mode, args.max_control_iterations,
                          reg_controls_enabled, cap_controls_enabled)
        if not warmup.converged:
            raise RuntimeError(f"Warm-up failed for {feeder.name}")

        measured = [run_case(feeder, args.steps, "measured", run,
                             args.mode, args.step_size_hours,
                             args.control_mode, args.max_control_iterations,
                             reg_controls_enabled, cap_controls_enabled)
                    for run in range(1, args.repeats + 1)]
        if not all(summary.converged for summary in measured):
            raise RuntimeError(f"Measured run failed for {feeder.name}")
        ms_per_step = [summary.millis_per_step for summary in measured]
        print(
            "DSSPY_QSTS_PERF_AGG "
            f"feeder={feeder.name} runs={len(measured)} "
            f"avgMsPerStep={statistics.mean(ms_per_step):.6f} "
            f"medianMsPerStep={statistics.median(ms_per_step):.6f} "
            f"minMsPerStep={min(ms_per_step):.6f}",
            flush=True,
        )


if __name__ == "__main__":
    main()
