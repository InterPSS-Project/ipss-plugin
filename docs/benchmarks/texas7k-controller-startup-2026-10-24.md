# Texas7k controller startup checkpoint

The attached dynamic stack now remains at equilibrium for ten seconds after
two initialization corrections. This is progress toward the complete case,
not full-system acceptance: 18 WTTQA1 records and 15 REAX controller attachments
still fail import. Their omission must be resolved before disturbance results
can qualify the supplied case.

## Confirmed initialization corrections

The native-record REPCA1 path now interprets its lower reactive deadband as a
magnitude. Matched independent fault runs with +0.025 and -0.025 produce
byte-identical complete exports at both 0.5 ms and 0.25 ms, with two cold runs
per profile. Previously the positive lower threshold introduced a negative
control error at an unchanged operating point. The entered data and historical
typed interval API remain unchanged. A regression fails before the correction
and passes afterward.

EXAC2 now uses exciter-base field current in its KD feedback at runtime, as it
already did during initialization and rectifier loading. The generic machine
signal uses a different base when Xd-Xl differs from one. An independently
generated SMIB confirms the exciter-base initialization equation and stationary
field output. Flat, reference-step and fault references retain all channels at
two timesteps and two cold repeats. The permanent gate compares native initial
coordinates and a coupled flat run; complete native disturbance trajectory
acceptance is a separate claim.

## EXPIC1 availability and observation scope

The exact 24-constant record, with no selector fields, now attaches a dedicated
seven-coordinate Java kernel. It exposes named states, actual initialization
derivatives and predictor/corrector derivative observations. Numerical stages
use reusable arrays. Algebraic bypasses, machine-base bridge measurements,
rectifier loading and the optional exciter field dynamics are explicit.

All 86 supplied EXPIC1 records attach, allowing 31 dependent stabilizers to
attach as well. The total attached-record count increases from 2,555 to 2,672.
The model is classified **LOADABLE**, not independently trajectory-verified.

Twenty-six reference profiles, each cold-regenerated twice, cover both
timesteps, positive/negative reference steps, faults, integral clipping,
instantaneous rate feedback, saturation and constant/current-fed bridge paths.
All raw samples, event-side duplicates and channels are retained. Integrated
coordinates have explicit provisional regression bounds. Algebraic observations
and output timing are also scored, but remain diagnostic: the instantaneous
field output differs by about 1.915 pu on the fault's duplicate timestamp, and
rate-feedback bypass observations differ on reference-step duplicates. These
counterexamples are not omitted or relabeled as accepted trajectories.

An additional constant-source reference with insufficient initial regulator
limits is retained as rejected evidence. The distinct valid profile supplies
sufficient limits; it does not replace that counterexample. Automatic parameter
corrections and additional limiter ports have not been qualified by this slice.

## Preparation-file handling

Model-removal directives now retain their slot selectors through the multi-file
loader and dynamic parser. Type 1 skips the complete stack; types 3, 6 and 7
skip the named stabilizer, exciter and governor respectively. Scoped removal
precedes schema/prerequisite checks and produces an auditable intentional skip.
Unknown model-category mappings are not guessed. Existing BSG and generator
netting preparation remain enabled for the case.

## Ten-second all-bus diagnostic

The unchanged supplied input files are identified by the existing private
source manifest. The diagnostic uses the Java sparse solver, a 1/240 s step,
modified Euler, constant-current active load and constant-impedance reactive
load. Power-flow tolerance is 0.001 pu; the diagnostic dynamic-network
voltage-correction tolerance is 1e-8 pu to isolate equilibrium residuals.

| Quantity | Before both startup fixes | After both fixes |
|---|---:|---:|
| Maximum bus-voltage drift | 0.0836929 pu | 2.77556e-14 pu |
| Maximum speed drift | 2.80444e-4 pu | 1.33227e-15 pu |
| Maximum rotor-angle drift | 6.93880 degrees | 2.18250e-11 degrees |
| Disabled attached controllers | 0 | 0 |

Power flow converges for 6,717 buses; its reported maximum reactive mismatch
is approximately 5e-5 pu. All 120 BSG entries are applied. The all-bus diagnostic
checks finite voltages and machine states throughout and preserves initial,
first-step and final device-state snapshots. Its 73.51-second elapsed time
includes setup and diagnostics and is not a warmed production benchmark.

## Remaining work

- Complete native-compatible handling of the 18 rejected torque records.
  Small independent cases already accept the supplied repeated/decreasing
  power points and selector 2 without changing their readbacks. Nonzero-response
  branch behavior must be established before relaxing production validation.
- Attach the 13 REAX3BU1 and two REAX4BU1 records to their actual converter and
  shared plant-control hosts, with independent interface verification.
- Require complete import, then repeat the all-bus flat run and disturbance
  validation with the full stack. A stationary partial stack cannot substitute
  for that requirement.
- Resolve EXPIC1 bypass/output event phases and extend the model-specific
  validation matrix before claiming complete native trajectory acceptance.

Raw references, scripts, exact tool provenance, readbacks and integrity hashes
are retained in private evidence. Public documentation uses neutral reference
terminology.

## Regression validation

The complete registered plugin reactor passes 4,093 tests with 86 existing
skips and zero failures/errors: 442 tests in the core module, 468 in the
three-phase module and 3,269 integration tests, including skips in those module
totals. All 402 offline tooling checks pass. The six-case short no-event,
three-cycle-fault and generator-trip smokes and strict ten-second ALLSYM
equilibrium gate run successfully. An additional opt-in run completes all
fifteen topology-selected fault regressions with unique output names.

These are regression gates, not complete independent trajectory acceptance
for the partially attached Texas7k case. The source inventory, raw references,
bounded logs and complete test reports are indexed by the private checkpoint
`verification-artifacts/2026-10-24-texas7k-startup`.
