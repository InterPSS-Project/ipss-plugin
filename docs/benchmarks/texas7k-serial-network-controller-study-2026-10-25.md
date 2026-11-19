# Texas7k serial network and controller experiments

The complete ten-second, single-CPU objective remains 20 seconds, including
recording and export. This study checks exact traversal changes, two forms of
network acceleration and direct stabilizer parameter bindings. Experimental
network changes are not promoted to the production solver.

Every complete run retains 2,403 boundaries, all 6,717 bus-voltage channels
and 15 channels from three selected machines: 16,176,996 float64 values.
Physical timestamps, solver timestamps and both event sides are preserved.
The network step remains 1/240 s, voltage-correction tolerance remains 1e-4 pu,
fresh solves require at least two iterations and the maximum remains 50.
All sparse factorization and solve code is Java.

The Java process is pinned to logical CPU 6, mask `0x40`, verified before and
after each timing batch. It uses `ActiveProcessorCount=1`, serial garbage
collection and ordinary serial device updates. Each matched batch contains
six complete alternating warmups followed by three measurements per mode.

## Exact traversal changes

A membership-versioned inventory skips branch traversal when every standard
branch has an empty dynamic-device list. It is checked after bus callbacks,
so same-stage branch attachments and removals remain visible. Static-row
threshold checks avoid redundant magnitude evaluation away from the cutoff;
near the cutoff they retain the original arithmetic. Packed iteration entry
reads rectangular voltage coordinates directly and preserves inactive rows.

The probes pass 1,251,062 raw component comparisons across 312,762 row cases,
32 branch callbacks and 11 membership-mutation checks. A separate predictor,
corrector and frequency-history probe passes 50,400 raw comparisons across
both convergence norms, including 60 observer-triggered fallback steps.

The matched whole-run median changes from 25.600 to 24.630 seconds. Individual
measurements are 26.363 / 25.600 / 24.741 seconds for the control and
24.570 / 24.630 / 26.826 seconds for the candidate. The ranges overlap; this
does not supersede the earlier 24.603-second single-CPU baseline. All twelve
full traces have the retained `cf74add8…` hash, including every recorded value.

## Network acceleration

Packed Anderson acceleration reduces iterations to 23,683, but its cold trial
does not establish a timing gain. A separate frozen local-current Jacobian
uses real sparse factors of the original network matrix minus a read-only
current derivative approximation. Static loads and qualified converter maps
supply those derivatives; omitted derivatives affect the preconditioner only.
Every trial still refreshes the complete physical current injection.

The first chord version performs both complex and real solves during many
iterations. The direct-current version instead solves current balance with
the cached real factors and uses the original complex fixed-point map to
certify convergence. It never accepts a clipped or mixed correction as its
stopping test. Factor failure and repeated failed certificates return to the
ordinary iteration within the same 50-iteration budget.

The direct version performs 17,547 real correction evaluations and 4,800 raw
complex certificates, with three real factorizations. Its matched whole-run
median is 30.660 seconds versus 31.025 seconds for its control. The respective
three-run ranges are 29.342–31.401 and 28.381–31.283 seconds. This is not a
demonstrated improvement and does not replace the retained baseline.

Refreshing the factor once more after each matrix change uses six real
factorizations, 17,551 correction evaluations and 4,938 certificates. It does
not reduce work and is not preferred. Its cold timing is not a warmed result.

The direct sparse solver passes 80,800 exact comparisons with the existing
sparse substitutions, 87,364 dense-solution component checks and 200 affine
or damped-root checks across 100 matrices. Maximum scaled dense error is
9.31e-16. Its lifecycle probe passes 59 checks, including atomic invalid-input
rejection, stale-current rejection, raw-correction stopping and preservation
of differential states. Four existing independent converter profiles pass
with their unchanged tolerances; the factor-refresh variant passes them too.

Against the unaccelerated tighter control, the direct-current version has
worst fault/postfault voltage NRMSE 0.00030750, peak voltage difference
0.00027380 pu and worst recorded full-run rotor-angle RMSE 0.00073765 degrees.
The second tighter control also passes. The factor-refresh and packed
Anderson variants pass the same period-specific gates. These are internal
convergence comparisons; complete independent network acceptance remains open.

## Direct stabilizer bindings

The IEEEST prototype binds frequent public parameters to Java field readers
when compiling the exact standard model class. Parameter writes remain live;
custom subclasses retain their existing provider hook. It preserves the
controller equations, getter order and integration stages.

The focused probe passes 72,204 exact state/output comparisons across all six
input selectors and both stages, 203 live-parameter checks and custom-provider
fallback. All twelve complete timing traces retain the canonical hash.

| Parameter reader | Three measured whole-run values | Median |
| --- | --- | ---: |
| Existing lookup | 32.765 / 36.144 / 39.223 s | 36.144 s |
| Direct live bindings | 31.856 / 29.089 / 27.368 s | 29.089 s |

Network time also varies in this batch, although the network algorithm is
unchanged. The whole-run difference cannot all be assigned to parameter
binding, and this batch does not supersede the earlier 24.603-second baseline.
Recording and export settle to fractions of a second after warmup; network
work and device integration remain the main costs.

The production change is limited to the standard IEEEST parameter-binding
hook. The three new permanent tests verify live writes, custom-provider
fallback and exact compiled-versus-ordinary state/output histories across all
six selectors. All 33 selected integration tests and 10 selected plugin tests
pass without skips. A complete run using the actual compiled production
controller class also reproduces the canonical full trace. The surrounding
network workspace remains experimental. Concurrent edits to the network
solver and dependency version are outside this change; retained test reports
record their actual dependency classpaths.

## Evidence

The [checkpoint](checkpoints/texas7k-serial-network-controller-study-2026-10-25.json)
indexes private source and class archives, reproducible launchers, bounded
logs, all complete and failed trials, all-channel metrics, CPU affinity and
the matched phase timings. Only hash-identical generated traces share storage.
Supplied references remain unchanged. The whole-runtime production promotion
and single-CPU 20-second objective remain open.
