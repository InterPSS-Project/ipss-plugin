# Renewable substeps and cold-point recovery diagnostics

The cross-case performance qualification cannot yet proceed: the retained
quarter-cycle configuration fails Cases 3 and 4 before the fault. Local renewable
substeps remove that early failure in short probes, but do not restore reference
accuracy. A separate cold-point investigation identifies an overly restrictive
reactive-limit release estimate in Case 3. No production default changes in this
checkpoint, and the eight-second complete-run goal remains open.

## Integration discriminator

All probes retain a 1e-5 complex-voltage correction tolerance, one logical CPU,
the original physical model data, current-P/impedance-Q loads, ten ST1 substeps,
and full recording. Network acceleration is disabled in these diagnostics.
These are cold diagnostic runs, not warmed timing measurements.

| Case 3 configuration | Network step | Result |
| --- | ---: | --- |
| Existing renewable integration | 1/240 s | Fails at 0.0458333 s |
| Existing renewable integration | 1/480 s | Completes the 0.1 s probe |
| Existing renewable integration | 1/960 s | Completes 0.1 s and 1.2 s probes |
| Two local renewable substeps | 1/240 s | Completes 0.1 s and 1.2 s probes |
| Four local renewable substeps | 1/240 s | Completes 0.1 s and 1.2 s probes |

Cases 3 and 4 both complete the 1.2-second bus-6307 fault window with two and
four local renewable substeps. The fault starts at 1.0 s and clears at
1.0833333 s. One local substep exactly reproduces the existing failure prefix.
The prototype advances each complete renewable bundle in serial local steps
while retaining the global network interval. Its internal power observations
refresh against the held network voltage. This is an isolated experiment;
general cross-device coupling, event lifecycle and regression coverage remain
unqualified.

The supplied full-system data remain the reference. Every supported voltage,
bus-angle, generator-P/Q and rotor-angle channel is compared: 5,579 in Case 3
and 4,919 in Case 4. All nonnegative actual samples and event sides are retained.
Reference interpolation stays within event segments; no fitted angle or time
offset is applied. Normalization uses the full ten-second reference range.
Negative history rows remain in the raw recordings.

| Case 3 configuration | Maximum voltage error through 0.0458333 s | Through 0.1 s |
| --- | ---: | ---: |
| Existing quarter-cycle path, failed prefix | 0.715079 pu | Not completed |
| Half global step | 0.036011 pu | 0.170732 pu |
| Quarter global step | 0.015327 pu | 0.020578 pu |
| Two local renewable substeps | 0.038556 pu | 0.101447 pu |
| Four local renewable substeps | 0.012486 pu | 0.027933 pu |

The first comparison window uses the same stored actual sample grid. Nominal
window boundaries admit 1e-9 seconds of timestamp accumulation error. All
variants retain two startup integration steps, so changing the global step also
changes the duration of the negative-time history. This confound is reported
rather than aligned away. The actual history spans 8.333333 ms at the original
step and 2.083333 ms at the finest step; the supplied history spans 2 ms.

Later prefault errors remain large. With two/four local substeps, worst voltage
errors through fault application are 0.374742/0.257963 pu in Case 3 and
0.379696/0.361477 pu in Case 4. Corresponding worst rotor-angle errors are
35.4423/40.9190 and 41.3596/44.0360 degrees. The finer global-step Case 3 control
still reaches 0.249908 pu and 33.4890 degrees. Finite execution and removal of
the early divergence do not establish accuracy.

The initial wind power-order derivatives are not automatically defects. The
retained independent wind-stack discriminator already demonstrates a nonzero
startup derivative with the supplied speed-multiplier convention. Those
derivatives and source parameters remain unchanged.

## Cold-point discrepancy and release trial

Before any dynamic integration, Case 3 bus 5170 is at 0.984455170 pu instead
of the supplied 0.999999940 pu. Its generator is held at its -8.64-Mvar minimum;
the reference requires 7.267192 Mvar, within its unchanged 12.78-Mvar maximum.
The discrepancy is already present in the prepared power flow.

The imported limit guess is intended to be reversible. The release controller
correctly observes low voltage at the reactive minimum, but its subsequent
fixed-neighbor estimate predicts 14.847197 Mvar at the target voltage. That
estimate exceeds the upper bound and prevents a release trial. It neglects
the neighboring voltages' response to the control change.

A diagnostic bypasses only that preliminary estimate and retains the existing
transactional trial, convergence checks and rollback machinery. It restores
bus 5170 to 1.0 pu and 7.267151 Mvar. Preparation takes 14 iterations before
netting and zero afterward, with no unsettled controls. Maximum cold voltage
error across all 2,000 buses falls from 0.015544771 to 0.000011302 pu.

This is not permission to remove all feasibility screening. The existing core
regression requires rejection of an infeasible release across a stiff connection
to a fixed voltage. All five existing polar reactive-limit backoff tests pass
with the ordinary filter; the bypass passes four and fails that stiff-connection
guard. This failed regression is retained, and its assertion is unchanged.
A general correction needs to account for network response
while preserving that case and transactional recovery. The successful trial is
retained as a reproducer, not promoted as the default implementation.

Other cold discrepancies remain. Case 4 cold voltage already matches within
0.000004959 pu, yet its largest per-generator reactive-power disagreement is
98.258004 Mvar at bus 2065/unit 1. The Case 3 trial retains a 94.044877-Mvar
disagreement at the same unit. These require a separate generator/shunt
allocation investigation; correcting bus 5170 does not resolve them.

## Evidence and limitations

Fourteen simulation attempts are retained: eleven completed diagnostic prefixes,
two early integration failures and one preparation failure. Disabling all
imported reactive-limit guesses fails during voltage-dependent group-limit
preparation and is not an accepted alternative.

The first release audit called the existing target-Q predictor and failed its
byte-identical recording check. That predictor temporarily changes bus voltage
storage; its invocation is therefore not classified as a non-mutating audit.
Its observations and changed recordings remain retained. No observation from
that run is used as trajectory acceptance evidence.

The [evidence index](checkpoints/renewable-stability-probes-2026-10-22.json)
identifies all commands, source/class snapshots, diagnostic statuses, reference
hashes, full-channel error reports and recording dependencies. Large recordings
remain on the designated artifact drive. Original references are unchanged.
No new warmed runtime, ten-second accuracy result or completed six-case gate is
claimed. Existing worktree performance prototypes are excluded from these runs.
