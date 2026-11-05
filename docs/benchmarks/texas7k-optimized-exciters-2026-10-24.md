# Texas7k additional exciters in the opt-in staggered solver

The opt-in staggered solver now accepts all 137 previously rejected active
exciters in the supplied complete Texas7k case. The ordinary production solver
remains the default. Complete-network disturbance accuracy is still open.

| Family | Active units |
| --- | ---: |
| ESAC1A | 1 |
| ESAC6A | 2 |
| ESDC1A | 5 |
| ESDC2A | 2 |
| EXAC1 | 3 |
| EXAC2 | 42 |
| EXPIC1 | 75 |
| SCRX | 7 |

## Integration and state access

EXAC1, EXAC2, ESAC1A and SCRX reuse their existing annotated blocks. ESAC6A,
ESDC1A/2A and EXPIC1 expose state read, derivative evaluation and projected
state application through the existing participant interface. These ports
reuse the existing right-hand sides and limiter functions. ESAC6A and
ESDC1A/2A now also expose named integration states and stored derivative
snapshots. Derivative evaluation does not advance differential states.

Complete-case execution also requires the existing fixed-current converter
port and the REAX/PLNT upstream groups. Shared upstream controllers are bound
by object identity and advance once per network step, even when several
converters reference them. These additions do not change imported parameters,
load conversion, BSG settings or the sparse-solver implementation.

## Local numerical resolution

A single quarter-cycle staggered step initially failed the complete no-event
test. EXAC2 at bus 200169 was the first growing controller state: its regulator
departure grew from roundoff to 0.0469 pu by two seconds. Halving the entire
network step removed this numerical growth.

The additional exciter families now receive an initialization-time numerical
stability check. A finite-difference Jacobian of their existing, initialized
equations supplies the continuous poles. The two-step recurrence's roots select
a power-of-two local count with a 20 percent step-size margin. Initial states
are restored after the check. Physical growing modes are not clamped away.

This selects two local steps for the exciter at bus 200169 and one for the other
136 additional exciters. The network step remains 1/240 s. This local check is
an operating-point resolution aid, not a guarantee for every nonlinear
trajectory or limiter transition.

`setExciterSubsteps(family, count)` provides an explicit count before
initialization; `setAutomaticExciterSubsteps(false)` selects a single step
unless explicitly overridden. `getResolvedExciterSubsteps()` reports the
resolved machine/count map. Reinitialize after changing equation coefficients
or model attachments. Notify the solver after an external discontinuity.
The separate ST1 setting retains its existing default of one local step.

## Accuracy checks and limits

The complete ten-second no-event gate passes with all 2,705 dynamic records,
all 120 BSG entries and zero disabled attached controllers:

| Quantity | Maximum observed drift |
| --- | ---: |
| All-bus voltage | 2.57572e-14 pu |
| Machine speed | 1.22125e-15 pu |
| Machine rotor angle | 2.04510e-11 degrees |
| Checked machine/controller coordinates | 9,541, each within 1e-8 |

The existing independent EXAC1/2, ESAC1A/6A, ESDC1A/2A and SCRX comparisons
also run with the optimized solver. At a 0.25 ms optimized step they pass the
existing channel tolerances against the existing 0.5 ms reference samples.
No comparison bounds were widened. At the same 0.5 ms step, four optimized
comparisons exceeded the existing voltage or machine-state bounds; tightening
the network tolerance did not remove those differences. Step refinement is
therefore part of this SMIB evidence, and these results do not qualify
quarter-cycle full-network trajectory accuracy. The existing comparison
windows are retained; they are not an all-event-side parity claim.

EXPIC1's existing independent replay gates remain in force. Previously open
algebraic event-phase differences remain open. Adding a state port does not
upgrade its catalog verification status.

## Complete fault run and performance

The complete five-cycle bus-111180 fault finishes through ten seconds with a
1/240 s network step, 1e-4 pu voltage-correction tolerance and the 50-iteration
ceiling. Every run retains 2,403 float64 samples, all 6,717 bus-voltage channels,
15 selected machine channels and both sides of each event. Each binary trace
is 129,464,040 bytes. Timing includes setup, recording and output serialization.

One warmup followed by two measured runs, using the same Java-only solver,
logical CPU 6 and tuned JVM settings as the ordinary comparison, gives:

| Run | Whole-run elapsed time |
| --- | ---: |
| Warmup | 65.069 s |
| Measured 1 | 57.038 s |
| Measured 2 | 51.296 s |

The ordinary matched warm control measured 91.384 and 92.170 s. The optimized
timings are diagnostic: their fault trajectories are not accepted. Against
the ordinary control, the largest voltage difference is 0.422692 pu at bus
150510, and the largest recorded rotor-angle RMSE is 4.64477 degrees. These
are differences between implementations, not errors established against an
independent full-network reference.

All three optimized traces are byte-identical. They make 2,403 network calls
and 58,908 iterations, with a maximum of 32 iterations in one call. Network
work takes 30.209 s in the second measured run, so controller support alone
does not meet the whole-run target.

The prefault voltage difference is below 1.65e-14 pu; the large departure starts
during the fault. Giving all eight additional exciter families four local
steps leaves the largest voltage difference at 0.422692 pu. This discriminator
does not support attributing the large discrepancy to their local resolution.
Bus 150510 has the existing REGCA1/REECA1, plant and wind-controller bundle.
Its integration/network boundary remains the next localization target.

The 20-second whole-run target and independent full-system disturbance
acceptance remain unmet. The faster path is not promoted to the default.

## Regression status

The focused selection passes 183 tests with no skips, failures or errors. It
includes the new state-purity, initialization-preservation, explicit-count,
activation and stability-region tests, existing independent SMIB comparisons,
EXPIC1 replay, converter/upstream controls and reference-manifest integrity.

The registered core plugin suite runs 2,698 tests with five failures and twelve
skips. All five failure methods reproduce using the production classes from
the preceding commit, `faca1e27c`, in a separate runtime overlay. The failures
are the REECC1 fault comparison, endpoint-monitor fault execution, two supplied
six-case disturbance smokes and the opt-in six-case stationary diagnostic.
Several network solves reach the 50-iteration ceiling. The opt-in diagnostic
also observes motion in supplied operating points that are not stationary
fixtures. These are retained failures, not accepted by widening tolerances or
raising the iteration limit. This extension is not a green milestone merge.

The full registered reactor runs 4,230 tests: 4,140 pass, 85 are skipped and
five fail, with no errors. The core module runs 442 tests and the three-phase
module 468, both without failures. Integration runs 3,320 tests with five
failures. Its supplied-case stationary diagnostic is disabled by its ordinary
default; the five failures instead include the topology matrix, which stops
at a Case 5 nonconvergence. All five complete Texas7k tests pass in both runs.

An additional independent-site execution attempts all fifteen reviewed
topology faults without stopping on an earlier failure: twelve pass and three
Case 5 sites fail (conventional terminal, renewable plant and weak grid).
All three reproduce on `faca1e27c` at the same simulation times.
The iteration ceiling remains 50. No reference thresholds, channels or samples
are removed to obtain these results. The test opt-ins are restored after each
registered validation run.

The [machine-readable checkpoint](checkpoints/texas7k-optimized-exciters-2026-10-24.json)
indexes private inputs, retained full traces, state snapshots, test logs and
reproducer sources. A bounded diagnostic runtime archive preserves the model
classes and dependencies; it is explicitly marked as unaccepted for fault
accuracy. Large traces are indexed in place rather than copied into the archive.
