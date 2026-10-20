# REGFMA1 native limiter SMIB follow-up

## Scope and source

Implementation commit: `26660c135` (four model/test files). This report records
the completed 54-test focused, full selected SMIB and plugin-unit verification
for that exact source; the commit's smaller focused-test count is not the
complete validation scope. The committed source was rechecked against the
isolated tested snapshot before this report was finalized.

This follows plugin `8eb6ad000`. It verifies additional REGFMA1 limiter paths
on the same public three-bus, 50-MVA converter/infinite-bus fixture. It does
not run or require full Texas2k dynamics. Source is the installed reference simulator
Model Library, printed pages 808–809 (PDF pages 820–821, one-based), with
the PDF/model-DLL hashes recorded in the
[preceding source contract](regfma1-native-schema-2026-10-17.md).
Both block diagrams were visually reviewed.

The native voltage PI has two separate limiters: its integral state s8 has
Emin/Emax bounds, and its algebraic proportional-plus-integral output has
Emin/Emax bounds. Output saturation alone does not freeze an interior integral.
At an integral bound, outward integration stops and inward motion is allowed.
The four P/Q PI integrators have their own one-sided zero bounds and separate
one-sided algebraic outputs.

## New independently repeated profiles

All profiles retain the same network, gains, 0.05–0.10-s three-cycle Bus 2
fault and 0.3-s endpoint. Each configuration runs at 0.5 ms and 0.25 ms, twice
cold at each step: twelve complete native runs, six new datasets.

| Configuration | Selectors | Changed source parameters | Exercised paths |
|---|---|---|---|
| Terminal-voltage PI with Imax | Vflag=1, QVflag=1 | Imax=0.5 | Active current limit, voltage integral, clearing/release |
| Narrow P/Q limits | Vflag=0, QVflag=0 | Pmax/Pmin=0.405/0.395; Qmax/Qmin=0.3/0.2 | All four P/Q integral and output paths |
| Narrow voltage limits | Vflag=1, QVflag=1 | Emax/Emin=1.08/1.07 | Independent upper integral bound and later inward release |

The solved initial point lies inside each declared limit interval. These are
declared discriminator profiles, not fitted source-case parameters. No gains,
curves, initial states, network settings or existing comparison tolerances
were changed to match native traces.

Every pair has identical RAW, DYR, full CSV and exact probe-script hashes.
All twelve native logs/CSVs pass the permanent initialization/convergence
quality auditor. Additional activity guards confirm actual limiter activation
and release. The P/Q profile exercises both directions: native extrema are
approximately -7.8810e-5 and +8.4383e-5 for the active-power integrals, and
-0.0548557 and +0.00612318 for reactive-power integrals. All four PI outputs
are zero at 0.3 s, even though the Pmax integral still has stored state. The
implementation does not incorrectly equate integral release with output release.

## Bug found and corrected

The previous native path stopped voltage integration when `Kpv*error+s8`
reached an output limit. Native reference simulator instead continues integrating until s8
itself reaches its independent bound. Two blockwise guards fail before the
correction: the first predicted integral should be 1.0939402014202504 but
remains 1.0933542014202504; an upper-bound run also stalls at that initial
value instead of reaching 1.10.

The fix applies the native integral-bound rule and clamps native s8 at both
predictor/corrector stages. It retains inward motion at both bounds and does
not alter the algebraic voltage-output limiter. Inactive native bounds do not
change the derivative. The existing typed/PowerWorld-compatible path remains
unchanged: this is a native reference simulator correction, not evidence that PowerWorld
has a different active-limit equation. That path still needs its own targeted
active-voltage-limit reference.

| Voltage-limit comparison | Before, 0.5 ms | Before, 0.25 ms | Corrected, 0.5 ms | Corrected, 0.25 ms |
|---|---:|---:|---:|---:|
| Maximum s8 error (pu) | 0.00273432 | 0.00272599 | 5.98820e-6 | 5.09976e-6 |
| Maximum Bus 1 voltage error (pu) | 0.000574974 | 0.000570596 | 3.82820e-5 | 1.98494e-5 |

Step halving did not remove the old mismatch. Correcting the published rule
does. Residual integral errors do not uniformly halve near limit release;
no universal second-order claim is made. All 24 channels are audited at every
regular endpoint, with separate errors/timestamps. Public tests embed nineteen
checkpoints for each fault dataset. Native instantaneous event-post rows and
initialization history remain retained but explicitly unscored, as in the
preceding slice; no integrated sample is relabeled as instantaneous event-post.

## Implementation and regression evidence

- Production change is confined to native REGFMA1 voltage-integral limiting.
  Existing native/legacy schema cardinalities and named-state keys are unchanged.
- References now carry immutable eighteen-CON vectors copied from the source
  DYR decimal values, not retuned or replaced by native float-rounding values.
  Existing nine dataset bodies/digests are retained; six new fixed-digest
  datasets bring the total to fifteen.
- **54 focused tests pass, zero failures/errors/skips.** These include native
  predictor/integral-bound release guards, every new dataset, old native
  profiles, legacy/PowerWorld trajectories and catalog checks.
- **1,155 selected SMIB/model tests: zero failures/errors, one skip; exit zero.**
  The skip remains the existing opt-in positive-Q REGCA1 diagnostic.
- **129 plugin-core unit tests: zero failures/errors, six optional skips;
  exit zero.** Four skips are optional KlusolveX native-solver checks, two
  are optional AUX fixtures. No full-system dynamics are required or run.
- Shared core source and the isolated `1.4.3-regfma-native` dependency are
  unchanged from tested integration commit `a036d9a78`; its earlier 2,945 core
  passes are not represented as a new core run for this plugin-only slice.

The isolated verification checkout contains the slice's exact four changed
model/test source files, hash-checked before testing. Unrelated concurrent
Texas2k reference-runner/report edits and the untracked JAX test are excluded.
Commands retain the preceding report's profiles/core override, with run IDs
`regfma-limiter-smib-20261017` and `regfma-limiter-plugin-unit-20261017`.

## Reproduction and remaining work

Frozen scripts, twelve full reference runs, two fresh isolated replays, native
quality logs, before/after full trajectories, public-checkpoint preparation,
source snapshots and fresh regression reports reside in the ignored private
`2026-10-17-regfma1-native-limiters` verification archive. Each file is covered
by its SHA-256 inventory. The replay wrapper stages the frozen script and RAW
in a new tree and reproduces RAW, DYR and complete CSV hashes for both the
narrow-P/Q and narrow-voltage configurations. It refuses overwrite and requires
the same native model library. Original failing runs are not replaced.
The sealed archive contains **844 hash-verified evidence files**.

Still open for complete REGFMA1 SMIB acceptance:

- Independent lower-voltage-integral-limit trajectory; both bounds and inward
  release are blockwise-tested, but this native disturbance activates only the upper.
- P/Q limit interaction with terminal-voltage PI enabled; this P/Q profile
  verifies direct-voltage mode only.
- Plant-input excursions and lifecycle/trip/reconnect coverage.
- Native zero-time branches, initialization outside nominal output limits and
  exact instantaneous event-post comparisons.
- Independent active-voltage-limit verification for the retained typed/
  PowerWorld-compatible path; the native correction is deliberately not
  propagated to that unverified branch without its own evidence.

The overall approved-model SMIB coverage goal remains incomplete.

The [lower-bound and simultaneous-PQ/voltage-PI follow-up](regfma1-lower-and-pq-pi-2026-10-19.md)
adds four cold-paired full-row datasets and independent source/stage checks.
It retains a coarse default-method counterexample explicitly; the historical
fifteen datasets, their bounds, and this report's original evidence are unchanged.
