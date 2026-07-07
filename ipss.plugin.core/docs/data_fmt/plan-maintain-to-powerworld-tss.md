# Plan/Maintain Model to PowerWorld Time Step Simulation

Maps InterPSS **PlanMaintainModel** day-ahead inputs to static PowerWorld Simulator **Time Step Simulation (TSS)** auxiliary files under `testData/powerworld/ieee39/`.

Related: [future-state-what-if-analysis.md](future-state-what-if-analysis.md) (InterPSS future-state pipeline).

PowerWorld references:

- [Setting up Scheduled Input Data](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Setting_up_Scheduled_Input_Data.htm)
- [Schedule Dialog](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Schedule_Dialog.htm)
- [Schedule Subscriptions Page](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Schedule_Subscriptions_Page.htm)
- [Auxiliary File Format PDF](https://www.powerworld.com/WebHelp/Content/Other_Documents/Auxiliary-File-Format.pdf)

## Source fixture

| Item | Path |
|---|---|
| Plan JSON | `testData/psse/v30/ieee39_dayahead_plan_maintain_plan.json` |
| PSSE base case | `testData/psse/v30/IEEE39bus_v30.raw` |
| InterPSS branch names | `ipss-core/ipss.test.core/testdata/json/ieee39.json` (`branchAry`, `branchCode=LINE`) |
| Maintenance definition | `ipss-core/.../Ieee39_Dayahead_Info.java` |

Horizon: **96** points, **15-minute** spacing, start **2026-06-27T00:00:00**.

## Generated artifacts

| File | Role |
|---|---|
| `IEEE39bus_v30_labeled.aux` | Sets `Label` on 10 gens, 19 loads, 34 line branches (InterPSS names) |
| `golden_tsschedule_reference.aux` | Minimal 1 gen + 1 load + 1 branch schedule/subscription syntax reference |
| `ieee39_dayahead_plan_schedules.aux` | 29 numeric MW schedules + 2 boolean line-status schedules + subscriptions |
| `ieee39_dayahead_plan_timepoints.csv` | Human-readable list of 96 TSS timestamps (`Single Solution`) |
| `ieee39_dayahead_plan.tsb` | TSS time-point manifest (see TSB note below) |
| `ieee39_dayahead_plan_generate_tsb.aux` | One-time Simulator script to save binary `.tsb` |
| `ieee39_dayahead_plan_outages.csv` | Scheduled Actions (PWCSV) parallel outage representation |
| `ieee39_dayahead_plan_run.aux` | Orchestration SCRIPT for full day-ahead TSS run |
| `generate_ieee39_dayahead_pw_tss.py` | Regenerates all text artifacts from plan JSON |

Regenerate:

```bash
python3 ipss.plugin.core/testData/powerworld/ieee39/generate_ieee39_dayahead_pw_tss.py
```

## Field mapping

### Gen / load MW (flat day-ahead schedule)

| InterPSS JSON | PowerWorld |
|---|---|
| `timePeriodRecList[0].timePointRecList[*].genMap.{name}.p` | `TSSchedule` `Sched_Gen_{name}` (Numeric, single point at T0) |
| `timePeriodRecList[0].timePointRecList[*].loadMap.{name}.p` | `TSSchedule` `Sched_Load_{name}` (Numeric, single point at T0) |
| Device name e.g. `Bus39-G1` | `Label` on Gen/Load + subscription `ObjectIdentifier` |
| Subscription target | `Gen MW` / `Load MW` via `TSScheduleSub` |

MW is flat across all 96 points in this fixture; one `SchedPoint` at `06/27/2026 12:00:00 AM` with `ApplyAsEvents=NO` re-applies each timestep.

### Planned line maintenance

| InterPSS (`EquipmentMaintainRec`) | PowerWorld TSS branch schedule | PowerWorld Scheduled Actions CSV |
|---|---|---|
| `name` e.g. `Bus29_to_Bus26_cirId_1` | `Label` on branch + `Sched_Maint_{name}` | `BranchLabel` column |
| `planState: Inactive` during `[startTime, endTime)` | Boolean schedule value **OPEN** | `OpenLine` action during window |
| Otherwise in service | **CLOSED** | (line closed outside window) |
| `startTime` / `endTime` | Step-change `SchedPoint` timestamps | `StartTime` / `EndTime` |

Outage windows (day-ahead fixture):

- `Bus29_to_Bus26_cirId_1`: 08:00–11:00 (T32–T43)
- `Bus26_to_Bus25_cirId_1`: 14:00–16:00 (T56–T63)

Branch labels follow InterPSS naming (`Bus{from}_to_Bus{to}_cirId_{ckt}`) and are assigned to the matching PSSE branch regardless of from/to order in the raw file.

### Object references

Contingency-style label references (see `testData/powerworld/texas7k/..._labeled_aux_contingencies_100.aux`):

```text
GEN 'Bus39-G1'
LOAD 'Bus15-L1'
BRANCH 'Bus29_to_Bus26_cirId_1'
```

Schedule subscriptions use the same label strings in `TSScheduleSub.ObjectIdentifier`.

## Load order (Simulator)

1. Open `IEEE39bus_v30.raw` (or saved `.pwb`).
2. `LoadAux("IEEE39bus_v30_labeled.aux", YES)` — verify labels in case information.
3. Generate/load `ieee39_dayahead_plan.tsb` (see below).
4. Run `ieee39_dayahead_plan_run.aux` **or** execute its SCRIPT steps manually.

`ieee39_dayahead_plan_run.aux` sequence:

```text
SetScheduleWindow("06/27/2026 00:00", "06/27/2026 23:45", 15, MINUTES);
TimeStepLoadTSB("ieee39_dayahead_plan.tsb");
LoadAux("IEEE39bus_v30_labeled.aux", YES);
LoadAux("ieee39_dayahead_plan_schedules.aux", YES);
ImportData("ieee39_dayahead_plan_outages.csv", PWCSV, 1, NO);
ApplyScheduledActionsAt("06/27/2026 08:00", "06/27/2026 16:00", , NO);
TimeStepDoRun("2026-06-27T00:00:00", "2026-06-27T23:45:00");
```

- **TSS branch-status schedules** are the primary maintenance mechanism for TSS.
- **Scheduled Actions / PWCSV** requires the Scheduled Actions add-on; provides parallel outage switching.

## TSB file note

PowerWorld `.tsb` files are **proprietary binary** and cannot be hand-authored reliably. The committed `ieee39_dayahead_plan.tsb` is a **text manifest** listing the 96 timestamps until a binary file is exported from Simulator.

To produce the binary TSB:

1. Open the IEEE39 case in Simulator.
2. Open Time Step Simulation → Summary; configure start/end/resolution using `ieee39_dayahead_plan_timepoints.csv`.
3. Set solution type **Single Solution** for all points.
4. Run `ieee39_dayahead_plan_generate_tsb.aux` (calls `TimeStepSaveTSB("ieee39_dayahead_plan.tsb")`).

Replace the text manifest with the exported binary before running `TimeStepLoadTSB` in production.

## Golden reference / syntax validation

`golden_tsschedule_reference.aux` documents inferred concise-format headers:

- `TSSchedule` + `SchedPoint` subdata (`PointType` 0 = numeric MW, 1 = boolean Closed/Open)
- `TSScheduleSub` bindings (`ObjectType`, `ObjectIdentifier`, `ObjectField`, `ScheduleName`)

If Simulator rejects the headers, create one schedule + subscription in the UI, `SaveData` export, and diff against the golden file. Update `generate_ieee39_dayahead_pw_tss.py` accordingly.

## Validation checklist (manual in PowerWorld)

1. Load `IEEE39bus_v30_labeled.aux` — confirm labels resolve (`Bus29_to_Bus26_cirId_1`, etc.).
2. Load `golden_tsschedule_reference.aux` on a test case — confirm schedule/subscription syntax imports.
3. Load binary `ieee39_dayahead_plan.tsb` — confirm **96** time points in TSS Summary.
4. Run `ieee39_dayahead_plan_run.aux` — TSS completes without import errors.
5. At **T32** (08:00): `Bus29_to_Bus26_cirId_1` **OPEN**; gen/load MW match JSON T32.
6. At **T44** (11:00): first line restored (**CLOSED**).
7. At **T56** (14:00): second outage active (`Bus26_to_Bus25_cirId_1` **OPEN**).
8. At **T0**: total gen ≈ **6192.83 MW**, total load ≈ **6150.1 MW** (flat schedule).

## Scope

In scope: static `.aux` / `.tsb` manifest / `.csv` fixtures, labeled references, this mapping doc.

Out of scope: reusable Java converter (deferred; follow `src/main/java/org/interpss/plugin/contingency/aux_fmt/` pattern if needed later).
