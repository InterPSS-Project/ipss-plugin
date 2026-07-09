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
| `Aux2PlanMaintainAdapter` | Entry: `createDayAheadModel(dir)` or `load(AuxTssInput)` |
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
| Plan JSON | `ipss.plugin.core/testData/psse/v30/ieee39_dayahead_plan_maintain_plan.json` |
| PSSE case | `ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw` |
| Branch names | `ipss-core/ipss.test.core/testdata/json/ieee39.json` (`branchAry`, `branchCode=LINE`) |

### Generate artifacts

```bash
python3 ipss-plugin/ipss.plugin.core/testData/powerworld/ieee39/generate_ieee39_dayahead_pw_tss.py
```

Output under `ipss.plugin.core/testData/powerworld/ieee39/`:

| File | Purpose |
|---|---|
| `IEEE39bus_v30_labeled.aux` | Labels on gens/loads/branches (InterPSS names) |
| `golden_tsschedule_reference.aux` | Minimal syntax reference (1 gen, 1 load, 1 branch) |
| `*_dayahead_schedules.aux` | MW + branch-status schedules + subscriptions |
| `*_dayahead_timepoints.csv` | 96 timestamps, 15-min, ISO8601 column |
| `*_dayahead_outages.csv` | PWCSV scheduled-outage rows (parallel to TSS) |
| `*_dayahead_plan_run.aux` | Simulator orchestration SCRIPT |
| `*_dayahead_plan.tsb` | Text manifest (not binary — see TSB note) |

Copy adapter-test subset to `ipss.test.plugin.core/testData/powerworld/ieee39/`.

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

PowerWorld `.tsb` is proprietary binary. Committed file is a **text manifest** only. Export binary from Simulator using `*_generate_tsb.aux` before `TimeStepLoadTSB` in production.

### Flat MW caveat (current IEEE39 fixture)

The generator emits **one** numeric `SchedPoint` at T0 per device (`ApplyAsEvents=NO`). Plan JSON may have **time-varying** MW at T1+. Full round-trip requires multi-point `SchedPoint` rows per device (future generator enhancement).

---

## Phase 2 — Import (PowerWorld → PlanMaintainModel)

### Adapter file discovery

`Aux2PlanMaintainAdapter.createDayAheadModel(Path dir)` finds exactly one file per suffix:

| Suffix | Required |
|---|---|
| `dayahead_schedules.aux` | Yes |
| `dayahead_timepoints.csv` | Yes |
| `dayahead_outages.csv` | Optional (fallback when no `Sched_Maint_*` schedules) |

Example valid names: `ieee39_dayahead_schedules.aux`, `ieee39_dayahead_timepoints.csv`.

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

### End-to-end sample

`ipss.plugin.core/src/sample/java/org/interpss/fstate/Aux_FSPluginDclfAlgoRunSample.java`:

```java
AclfNetwork aclfNet = IEEE39_RAW_Info_Sample.loadIEEE39Raw();
PlanMaintainModel model = Aux2PlanMaintainAdapter.createDayAheadModel(pwDir);
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

# Full DCLF integration (AUX plan)
mvn -pl ipss.test.plugin.core test -Dtest=AuxFSPluginDclfAlgoRunTest
```

Both are in `CorePluginTestSuite`.

### Adapter test checklist

- [ ] `DayAhead` plan type, 96 points, 15-min interval
- [ ] 10 gens, 19 loads at T0
- [ ] 2 maintenance records with correct windows
- [ ] Flat MW T0=T1=T2 (single SchedPoint fixture)
- [ ] Parser: 31 schedules + 31 subscriptions

### Manual PowerWorld checklist

1. Load `*_labeled.aux` — labels resolve
2. Load `golden_tsschedule_reference.aux` — syntax OK
3. Load binary TSB — 96 time points in TSS Summary
4. Run `*_run.aux` — no import errors
5. Spot-check outage windows at T32/T56

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
- [ ] 8. Add sample + AuxFSPluginDclfAlgoRunTest if DCLF integration needed
- [ ] 9. Ensure network device names match plan schedule names
- [ ] 10. Update plan-maintain-to-powerworld-tss.md
```

For **week/month** plans: adjust `FSPlanMaintainModelType`, horizon length, interval, and file suffix convention (extend adapter beyond `createDayAheadModel` if needed).

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
| Adapter `Expected exactly one *dayahead_schedules.aux` | Wrong filename suffix | Rename to `*dayahead_schedules.aux` |
| Flat MW but JSON varies by T | Single SchedPoint export | Add multi-point SchedPoints in generator |
| TSB load fails | Text manifest, not binary | Export from Simulator |
| Branch maint not found | Label mismatch | Use `ieee39.json` LINE branch names in labeled.aux |
| Week plan 26k-line JSON | Long horizon | Separate generator; may need non-flat schedule export |

---

## Reference links

- Mapping doc: `ipss-plugin/ipss.plugin.core/docs/data_fmt/plan-maintain-to-powerworld-tss.md`
- Contingency AUX pattern: `org.interpss.plugin.contingency.aux_fmt`
- Plan model builder: `com.interpss.algo.fstate.plan.PlanMaintainModelBuilder`
- Future-state architecture: `ipss-core/ipss.core_EMF/docs/md/notes/fstate-architecture.md`
