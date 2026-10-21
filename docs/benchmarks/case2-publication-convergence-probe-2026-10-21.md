# Case 2: convergence reduction during voltage publication

The guarded publication-reduction prototype is **not adopted**. It preserves
the complete Case 2 recording but is slower than its control: 9.6673181 versus
9.4254708 seconds median, a 0.2418473-second increase (about 2.6%). Production
simulation source remains unchanged.

## Matched experiment

Both campaigns used 32 full warmups followed by seven complete-case
measurements in a fresh JVM. Each case includes fresh import, power-flow
preparation, dynamic initialization, ten simulated seconds, and full recording.
The step remains `1/240` second, the complex-voltage correction tolerance remains
`1e-5`, and OS process affinity is verified as one logical processor before and
after each campaign. The load-flow tolerance remains `1e-3` pu. No measurements
were discarded and compilation time was not subtracted.

| Configuration | Complete-case median | Measured range | Publication reductions per case |
| --- | --- | --- | --- |
| Same overlay, reduction disabled | 9.425471 s | 9.339831–9.565607 s | 0 |
| Same overlay, reduction enabled | 9.667318 s | 9.591492–12.371150 s | 33,964 |

Measurements in execution order:

- Control: `9.4254708, 9.3398312, 9.4488977, 9.5656073, 9.3485489, 9.4186642, 9.500908`.
- Candidate: `9.6673181, 9.6239649, 9.5914918, 9.777489, 12.3711504, 9.6334351, 9.7856482`.

The 12.3711504-second candidate measurement recorded 2,673 milliseconds of
compilation. It is retained. Even the fastest candidate measurement exceeds the
slowest control measurement. The median simulation/output phases are 9.4396162
seconds candidate and 9.2074147 seconds control; phase medians need not sum to
the complete-case median. Campaigns were sequential, not randomized. This
supports rejecting this implementation, not assigning a universal cost to all
possible publication-convergence designs.

The control includes the same new classes with the reduction disabled. It is
not a new untouched-production timing campaign. The original 5,275 effective
production class files were rechecked and are unchanged. No other builds,
profiles, or heavy analysis ran concurrently with the qualifying measurements.

## Implementation and equivalence

The candidate reduces voltage corrections during network voltage publication.
It stages new history values and commits them only after a successful complete
solve. This preserves existing failure behavior: a failed solve must not leave
partially updated convergence history. The first ordinary scan validates the
layout; later reductions require the same tracked standard network, bus list,
table revision, coordinate mode, and standard sparse matrix implementation.

Custom publication, observers, changed membership, escaped table views,
non-finite values, or changed scan settings select the ordinary scan. The commit
holds the existing table monitor. Voltage-dependent current injections and
network iteration callbacks continue through their existing paths. The design
removes repeated voltage reads and moves the arithmetic, but still needs a
buffer commit pass and eligibility checks. The total timing does not justify
that additional machinery in this form.

All **43 focused tests passed**, including both convergence norms, exact
tolerance boundaries, extreme finite arithmetic, ties, minimum-iteration
behavior, invalid values, table mutations, structural changes, a failed solve
after an earlier bus was published, and a late observer that changes an earlier
bus. Existing voltage-table and network-callback lifecycle tests were included.

All **80 complete cases** (two initial comparison runs plus both 39-run timing
campaigns) produced the same full recording as the retained baseline:

`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`

The recording retains 2,405 samples, 4,810 channels and event-side duplicates.
Selected-channel CSVs also match. Equality checks and hard-link storage sharing
occur after the timed case. This preserves numerical behavior for this Case 2
configuration; independent-reference accuracy remains unresolved.

## Decision and retained evidence

Keep the current production scan and the faster frozen executable. This result
does not close the production-to-frozen performance gap. The next comparison
should isolate integration preparation and current-assembly costs against the
frozen executable, with profiles from the actual compared paths rather than
assuming the extra scan explains the difference.

The [checkpoint index](checkpoints/case2-publication-convergence-probe-2026-10-21.json)
pins a private evidence archive containing the prototype sources and classes,
tests, build/run commands, all timing statuses, bounded logs, affinity evidence,
and recording hashes. The generated recording paths remain available through
verified hard links; supplied reference files were not changed.
