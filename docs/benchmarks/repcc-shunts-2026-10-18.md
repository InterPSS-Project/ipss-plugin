# REPCC ordered switched-shunt kernel

## Scope and implementation

This slice implements `RepccShuntController`, a deterministic endpoint-driven
state machine with ten ordered device slots. It consumes machine-base measured
reactive power and returns completed physical-switch actions. It does not yet
attach to a plant, resolve measurements, or modify network admittance. Runtime
family coverage therefore remains **25/27**; REPCC/REPCCU and HPLNTD remain open.

The contract follows the installed native Model Library, section 27.4, printed
pages 913–925, and the independent [PowerWorld REPC_C description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_C.htm).
The source-document SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Exact executable/library provenance is retained in private run manifests.

Implemented behavior:

- Remove capacitors before inserting reactors for low reactive power; remove
  reactors before inserting capacitors for high reactive power. Preserve input
  ordering within each device type.
- Separate strict first-/second-stage bands and delay clocks, with reset on
  central-band recovery or direction reversal. Retain elapsed threshold clocks
  when changing severity on the same side.
- Issue at most one new command per accepted endpoint. Multiple breaker timers
  may overlap. Both capacitors and reactors use the breaker delay in the native
  observed implementation. Pending commands remain latched after band recovery.
- Start capacitor discharge lockout at actual opening, not command issue. Do
  not issue a capacitor reclosure before `TOUT` expires. Initially open
  capacitors are assumed discharged.
- Skip unused zero-bus slots safely. Disable switching for a missing measurement
  branch, disabled flag or invalid switching thresholds. Reject missing or
  duplicate configured devices when switching is active.
- Expose immutable named monitoring values: two threshold clocks, ten breaker
  clocks, ten last-opening/discharge clocks and ten service flags. Repeated
  endpoint times are idempotent. Predictor/network iterations must not advance
  this state machine.

## Independent evidence and strict comparison

Ten datasets have twenty cold native runs at 0.5 ms and 0.25 ms. Each reference
retains every raw sample and event-side duplicate, the exact input, a separate
API-query stream for all ten physical shunt statuses and nominal admittances,
the runnable probe, logs and hashes. Compressed immutable CSV/status/input
resources are exercised by the registered `RepccShuntNativeConformanceTest`.

Eight datasets cover positive/negative reactive power and slow/fast threshold
paths at both timesteps. Every sample of both threshold timers and all ten
breaker timers agrees within **1e-10 seconds**. Every physical service status
agrees at every queried endpoint. Each profile has five matching switch events,
including identities, order and completion times; no broad event window is used.
The replay test exports eight finite, 36-column comparison traces.

Eight complete raw CSV pairs are byte-identical. Two differ only in inactive
plant-recovery `VAR12` below 1e-18 seconds, with filtered voltage above the
freeze threshold. Every other field and sample is identical. All ten status,
input and probe pairs are byte-identical. Both raw files/hashes are retained;
neither references nor model parameters are altered to force agreement.

## Discharge discrepancy: deliberately not parity acceptance

Two datasets, each cold-repeated, apply a held three-phase Bus 2 fault from
0.03 to 0.25 seconds to reverse the measured reactive-power direction. This is
a 220-ms relay stress, **not** a three-cycle benchmark. `TOUT=0.15` seconds is
checked against the actual post-initialization parameter array.

The native reference opens C1 at approximately 0.014 seconds, then recloses it
near 0.040 seconds: only about 26 ms later despite the declared 150-ms discharge
time. InterPSS retains the published lockout and closes near 0.169 seconds. All
29 event identities/orders match in each profile; all non-capacitor-reclosure
times match within 1e-10 seconds. Exactly five capacitor reclosures differ by
more than 0.1 seconds. Tests preserve and assert this distinction rather than
accepting early reclosure or widening tolerances. Native discharge VARs remain
inactive and are not mislabeled as InterPSS last-opening clocks.

PowerWorld runs import the exact controller/shunt associations, active flag,
thresholds and delays. The complete network/model AUX audit confirms
`MSSFlag=1`, `TOUT=0.15`, ten valid fixed-shunt associations and active plant
states. The fault applies/clears and results are finite, but all ten physical
shunt statuses remain unchanged. These are **non-activating diagnostics**, not
independent discharge-lockout validation. The cause of inactive PowerWorld
switching and independent operational lockout acceptance remain open.

## Rejected and non-activating probes

- With switching enabled and four populated plus six unused slots, the native
  simulator exits during the first dynamic step after healthy initialization.
  Query-free and capacitor-only variants reproduce the stop. Ten populated
  slots run successfully. Disabled-switching and missing-measurement-branch
  controls run, but have not been cold-paired for acceptance. InterPSS safely
  handles unused slots rather than reproducing this crash.
- Reference-command cycle profiles and a higher-gain re-arm attempt fail to
  reverse the measured branch reactive power. They are retained as diagnostics
  and do not prove re-arm coverage. Only the explicitly activating held-fault
  profiles establish the discharge discrepancy above.
- Unsuccessful PowerWorld parameter-export attempts and a dependency-cache
  access failure are preserved separately from passing gates. A marker or
  completed process alone does not establish model activation.

## Acceptance gates and remaining work

On plugin baseline `b5c27de3f`, the completed gates have zero failures/errors:
84 focused tests, 344 plugin-core tests (six optional skips), and 1,386
registered SMIB/model/solver tests (one existing diagnostic skip). The focused
gate includes ten shunt unit tests and two independent replay/discrepancy tests.
Tests use the isolated, previously accepted engine jar with SHA-256
`e302580561dfd6a4b6c203fe910bdd082aa1ee212f67e20334a36998781ba187`;
all report classpaths are audited. No production dependency version is changed.

The audit checks 35 tested source/resource files and 2,083 baseline Java/POMs,
all twenty native run manifests and raw resources, finite replay traces, and
all five earlier sealed plant archives. A concurrent documentation-only commit
`9e6f92825` is included in the audited Git baseline; it changes no tested code.
The private evidence archive is
`2026-10-18-repcc-shunts`; it retains the failed/non-activating probes too.

Still required before complete REPCC acceptance:

- Resolve independent operational discharge-lockout evidence and inactive
  PowerWorld switching without fitting the controller to either tool.
- Attach the shunt actions at accepted solver endpoints and update physical
  network admittance exactly once per event; validate trip/restart behavior.
- Add exact import, plant attachment, branch/frequency measurements, MVA-base
  conversion and downstream command routing.
- Complete the remaining kernel initialization/selector/limit profiles and
  coupled SMIB flat, three-cycle fault and lifecycle comparisons at both steps.

No full Texas2k dynamic simulation is required or run.
