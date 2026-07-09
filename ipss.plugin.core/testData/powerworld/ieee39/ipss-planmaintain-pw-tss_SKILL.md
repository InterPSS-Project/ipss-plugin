---
name: ipss-planmaintain-pw-tss
description: Port InterPSS PlanMaintainModel to/from PowerWorld TSS auxiliary files (AUX, CSV, TSB). Use when exporting plan/maintain JSON to PowerWorld, importing PW schedules into PlanMaintainModel, wiring Aux2PlanMaintainAdapter, or debugging genNotFoundList / device name mismatches in future-state DCLF runs.
metadata:
  short-description: PlanMaintainModel ↔ PowerWorld TSS porting
---

# PlanMaintainModel ↔ PowerWorld TSS Porting

Bidirectional workflow between InterPSS **PlanMaintainModel** (future-state planning) and PowerWorld Simulator **Time Step Simulation (TSS)** text artifacts.

Canonical mapping doc: `ipss-plugin/ipss.plugin.core/docs/data_fmt/plan-maintain-to-powerworld-tss.md`

## When to use

- Export a `PlanMaintainModel` JSON fixture to PowerWorld `.aux` / `.csv` for Simulator TSS
- Import PowerWorld TSS files back into `PlanMaintainModel` for `FStateDclfAlgorithm`
- Port a new case (IEEE39 day-ahead, week plan, etc.)
- Debug `genNotFoundList` / `loadNotFoundList` after switching from JSON to AUX-loaded plans

## Architecture (two directions)

```
PlanMaintainModel JSON  ──generate_ieee39_*_pw_tss.py──►  PW TSS artifacts
                                                              │
                                                              ▼
PlanMaintainModel       ◄── Aux2PlanMaintainAdapter ────  *_schedules.aux
                                                          *_timepoints.csv
                                                          *_outages.csv (opt)
```

Java adapter package: `org.interpss.plugin.fstate.aux_fmt`

| Class | Role |
|---|---|
| `Aux2PlanMaintainAdapter` | Entry: `createDayAheadModel(dir)`, `createWeekModel(dir)`, or `load(AuxTssInput)` |
| `AuxTssScheduleAuxParser` | `TSSchedule` / `TSScheduleSub` + `<SUBDATA SchedPoint>` |
| `AuxTimepointsCsvParser` | Horizon from CSV |
| `AuxOutageCsvParser` | Optional maintenance fallback |
| `AuxScheduleEvaluator` | Step-hold evaluation at each timestamp |
| `Aux2PlanMaintainModelMapper` | → `TimePointRec[]`, `EquipmentMaintainRec` |
| `AuxParseUtil` | Shared AUX tokenization (`org.interpss.plugin.aux_fmt.util`) |

Build target model via `PlanMaintainModelBuilder` in `ipss-core`.

---

## Phase 1 — Export (InterPSS → PowerWorld)

### Inputs

| Item | Typical path |
|---|---|
| Plan JSON (day-ahead) | `ipss-core/ipss.test.core/testdata/json/ieee39_dayahead_plan_maintain_plan.json` |
| Plan JSON (week) | `ipss-core/ipss.test.core/testdata/json/ieee39_week_plan_maintain_plan.json` |
| PSSE case | `ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw` |
| Branch names | `ipss-core/ipss.test.core/testdata/json/ieee39.json` (`branchAry`, `branchCode=LINE`) |
| Shared utilities | `ipss.plugin.core/testData/powerworld/ieee39/pw_tss_common.py` |

### Generate day-ahead artifacts

```bash
python3 ipss-plugin/ipss.plugin.core/testData/powerworld/ieee39/generate_ieee39_dayahead_pw_tss.py
```

Output under `ipss.plugin.core/testData/powerworld/ieee39/dayahead/`:

| File | Purpose |
|---|---|
| `IEEE39bus_v30_labeled.aux` | Labels on gens/loads/branches (parent `ieee39/` dir) |
| `golden_tsschedule_reference.aux` | Minimal syntax reference (parent dir) |
| `*_dayahead_schedules.aux` | MW + branch-status schedules + subscriptions |
| `*_dayahead_timepoints.csv` | 96 timestamps, 15-min, ISO8601 column |
| `*_dayahead_outages.csv` | PWCSV scheduled-outage rows (parallel to TSS) |
| `*_dayahead_run.aux` | Simulator orchestration SCRIPT |
| `*_dayahead.tsb` | Text manifest (not binary — see TSB note) |

### Generate week artifacts

```bash
python3 ipss-plugin/ipss.plugin.core/testData/powerworld/ieee39/generate_ieee39_week_pw_tss.py
```

Output under `ipss.plugin.core/testData/powerworld/ieee39/week/`:

| File | Purpose |
|---|---|
| `*_week_schedules.aux` | 29 numeric + 3 boolean schedules + subscriptions (32 total) |
| `*_week_timepoints.csv` | 168 hourly timestamps |
| `*_week_outages.csv` | PWCSV outage rows (3 maintenance windows) |
| `*_week_run.aux` | Simulator SCRIPT (`SetScheduleWindow(..., 60, MINUTES)`) |
| `*_week.tsb` | Text manifest (168 timestamps) |

Copy adapter inputs to `ipss.test.plugin.core/testData/powerworld/ieee39/dayahead/` or `.../week/`.

### Field mapping (export)

| InterPSS | PowerWorld |
|---|---|
| `genMap.{name}.p` | `TSSchedule` `Sched_Gen_{name}` (Numeric) |
| `loadMap.{name}.p` | `TSSchedule` `Sched_Load_{name}` (Numeric) |
| `EquipmentMaintainRec` (Inactive) | `Sched_Maint_{branch}` boolean OPEN/CLOSED |
| Device name `Bus39-G1` | `Label` + `TSScheduleSub.ObjectIdentifier` |
| Branch `Bus29_to_Bus26_cirId_1` | `Label` + `Sched_Maint_*` |

Schedule naming conventions:

- Gen: `Sched_Gen_Bus31-G1`
- Load: `Sched_Load_Bus39-L1`
- Maintenance: `Sched_Maint_Bus29_to_Bus26_cirId_1`

`SchedPoint` rows: `PointType 0` = numeric MW, `PointType 1` = boolean (`CLOSED`/`OPEN`).

### TSB limitation

PowerWorld `.tsb` is proprietary binary. Committed files are **text manifests** only. Export binary from Simulator using `*_generate_tsb.aux` before `TimeStepLoadTSB` in production.

### Multi-point SchedPoint export

Generators use `compress_step_hold()` on flattened time-point series. IEEE39 fixtures emit ~**4** numeric `SchedPoint` rows per gen/load device (T0, T1, T2, T3+ flat). Maintenance schedules use 3 boolean points (CLOSED → OPEN → CLOSED).

---

## Phase 2 — Import (PowerWorld → PlanMaintainModel)

### Adapter file discovery

`Aux2PlanMaintainAdapter.createDayAheadModel(Path dir)` finds exactly one file per suffix:

| Suffix | Required |
|---|---|
| `dayahead_schedules.aux` | Yes |
| `dayahead_timepoints.csv` | Yes |
| `dayahead_outages.csv` | Optional (fallback when no `Sched_Maint_*` schedules) |

`createWeekModel(Path dir)` uses `week_schedules.aux`, `week_timepoints.csv`, `week_outages.csv`.

Example valid names: `ieee39_dayahead_schedules.aux`, `ieee39_week_schedules.aux`.

Explicit paths:

```java
PlanMaintainModel model = Aux2PlanMaintainAdapter.load(new AuxTssInput(
    schedulesAux, timepointsCsv, outagesCsv,
    FSPlanMaintainModelType.DayAhead, null));
```

Not used by adapter: labeled base AUX, binary `.tsb`, run SCRIPT.

### Reverse mapping

| PowerWorld | PlanMaintainModel |
|---|---|
| `Sched_Gen_{name}` step-hold MW | `TimePointRec[i].genMap.get(name).p` |
| `Sched_Load_{name}` step-hold MW | `TimePointRec[i].loadMap.get(name).p` |
| `Sched_Maint_{branch}` OPEN intervals | `EquipmentMaintainRec` (`Inactive`, `Acline`) |
| CSV ISO8601 column | `point2TimeMap`, interval, count |

---

## Phase 3 — Wire into FState DCLF

### Device naming (critical)

PSSE import names devices `Gen:1(31)` / `Load:1(31)`. Plan JSON and PW schedules use **`Bus31-G1`** / **`Bus31-L1`**.

Before running `FStateDclfAlgorithm`, rename network devices:

```java
IEEE39_RAW_Info_Sample.addInfo2Network(aclfNet);
// applyInterpssDeviceNames: Gen:* → Bus{id}-G{n}, Load:* → Bus{id}-L{n}
// then createAclfGenUIDLookupTable / createAclfLoadUIDLookupTable
```

If names are not aligned, `processPlanDataInfo` reports **`genNotFoundList`** / **`loadNotFoundList`** with plan MW that never reaches the network.

### End-to-end samples

Day-ahead DCLF: `ipss.plugin.core/src/sample/java/org/interpss/fstate/Aux_FSPluginDclfAlgoRunSample.java`

Week DCLF: `ipss.plugin.core/src/sample/java/org/interpss/fstate/Aux_FSPluginWeekDclfAlgoRunSample.java`

```java
AclfNetwork aclfNet = IEEE39_RAW_Info_Sample.loadIEEE39Raw();
PlanMaintainModel model = Aux2PlanMaintainAdapter.createWeekModel(
    Path.of("ipss.plugin.core/testData/powerworld/ieee39/week"));
FStateDclfAlgorithm fsAlgo = new FStateDclfAlgorithm(aclfNet, model, new FStateAlgoConfig());
fsAlgo.buildFStateAlgo();
new FStateDclfAlgoHelper(fsAlgo).processPlanDataInfo(true);
fsAlgo.performAssessment(false);
```

JSON baseline sample (same DCLF flow, JSON plan): `FSPluginDclfAlgoRunSample.java`.

### Path conventions

| Context | Base path |
|---|---|
| Sample `main` | `ipss.plugin.core/testData/...` from `ipss-plugin` repo root |
| JUnit (`ipss.test.plugin.core`) | `testData/...` relative to test module CWD |
| Test fixture network | `IEEE39Raw_FState_TestFixture` mirrors `IEEE39_RAW_Info_Sample` |

---

## Phase 4 — Tests and validation

### Automated tests

```bash
# Adapter unit tests
mvn -pl ipss.test.plugin.core test -Dtest=PowerWorld2PlanMaintainAdapterTest
mvn -pl ipss.test.plugin.core test -Dtest=PowerWorld2PlanMaintainWeekAdapterTest

# Full DCLF integration (AUX plan)
mvn -pl ipss.test.plugin.core test -Dtest=AuxFSPluginDclfAlgoRunTest
mvn -pl ipss.test.plugin.core test -Dtest=AuxFSPluginWeekDclfAlgoRunTest
```

All four are in `CorePluginTestSuite`.

### Adapter test checklist (day-ahead)

- [ ] `DayAhead` plan type, 96 points, 15-min interval
- [ ] 10 gens, 19 loads at T0
- [ ] 2 maintenance records with correct windows
- [ ] MW profile: T0/T1/T2 vary, T3 = T0 (`Bus31-G1` ≈ 572.83 / 601.48 / 544.19)
- [ ] Parser: 31 schedules + 31 subscriptions; gen schedule has 4 SchedPoints

### Adapter test checklist (week)

- [ ] `Week` plan type, 168 points, 60-min interval, 7 periods
- [ ] 3 maintenance records (Mon/Wed/Fri windows)
- [ ] MW profile matches `WeekMaintainPlanTest` baseline
- [ ] Parser: 32 schedules + 32 subscriptions

### Manual PowerWorld checklist

1. Load `*_labeled.aux` — labels resolve
2. Load `golden_tsschedule_reference.aux` — syntax OK
3. Load binary TSB — 96 (day-ahead) or 168 (week) time points in TSS Summary
4. Run `*_run.aux` — no import errors
5. Spot-check outage windows (day-ahead: T32/T56; week: Mon 08:00, Wed 10:00, Fri 14:00)

---

## Porting a new case (checklist)

```
Task Progress:
- [ ] 1. Obtain PlanMaintainModel JSON + PSSE case + ieee*.json branch names
- [ ] 2. Clone/adapt generate_*_pw_tss.py (horizon, interval, naming prefix)
- [ ] 3. Generate labeled.aux (gen/load/branch Labels = InterPSS names)
- [ ] 4. Generate schedules.aux + timepoints.csv + outages.csv
- [ ] 5. Add golden_tsschedule_reference.aux (minimal syntax probe)
- [ ] 6. Copy adapter inputs to ipss.test.plugin.core/testData/...
- [ ] 7. Add/extend PowerWorld2PlanMaintainAdapterTest assertions
- [ ] 8. Add sample + AuxFSPlugin*DclfAlgoRunTest if DCLF integration needed
- [ ] 9. Ensure network device names match plan schedule names
- [ ] 10. Update plan-maintain-to-powerworld-tss.md
```

For **week** plans: use `generate_ieee39_week_pw_tss.py`, `createWeekModel(dir)`, `week_*` file suffixes, and `FSPlanMaintainModelType.Week`.

---

## Parser patterns (AUX)

Follow `AuxContingencyParser` for concise `OBJECT (fields) { rows }` blocks, but TSS schedules need **SUBDATA**:

```text
TSSchedule (ScheduleName, ValueType, ...)
{
  "Sched_Gen_Bus31-G1" "Numeric" ...
  <SUBDATA SchedPoint>
    06/27/2026 12:00:00 AM 0 572.834900 NO "" ""
  </SUBDATA>,
}
```

Date formats:

- SchedPoint: `MM/dd/yyyy hh:mm:ss a` (e.g. `06/27/2026 08:00:00 AM`)
- Outages CSV: `MM/dd/yyyy HH:mm`

---

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `genNotFoundList` with `Bus31-G1` | PSSE names not renamed | Call `IEEE39_RAW_Info_Sample.addInfo2Network` before DCLF |
| Adapter `Expected exactly one *dayahead_schedules.aux` | Wrong filename suffix | Rename to `*dayahead_schedules.aux` or `*week_schedules.aux` |
| MW mismatch at T1/T2 | Old flat T0-only export | Regenerate with `pw_tss_common.compress_step_hold` |
| TSB load fails | Text manifest, not binary | Export from Simulator |
| Branch maint not found | Label mismatch | Use `ieee39.json` LINE branch names in labeled.aux |
| Week plan 26k-line JSON | Long horizon | Separate generator; may need non-flat schedule export |

---

## Reference links

- Mapping doc: `ipss-plugin/ipss.plugin.core/docs/data_fmt/plan-maintain-to-powerworld-tss.md`
- Contingency AUX pattern: `org.interpss.plugin.contingency.aux_fmt`
- Plan model builder: `com.interpss.algo.fstate.plan.PlanMaintainModelBuilder`
- Future-state architecture: `ipss-core/ipss.core_EMF/docs/md/notes/fstate-architecture.md`
