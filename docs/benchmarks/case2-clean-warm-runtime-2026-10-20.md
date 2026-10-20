# Case 2 clean builds and warmed runtime

After one complete warm-up, three fresh-case simulations in the same JVM take
**12.5364, 11.5482 and 11.9304 seconds** for integration. The median is **11.9304
seconds**. The ten-second single-core target remains open. All 4,810 channels,
2,405 samples and both sides of each event are retained.

## Warm measurement protocol

Each run reloads the unchanged inputs, solves the operating point and initializes
a new network and controller graph. Only the JVM and its compiled code persist.
The first complete ten-second simulation is excluded from the measured results.
The following three runs use one logical CPU, zero simulation worker threads,
1/240-second steps, and 1e-5 maximum-component voltage convergence tolerance.
Each performs 2,400 steps, 4,800 network calls and 38,819 network iterations.

| Run | Integration s | Network s | Dynamic loop with recording s | Runner elapsed s |
| --- | ---: | ---: | ---: | ---: |
| Warm-up, excluded | 14.8098 | 6.9600 | 28.1093 | 42.9058 |
| Measured 1 | 12.5364 | 7.1998 | 25.5083 | 28.9176 |
| Measured 2 | 11.5482 | 6.5383 | 24.4433 | 25.8752 |
| Measured 3 | 11.9304 | 6.8458 | 24.9458 | 26.5955 |

Integration timing encloses each solver step and excludes the subsequent channel
recording. Network timing is a subset of integration time. The dynamic-loop timer
also includes full compressed export and ordinary recording. Runner elapsed time
includes importing, power flow, initialization and output closure; it excludes
Python preparation and Java source compilation. Every run uses a 2 GiB Java heap.
Process affinity is verified before and after the complete four-run sequence.

The user-provided reference timing for Case 2 reports 4,810 channels, 3.969 seconds
of dynamic computation and 7.479 seconds total in process. These are useful target
values; hardware and the reference timer boundaries have not been independently
verified. The warmed candidate still needs about **16.2%** less integration time
to reach ten seconds. Network solving accounts for roughly 57% of measured
integration time. Recording adds about thirteen seconds to the dynamic loop.

## Clean-build correction

Rebuilding the earlier experiment exposed an attribution error in the
[previous report](case2-compiled-controller-single-core-2026-10-20.md). The archived
typed-ST1 source called unsupported method signatures and failed compilation.
Its producer had copied existing classes before compiling, so later runs used an
older `St1DirectEvaluation.class`. Those runs did not verify the typed rewrite.
The original logs and archive are retained unchanged.

The corrected experiment uses the existing numerical kernel for the affected
non-windup calls. Every build starts with an empty classes directory and writes a
completion manifest only after successful compilation. The runner verifies source
and class hashes before execution and runtime hashes afterward. Actual source and
class snapshots accompany the new evidence. The independently built, committed
[compiled controller runtime](compiled-controller-runtime-2026-10-20.md) is
unaffected by the earlier experimental build failure.

The additional experimental changes use indexed network traversal, an invalidated
bus-array cache and guarded voltage-field updates that preserve the existing
notification fallback. They remain experimental overlays. This report does not
promote them into the production solver or claim general subclass compatibility
from the Case 2 fixture alone.

## Verification

The freshly built ST1 evaluator passes 1,000 focused stages with two observations
per stage. The check varies live selector and limiter inputs and includes both
integration flags and positive/zero-time steps. State, stored derivative, input
and output observations are finite and exactly equal to the preceding evaluator.

All four full warm-series exports are byte-identical to the retained control:
**11,568,050 measurements per run**, with matching channel metadata and initial
compensation records. The independently parsed control comparison verifies finite
values, row count and both event sides. Thus the optimization preserves the
control trajectory exactly; it does not resolve the existing differences against
the supplied independent reference.

After verification, five identical generated full traces share immutable storage
with the retained control, avoiding 464,674,410 duplicate bytes. No supplied
reference is changed or removed.

## Earlier fresh-process screens

These runs did not use a warm-up. Ordinary selected-channel recording was enabled;
full diagnostic export was disabled. They are separate screens, not the warmed
qualification above.

| Experiment | Integration s | Network s | Runner elapsed s |
| --- | ---: | ---: | ---: |
| Clean control | 14.2203 | 7.0839 | 23.1930 |
| Clean typed evaluator and indexed traversal | 14.1086 | 6.9414 | 23.1865 |
| Earlier C2 compilation | 13.3777 | 6.3355 | 27.0894 |
| Synchronous C2 compilation | 13.2418 | 6.4666 | 27.8677 |
| Earlier C2 compilation with larger inlining budget | 13.2083 | 6.3803 | 29.3721 |
| Guarded candidate, repeat 1 | 13.2806 | 6.1527 | 28.4899 |
| Guarded candidate, repeat 2 | 13.2886 | 6.4691 | 29.1032 |

All listed selected traces match exactly. The small control/candidate difference
does not establish a separate traversal speedup. Earlier compilation reduces the
measured integration time but increases fresh-process elapsed time. The warmed
protocol above follows the user's instruction to exclude first-run compilation
cost from the performance target.

An endpoint voltage-prediction experiment reduced iterations to 25,513 and took
12.3181 seconds, but changed selected voltages by up to 0.0082231 pu and rotor
angles by 36.8534 degrees. It is rejected for this optimization path. A limited
nominal-ten-second rotor endpoint screen against the supplied reference still
shows errors above 102 degrees for that candidate and above 105 degrees for the
control. That screen is not a full trajectory acceptance calculation and applies
to this quarter-cycle, 1e-5 performance setup.

Compact source, binary, command, hash, focused-test and comparison evidence is
retained privately under `2026-10-20-case2-clean-warm-runtime`. Large traces and
supplied references are retained by path and hash rather than copied into the
archive. Production runtime changes remain in the earlier scoped core and plugin
commits; this follow-up corrects attribution and verifies warmed performance.
