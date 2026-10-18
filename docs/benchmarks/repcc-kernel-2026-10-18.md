# REPCC thirteen-state kernel verification

## Status and scope

This slice implements the REPCC differential/algebraic controller kernel on top
of the [typed equilibrium foundation](repcc-foundation-2026-10-18.md). It is not
a loadable network device: strict import, attachment, mechanically switched
shunts and several initialization modes remain open. Approved family runtime
coverage remains **25/27**, with REPCCU's family and HPLNTD still missing.

The source contract remains the installed standard-native REPCC, not an inferred
REPCCU wrapper. Source section 27.4 and diagram provenance are recorded in the
foundation report. The diagram was visually rechecked using the PDF workflow.
[PowerWorld REPC_C](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_C.htm)
remains an independent modeling reference; its additional model-base and
frequency-rate fields are not inserted into the 57-CON native input.

## Implementation

- Thirteen named states, immutable monitoring snapshots and explicit
  modified-Euler predictor/corrector stages. A mismatched or duplicate pending
  predictor is rejected. Output queries do not advance simulation time.
- Separate voltage, reactive-power and power-factor error paths; compensated
  complex voltage, reactive droop, signed PF reference with absolute active
  power, filtered frequency, voltage-based frequency gating and frequency-error
  limits before droop.
- Distinct electrical feedback and feedforward. Active-error bounds apply
  after feedback subtraction, not to the feedforward request. PI output bounds
  depend on the feedforward flag as required by the native contract.
- Conditional non-windup integration: outward integration stops at an active
  PI output bound, but a proportional jump does not refit integral storage.
  Inactive bounds preserve the local derivative.
- Plant/Q-reference slew filters use twice the actual timestep. The active
  command lag also has derivative limits. Reactive output slew is selected only
  for a downstream reactive-power command, not a voltage command.
- Only states 2, 3, 5 and 6 freeze below filtered Vfrz and through recovery.
  Measurements/reference slew remain live. Recovery starts at an accepted
  endpoint and the absolute start timestamp clears when the hold expires.
  A new dip resets recovery.

The existing foundation's positive-integral-gain/enabled-loop/disabled-shunt
initialization restrictions are deliberately retained. They must not be mistaken
for complete parameter or runtime support. Zero-time algebraic paths and a
downstream voltage-command bypass have equation tests; independent native
profiles for their full combinations remain open.

## Native discriminators and replay

Fourteen datasets cover seven profiles at 0.5 and 0.25 ms: combined voltage,
active and frequency reference changes; reactive-reference changes; PF-reference
changes; active PI limits; droop plus nonzero lead time; a three-cycle fault with
voltage freeze/recovery; and explicit frequency-error saturation. Each has two
cold runs. Runs retain 60 channels and 608/1,208 samples through 0.3 seconds,
including two negative-history samples and all five duplicate endpoint rows.

The replay initializes from physical P/Q/voltage and configuration, then uses
recorded physical bus voltages, current derived from measured branch P/Q,
frequency and external reference signals. It never loads native state vectors
into the kernel. Every raw row is compared, including history and pre/post-
reference-update duplicates. This is kernel conformance evidence, **not** an
independent InterPSS-network SMIB trajectory comparison.

All thirteen states plus active command, reactive error and limited frequency
error have individual fixed per-profile bounds, generally 25 percent above
the measured maximum with a small numerical floor. Initial float offsets remain
in the comparison. Traces retain both state vectors and both timer values.

Representative maximum errors before tolerance margins (kernel coordinates,
not independently simulated network-bus errors):

| Profile / dominant state | 0.5 ms | 0.25 ms |
|---|---:|---:|
| Combined references / plant-reference slew | 7.41e-5 pu | 4.25e-5 pu |
| PF reference / reactive lead-lag | 2.47e-4 pu | 1.24e-4 pu |
| Freeze fault / filtered regulated voltage | 5.96e-4 pu | 3.06e-4 pu |
| Frequency saturation / active command | 9.94e-5 pu | 4.99e-5 pu |

Important discriminator findings:

1. Refitting the integral to a moving proportional-error bound was wrong.
   The active-limit profile reduced the observed integral discrepancy from
   approximately 0.027 pu to 1.2e-7 pu after implementing conditional integration.
   This is a production-equation correction, not a gain or initial-state fit.
2. The frequency-saturation profile proves native VAR8 reports the **limited**
   error. The kernel exposes raw deadband output and limited error separately;
   diagram labeling must not be used to interchange their observation coordinates.
3. Event/reference first increments retain a bounded integration/history
   difference from explicit modified Euler. Principal state errors fall with
   the timestep, but small float-filter floors do not improve uniformly. The
   kernel is not quantized to emulate native float storage.
4. Freeze timer activation/clearing can differ by one step. On intervals when
   both timers are active, start times must agree within one step plus 3 us.
   Active/inactive disagreement is confined to the two transition windows,
   with at most four raw rows including duplicates. A 0.123-second timer value
   versus zero is not accepted as an unrestricted scalar-error tolerance.

## Cold-repeatability exception

Of fourteen cold pairs, ten raw CSV pairs are byte-identical. Four differ only
in the inactive recovery-timer variable: zero versus approximately 1.36e-19
seconds, while filtered voltage stays above Vfrz. Every other sample/channel is
identical. Preserve both raw hashes and files. The audit permits only this named
inactive field below 1e-18 seconds and rejects any other difference. No CSV,
initial state or model parameter is rewritten to hide the residue.

## Remaining work

- Native proportional-only, disabled-loop, PF-without-feedback and low-power
  PF initialization, charging/discharging, initial limits and all selector modes.
- Additional active reactive-PI limits, zero-time paths, active output-rate
  limits, native voltage-command bypass and frequency-voltage gating profiles.
- Mechanically switched shunt state machine, ordered dispatch, breaker and
  discharge/re-arm timers, and admittance changes.
- Exact native import, non-overwriting attachment, branch/frequency measurement
  resolution, base conversion and downstream command routing.
- Coupled InterPSS SMIB flat, three-cycle fault, reference and lifecycle events
  against native/PowerWorld references at both steps, including event sides.
- Resolve or explicitly accept the remaining integration-history, float-filter
  and timer-transition residuals against the full verification plan.

No full Texas2k dynamic simulation is required or run.

## Acceptance gates and sealed evidence

Final gates pass with zero failures/errors: 56 focused checks, 334 plugin-core
tests (six optional skips), and 1,379 registered SMIB/model/solver checks (one
existing diagnostic skip). The focused gate includes nine kernel equation tests
and two replay/profile-activation tests spanning all fourteen datasets. The
last gate selects the registered SMIB suite plus supplemental catalog, staggered
solver, static-load voltage-curve and runtime-plan tests.

The audit matches 33 owned Java/resource files to the tested snapshot, checks
2,079 other baseline Java/POM files against Git, verifies all 28 native run
manifests/inputs/outputs, and checks fourteen finite 29-column replay traces.
It rechecks the four earlier immutable plant archives (1,021, 1,079, 1,085 and
1,473 files respectively). Raw pairs with timer residue are checked across
every sample/channel under the named exception, not accepted from a summary
label alone. The private archive is `2026-10-18-repcc-kernel`.

The tested plugin baseline is `8342c05fe`, including its scoped stabilizer-output
reuse change. The isolated last-accepted core jar has SHA-256
`e302580561dfd6a4b6c203fe910bdd082aa1ee212f67e20334a36998781ba187`.
Every test report's classpath is checked for that frozen artifact. Concurrent
shared-repository engine rebuilds do not alter these test inputs, and no
production POM or dependency version is changed for the isolation.
