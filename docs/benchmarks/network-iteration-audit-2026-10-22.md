# Network iteration and reference-event audit

The network iteration count offers a larger performance opportunity than the
recent cache experiments. A depth-one acceleration prototype reduces Case 1
iterations by 27.6%, but it is not promoted: independent-reference accuracy
remains unresolved, active-power error increases, and warmed runtime has not
been measured. Production equations and convergence behavior are unchanged.

## Reference-event correction

The retained Case 1 performance plan applies a bus-7249 fault. The supplied
full-system reference applies a bus-6307 fault. Earlier one-, four- and ten-ST1-step
Case 1 comparisons mistakenly paired those different events. Their independent
reference errors are withdrawn; their implementation-to-implementation
differences remain meaningful because those arms used the same event.
The original private artifacts are preserved, with validity corrections in
their public evidence indexes. Case 2 uses bus 6307 in both sources and is
unaffected by this event-location error.

A fresh Case 1 run uses bus 6307 with the same numerical settings, preparation,
ten ST1 substeps and committed boundary-reuse implementation. Its full-system
comparison covers all 5,191 matching voltage, bus-angle, generator-power and
rotor-angle channels. Case 2 covers 4,501 channels and reproduces its retained
recordings byte for byte. Both reference event timestamps are 0.999990702 and
1.083994627 seconds; actual nominal boundaries are 1 and 1.083333333 seconds.
The comparison preserves event-side duplicates, interpolates only within event
segments, and fits no angle offsets. This checks event identity, not equivalence
of all reference preparation settings or numerical methods.

`tools/dynamic_reference_identity.py` rejects mismatched case, bus, event type
or event timing before calculating errors. Four tests cover the observed wrong-bus
pairing, case/type mismatches, incorrect/nonfinite times, and permissible
reference-grid/roundoff differences. The retained matched comparison invokes it.

## Iteration work

The instrumented original-event runs reproduce complete baseline recordings.
Each contains 2,405 network calls and uses one logical CPU, a quarter-cycle
integration interval and a 1e-5 pu complex-voltage-correction tolerance.

| Run | Network iterations | Mean per call | Median successive correction ratio |
| --- | ---: | ---: | ---: |
| Case 1, original bus-7249 event | 29,544 | 12.28 | 0.575 |
| Case 2, bus-6307 event | 36,369 | 15.12 | 0.589 |

The matched bus-6307 Case 1 control needs 29,699 iterations. These are counts,
not exclusive timing measurements. Instrumentation and cold compilation make
these audit wall times unsuitable for a warmed performance comparison.

The renewable network callback can accumulate a high-voltage correction on
each iteration, so its evaluation cannot generally be treated as a pure
fixed-point map. Separate matched-event audits observed 3,081,984 callbacks in
Case 1 and 3,722,304 in Case 2, with zero accumulated correction changes in both.
This supports a limited experiment; it does not establish general acceleration
eligibility for all models, events or operating points.

## Isolated depth-one experiment

The prototype uses one prior correction and mapped voltage to calculate an
Anderson mixing coefficient, following the fixed-point formulation in the
[SUNDIALS mathematical documentation](https://sundials.readthedocs.io/en/v7.7.0/kinsol/Mathematics_link.html#anderson-acceleration).
History starts afresh for each network solve. Singular/nonfinite histories,
large coefficients or extrapolations, and nonzero renewable accumulated
corrections prevent mixing. Every convergence decision uses the original
unaccelerated network correction at the current guess, before mixing; the
mixed step is never used as a stopping criterion. Voltage-dependent injections
and callbacks still refresh on every iteration. Existing factorization reuse
and all ten ST1 substeps are retained.

Four new numerical tests cover contraction, stopping diagnostics, minimum
iterations, repeated-solve history, singular/nonfinite histories and eligibility
reset. Together with 39 existing focused tests, all 43 pass. This is a prototype
test set, not general solver conformance or production qualification.

The complete matched Case 1 run uses 21,497 iterations and 16,689 mixing updates,
versus 29,699 ordinary iterations. Its largest accepted correction is
9.998410194e-6 pu; renewable accumulated corrections remain zero. It records all
channels and event-side samples.

| Worst full-run channel RMSE against the supplied reference | Ordinary iteration | Depth-one prototype |
| --- | ---: | ---: |
| Rotor angle, degrees | 12.182252 | 12.174528 |
| Bus voltage, pu | 0.01920410 | 0.01919247 |
| Generator active power, MW | 61.211437 | 65.927617 |
| Generator reactive power, Mvar | 46.841438 | 47.148925 |

The corrected ordinary rotor error is not an improvement from the old 42.3457
degree report: the old comparison used incompatible events. Prefault rotor
maximum error remains about 1.13 degrees and prefault voltage maximum error
about 0.0082 pu. Neither run meets the existing accuracy contract. The prototype
also worsens worst-channel active-power RMSE, so reduced iteration count alone
cannot justify promotion. Case 2 acceleration and Cases 3–6 acceleration have
not been tested. No warmed runtime gain or eight-second completion is claimed.

The [evidence index](checkpoints/network-iteration-audit-2026-10-22.json) retains
source overlays, tests, commands, complete per-call diagnostics, callback counts,
affinity, all-channel errors and recording/reference dependencies. Large
recordings are referenced in place. The next acceleration experiment must
retain the residual check, account for stateful callbacks, and be evaluated
against the supplied trajectories before any production timing qualification.
