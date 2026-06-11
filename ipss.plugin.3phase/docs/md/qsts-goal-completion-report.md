# QSTS Goal Completion Report

Date: 2026-06-11

Branch: `qsts-opendss-parity-improvements`

## Goal

Implement the six QSTS improvement tasks, complete the tests and documentation,
and make one focused commit after each main task.

The architectural target was:

- Static power-flow and QSTS paths use static network/device APIs.
- OpenDSS QSTS studies use `OpenDSSStaticDataParser` and `Static3PNetwork`.
- QSTS and static PF do not instantiate or depend on dynamic DStab models.
- Remaining dynamic/DStab APIs stay on the dynamic-study side.

## Completed Commits

| Task | Commit | Summary |
|---|---|---|
| 1 | `8c80b302 test: add QSTS multi-step OpenDSS references` | Added multi-step checked reference coverage for load, PV, and storage terminal powers. |
| 2 | `4852d529 test: add delayed capacitor QSTS reference` | Added delayed CapControl queue operation-count reference coverage. |
| 3 | `d9ce1f53 test: add PV duty inverter QSTS reference` | Added duty-mode PV QSTS coverage with enabled WATTPF inverter control. |
| 4 | `1772d03e test: add storage SOC QSTS reference` | Added storage state-of-charge reference rows and static storage-state verification. |
| 5 | `b4f5f6de docs: map OpenDSS QSTS control behavior` | Added behavior mapping for RegControl, CapControl, ControlQueue, and Reactor. |
| 6 | `95cacbd0 refactor: tighten QSTS static network boundary` | Removed DStab special cases from PF voltage helpers and strengthened QSTS static-boundary tests/docs. |

## Key Evidence Used

### Commit Evidence

Current top six commits on the branch are the six task commits:

```text
95cacbd0 refactor: tighten QSTS static network boundary
b4f5f6de docs: map OpenDSS QSTS control behavior
1772d03e test: add storage SOC QSTS reference
d9ce1f53 test: add PV duty inverter QSTS reference
4852d529 test: add delayed capacitor QSTS reference
8c80b302 test: add QSTS multi-step OpenDSS references
```

### Documentation Evidence

The QSTS TODO and support plan now record the completed items:

- `qsts-implementation-todo.md`
  - Delayed control-queue operation count is checked off.
  - PV duty-curve QSTS with inverter controls enabled is checked off.
  - Storage state-of-charge reference rows are checked off.
  - DStab/static boundary migration item is checked off with concrete evidence.
- `qsts-study-support-plan.md`
  - Source behavior mapping table is checked off.
  - CapControl delayed queue coverage is recorded.
  - DER/storage SOC and duty-inverter coverage are recorded.

Deferred items such as `StorageController`, switched Reactor control, and deeper
RegControl delayed-queue parity remain explicitly open rather than being marked
complete without reference behavior.

### Static Boundary Evidence

The static/QSTS boundary is enforced by code and tests:

- `OpenDSSStaticDataParser` does not expose or accept `DStabNetwork3Phase`.
- `OpenDSSQstsStudyFactory.from(parser)` rejects dynamic OpenDSS parsers.
- `OpenDSSQstsAdapterTest` asserts static parser output does not materialize
  DStab bus/generator classes for QSTS.
- `DistributionPowerFlowAlgorithmImpl` fixed-point voltage update and
  positive-sequence helpers now use the generic `IBus3Phase` phase-voltage
  contract instead of DStab bus special cases.

### Reference Data Evidence

Checked-in reference files added or used for the goal:

- `qsts-multistep-dss-python-device-reference.csv`
- `capcontrol-delayed-dss-python-operation-reference.csv`
- `invcontrol-duty-qsts-dss-python-generator-reference.csv`
- `storage-mini-dss-python-soc-reference.csv`

Mini feeder cases added or used:

- `OpenDSSQstsLoadMini/Master.dss`
- `OpenDSSCapControlMini/DelayedHighVoltageOpen.dss`
- `OpenDSSInvControlMini/DutyWattPF.dss`
- `OpenDSSStorageMini/Master.dss`

## Verification Commands

Focused QSTS/control sweep:

```bash
mvn -pl ipss.plugin.3phase -am clean test \
  -Dtest=OpenDssQstsMultiStepReferenceTest,OpenDssCapControlMiniComparisonTest,OpenDssInvControlMiniComparisonTest,OpenDssStorageMiniComparisonTest,OpenDSSQstsAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
BUILD SUCCESS
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

Broader module gate:

```bash
mvn -pl ipss.plugin.3phase -am test -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
BUILD SUCCESS
Tests run: 301, Failures: 0, Errors: 0, Skipped: 26
```

## Verification Boundary

This goal did not include, and I did not run, a large-feeder QSTS parity study
that compares every time step against DSS-Python for bus voltages and branch
flows.

What was verified for large feeders in the existing repo artifacts is static
power-flow parity, mostly controls-off voltage comparison and device-level
diagnostics for feeders such as IEEE13, IEEE123, IEEE8500, Ckt7, and Ckt24.
Those are not the same as a time-step-by-time-step QSTS comparison.

What was verified for QSTS in this goal is mini-case and focused behavior
parity:

- multi-step load/PV/storage terminal P/Q references;
- delayed capacitor control queue operation counts;
- PV duty-curve QSTS with enabled inverter control;
- storage SOC carryover;
- static parser/QSTS boundary enforcement.

There are QSTS large-feeder performance benchmark notes for Ckt24, including a
240-step repeated-state run, but that benchmark is performance-oriented and does
not compare each QSTS step to DSS-Python bus voltages or branch flows.

### Large-Feeder Comparison Path Added

The existing large-feeder QSTS setup can now be used to generate detailed
DSS-Python references for Ckt24 and IEEE8500:

```bash
python3 ipss.plugin.3phase/src/test/python/export_qsts_large_feeder_reference.py \
  --case ckt24 \
  --steps 24 \
  --output-dir target/qsts-comparison
```

The exporter uses the same checked-in feeder masters and controls-off settings
as the large-feeder QSTS performance benchmark, then writes:

- `ckt24_qsts_dss_python_voltage_by_step.csv`
- `ckt24_qsts_dss_python_branch_power_by_step.csv`
- `ieee8500_qsts_dss_python_voltage_by_step.csv`
- `ieee8500_qsts_dss_python_branch_power_by_step.csv`

The CSV keys include case, step, hour, bus/element, terminal/conductor, voltage
magnitude/angle, and branch terminal P/Q. These files provide the reference side
for a per-step static-QSTS parity comparison.

The InterPSS side now has a matching manual voltage export using the static
parser/QSTS path:

```bash
mvn -pl ipss.plugin.3phase -am test \
  -Dtest=QstsLargeFeederComparisonExport \
  -Dqsts.compare.case=ckt24 \
  -Dqsts.compare.steps=24 \
  -Dqsts.compare.outputDir=target/qsts-comparison \
  -Dsurefire.failIfNoSpecifiedTests=false
```

It writes `ckt24_qsts_interpss_voltage_by_step.csv` or
`ieee8500_qsts_interpss_voltage_by_step.csv`. The InterPSS QSTS result model
already records per-step bus voltages and device powers, so this export is
static-network-only and does not depend on DStab. Per-step static branch-flow
comparison still needs either a static branch-flow sampler in QSTS results or a
dedicated diagnostic step hook. That should be implemented without reintroducing
DStab branch APIs into the static QSTS path.

The generated voltage files can be compared with:

```bash
python3 ipss.plugin.3phase/src/test/python/compare_qsts_voltage_reference.py \
  --dss-voltage target/qsts-comparison/ckt24_qsts_dss_python_voltage_by_step.csv \
  --interpss-voltage ipss.plugin.3phase/target/qsts-comparison/ckt24_qsts_interpss_voltage_by_step.csv \
  --magnitude-tolerance 0.003 \
  --angle-tolerance 1.0
```

Smoke datapoints from one-step Ckt24 and IEEE8500 DSS-Python exports:

- Ckt24 DSS-Python one-step export converged with 7,522 voltage rows and
  16,728 branch-power rows.
- IEEE8500 DSS-Python one-step export converged with 8,531 voltage rows and
  19,446 branch-power rows.
- Ckt24 InterPSS one-step static-QSTS voltage export converged with 18,177
  voltage rows.
- Ckt24 voltage comparison, excluding zero-voltage DSS nodes, found 7,160
  common energized step/bus/phase keys. The maximum voltage magnitude mismatch
  was 0.0029341957 pu at `0:n283892:C`; the maximum angle mismatch was
  0.331285484 degrees at `0:g2100bk4500_n283756_sec:B`. It fails a 0.001 pu
  magnitude tolerance but passes a 0.003 pu magnitude tolerance and 1 degree
  angle tolerance.

### IEEE8500 Profile/QSTS Inventory

The checked-in IEEE8500 `Master-InterPSS.dss` and `Master.dss` files are
balanced static snapshots. They redirect balanced `Loads.dss`, capacitors,
controls, regulators, lines, and transformers, but do not bind the main loads to
daily/yearly/duty `LoadShape` records.

The local IEEE8500 folder does contain profile-related material that can be used
for targeted QSTS data testing:

- `P174_Run_360kW_PV.DSS` compiles `Master-unbal.dss`, adds `Generator.G1`,
  defines `Loadshape.PVCurve`, binds `generator.g1.duty=PVcurve`, and solves
  `mode=duty number=2900 stepsize=1`.
- `Normalized-1s-2900-pts.CSV` is present and contains 2,913 one-second PV
  multiplier samples; existing parser tests already verify this shape can be
  parsed into QSTS profile metadata.
- `CloudTransient.dss` defines `Loadshape.Ramp` and a duty generator, but its
  referenced `solarramp.csv` file is not checked in.
- `Feeder_Loads.dss` references `Yearly=Load_Res`, but no
  `Loadshape.Load_Res` definition was found in the checked-in IEEE8500 folder.
- `IEEE8500u_EXP_Profile.csv` is an exported voltage-profile artifact, not a
  load or DER time-series input profile.

Conclusion: IEEE8500 is usable now as a large PV duty-profile parser/QSTS
candidate if we create an InterPSS-compatible static master around the
`P174_Run_360kW_PV.DSS` pattern. It is not yet a ready large load-profile QSTS
case, and it should not be treated as one until a real `Load_Res`/low-load
profile source is added and bound.

External search did identify an open IEEE13/IEEE123/IEEE8500 Volt-VAR dataset
and test environment as a possible future source of historical operating
profiles. That dataset should be reviewed separately before importing any files,
because it may use a different feeder variant and control/action model than the
checked-in OpenDSS IEEE8500 case.

## Final State

- Branch: `qsts-opendss-parity-improvements`
- Working tree before this report: clean
- Six implementation/documentation commits completed
- Focused QSTS test sweep passed
- Broader `ipss.plugin.3phase` Maven test gate passed

This completes the requested six-task QSTS improvement goal.
