# Hybrid plant disabled-control implementation

Follow-up: [feasible proportional-only equilibrium](hplntd-proportional-equilibrium-2026-10-18.md)
now covers enabled zero-integral loops and PF without electrical feedback. The
positive-gain restriction below describes this earlier slice, not current support.

## Implemented contract

HPLNTDU now supports disabled active and reactive control paths in the kernel,
initialization and shared REGCA1/REECA1 coordinator. The exact wrapper schema,
main importer and solver selection are unchanged.
The source contract is the installed model-library diagram and state/VAR tables
(PDF pages 1035-1040); its exact hash is retained in each private run manifest.

- Disabled active control leaves states 5, 6, 9 and 10 unused at zero. Its
  absolute command holds the initial aggregate active reference; its increment
  is zero. State 4 remains live only when the enabled reactive PF path needs it.
- Disabled reactive control leaves states 0, 1, 2, 3, 8, 11 and 12 at zero.
  Its absolute command holds the initial aggregate reactive reference and its
  increment is zero. Unused voltage/Q/PF references cannot activate the loop.
- Shared voltage state 7 remains a physical measurement even with both loops
  disabled. Used PF active measurement state 4 is not confused with active PI
  storage or plant-reference state 10.
- Both modified-Euler stages, zero-time algebraic bypasses, the opt-in staggered
  interface and named-state ordering preserve these distinctions. Reinitializing
  a group reseeds its held commands and resets the shared stage history.
- Positive integral gains are still required for enabled loops. A disabled
  loop does not need an integral bias and does not validate its held initial
  command against an unused dynamic-output limiter. Proportional-only
  initialization of an enabled loop remains explicitly unsupported.

The three new kernel regressions fail against the previous implementation,
which rejected disabled paths. No existing trajectory tolerance, model gain,
initial boundary measurement or solver equation is changed to fit a reference.

## Independent small-case evidence

Sixteen embedded profiles at 0.5/0.25 ms cover active-disabled, reactive-disabled
and both-disabled Q control; active-disabled compensated/droop voltage control;
and active-disabled, reactive-disabled and both-disabled PF selection. The latter
five configurations add twenty fresh cold reference runs; twelve prior cold runs
are reused for the other six profiles. Inputs, scripts and complete raw outputs
are retained, including both event/pause duplicate rows and all 89 raw channels.

Recorded-input replay compares every plant state and four absolute/incremental
commands without injecting native state values. The worst error is approximately
2.144e-6 pu in reactive integral storage. The full imported network comparison
covers 25 quantities: four bus voltages, four unit power channels, thirteen plant
states and four downstream states. All samples are retained. Tests also require
inactive command ports to remain zero-increment and independently assert the
modified-Euler scalar reference recurrence at every accepted step.
The additional voltage/PF profiles establish equilibrium, unused-reference
routing and shared-state ownership; they do not excite every voltage/PF
disturbance path.

These are bounded coupled comparisons, not exact whole-model trajectory parity.
The reference's STORE realization differs from default modified Euler for the
reference slews with time constant 2h: maximum active-reference disagreement is
approximately 0.005471 pu and reactive-reference disagreement 0.002184 pu.
The active-only coupled voltage-filter error falls from about 1.928e-5 to
9.193e-6 pu on halving h; frequency-filter error falls from about 1.357e-6 to
6.793e-7 pu. Recorded-input replay localizes these to network/method coupling
rather than disabled-state equations. Per-profile/channel regression limits are
tightened to approximately 10% above the observed maxima with a 1e-12 floor.
The raw paired traces record every maximum and its time.

## Repeatability and unused reference storage

Fourteen of sixteen cold pairs have identical raw CSV hashes. Two pairs differ
only in unused reactive-path VARs 0, 1, 7 and 9 with Qflag=0 and PF selected.
The source identifies these as voltage reference, reactive reference, Q/V
deadband output and PF reference. Some retain uninitialized native storage;
one raw value is approximately 1.9e28. Every state, physical channel and command
is identical across each pair. Neither raw output is sanitized or replaced.

`tools/audit_hplntd_selectors.py` explicitly permits comparison exclusions for
only those four variables in `q-disabled-pf` and `both-disabled-pf`. Its canonical
comparison hash is SHA-256 of UTF-8 compact JSON of parsed numeric rows, retaining
CSV column order and every row, with only those four keys omitted. All other
profiles compare all channels. Separate raw hashes, excluded keys and canonical
hashes are recorded. Negative controls prove that a changed state, command,
active reference or event time is rejected and that the exception cannot hide
changes to an enabled reactive path.

Reproduce new references with the installed reference Python environment:

```text
reference-python tools/hplntd_selector_probe.py UNIQUE_TAG 0.0005 MODE
reference-python tools/hplntd_selector_probe.py UNIQUE_TAG 0.00025 MODE
python tools/audit_hplntd_selectors.py
```

The five modes are defined explicitly in the probe. The previous discriminator
probe provides the other three configurations. The audit uses the retained
local run names; it is an artifact-sealing check, not a requirement for licensed
execution in ordinary Maven tests. Maven tests use immutable compressed CSV and
exact input resources with hash guards. Exact tool/source identities remain in
private manifests; public prose uses neutral benchmark language.

## Final gates and retained artifacts

Source-audited against plugin `31cc194fe` (the concurrent change from `29c6b8945`
is documentation only): 36 focused checks, 382 plugin-core tests (six existing
optional skips), and 1,499 selected SMIB/model/solver tests (one existing
diagnostic skip), zero failures/errors. Twenty-two offline probe/audit/wording
checks also pass. Three new kernel tests fail against the preceding production
code. Existing native/fault/other-model trajectory limits remain unchanged.

The private evidence archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-hplntd-disabled-controls`.
It contains source/input/output hashes, 32 raw reference runs, all sixteen
state-replay and sixteen coupled traces, fail-before output and final Surefire
reports. The earlier discriminator archive is checked for immutability.

## Remaining work

Enabled proportional-only equilibrium, feedback/feedforward discriminators,
independent voltage/PF disturbance profiles, branchless reference acceptance,
alternate downstream hosts, switching and
exhaustive trip/reconnect paths remain open. The earlier instantaneous fault
current/frequency callback parity gap is unchanged. Source-list HPLNTD is not an
inferred alias. No full Texas2k dynamic simulation is run or required here.
