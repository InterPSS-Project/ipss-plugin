# Hybrid plant switching kernel evidence

Historical kernel-only slice `b86a10f8a`. The
[physical-boundary follow-up](hplntd-switching-boundary-2026-10-18.md) subsequently
enables exact physical switching/import with bounded coupled acceptance and
explicit remaining clock/origin/discharge limitations.

Scope: small four-bus/two-unit reference testbed, not Texas2k dynamics.
This slice proves a shared discrete kernel, not HPLNTDU physical-network
switching or complete model verification. Main switched-shunt import remains
rejected until its physical binding and coupled evidence are implemented.

## Implementation and source contract

The existing ordered ten-device `RepccShuntController` now accepts `HplntdData`
without translating the continuous model or its native layout into REPCC.
Only eight switching constants are shared: Qdn1/Qdn2/Qup1/Qup2, Tdelay1/2,
Tmssbrk and Tout. The timer/order/lockout algorithm is unchanged for REPCC.
HPLNTDU input and thresholds are **physical Mvar**; no plant-MVA division is
permitted. Signed measurement-branch direction is resolved before the kernel.

The versioned model-library flowchart and notes define slow/fast bands,
input-order selection, removal of opposing support before insertion, breaker
delay and capacitor discharge lockout. Exact source/runtime hashes are retained
in private run manifests. Source pages 1041 and 1045–1047 were inspected.
Invalid Q threshold ordering or a missing measurement branch disables switching
without silently correcting entered parameters.

## Repeatable reference artifacts

`tools/hplntd_switching_probe.py` creates eight modes at 0.5 and 0.25 ms, each
run cold twice: positive/negative fast and slow bands, disabled switching,
invalid threshold ordering, missing measurement branch and zero delay.
Five capacitors and five reactors at bus 2 use 0.01 Mvar nominal magnitude.
Their small sizes maintain the selected band while exercising all ten slots.
Machine bases are unequal (50/100 MVA), with a 150 MVA plant aggregate.

Each run preserves RAW/DYR, runnable probe/helper, binary output, log, manifest,
89 raw channels and 32-column physical shunt-status/nominal data. All 61 entered
constants are checked against actual float32 storage. Embedded CSV/status/input
resources have fixed independent hashes. `tools/audit_hplntd_switching.py`
audits all 32 runs, exact input variants, allocation, finite samples, generator
identity, raw hashes, byte-exact cold CSV/status equality and embedded resources.
Raw output has 402/802 samples (403/803 for zero delay); physical status queries
have 201/401 samples. No history or duplicate observation is discarded.

## What the comparison proves

`HplntdShuntNativeConformanceTest` replays native physical Mvar and the actual
native accepted clock through the shared kernel. Fourteen nonzero-delay
profiles match all twelve threshold/breaker timers and all ten physical statuses.
Positive Q removes R1–R5 before inserting C1–C5; negative Q removes C1–C5 before
inserting R1–R5. Disabled/invalid/missing-branch profiles produce no action.
Dividing input by live plant MVA incorrectly suppresses all ten positive-fast
actions; a regression guard preserves this discriminator.

The source note describing breaker timers as capacitor-only is contradicted
by populated reactor breaker storage in both cold runs. Native reactor timers
are retained and compared rather than hidden or forced to unused values.

## Explicit limitations and next slice

- The first physical-status query carries the actual negative prehistory clock,
  before first dynamic evaluation. In zero-delay profiles the raw output retains
  both origin observations; the kernel performs its first action at time zero.
  Positive-time physical statuses match, but native unused threshold-timer
  storage does not. This is a diagnostic, not complete state/callback parity.
- Native discharge VAR storage stays -1 after capacitor opening, unlike the
  published lockout state. Both coordinates are retained in comparison traces.
  No profile recloses a capacitor; no native discharge-function parity is claimed.
  Independent unit tests retain the published discharge lockout and verify
  release only after Tout measured from actual opening.
- Replay uses native clock arithmetic, including slow-band threshold crossings;
  it does not establish ideal-double-clock coupled switching-time parity.
- Physical network G+jB updates, HPLNTDU accepted-endpoint action dispatch,
  switched import and closed-loop waveform comparisons remain the next slice.
  Converter outputs, continuous states and network voltages are preserved in
  raw artifacts but are not scored as newly accepted coupled trajectories here.

No parameters, epsilons, timers or state coordinates were fitted to the traces.

## Regression and evidence handoff

Final isolated gates: 38 focused Java checks; 401 plugin-core tests with six
existing optional skips; 1,530 selected SMIB/model/solver tests with one existing
diagnostic skip. All have zero failures/errors. The frozen companion-core jar
is unchanged. Seven new offline schema/audit guards accompany the existing
32 offline verification/language tests. Full-system dynamic simulations were
not run or used as a gate.

Raw references, all sixteen 56-column kernel traces, source snapshots, gate
reports and SHA inventory are sealed under the private verification-artifacts
directory `2026-10-18-hplntd-switching-kernel`. The support/evidence inventory
continues to classify this family as partial rather than completely verified.
