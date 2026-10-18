# Hybrid downstream command-lag kernel

## Scope and source

`ReaxdKernel` implements the two REAXDU lag states on machine MVA base, with
explicit modified-Euler predictor/corrector stages and semantic named states.
The published order is initial command plus weighted plant increment, then lag,
then output limits. Sharing is applied exactly once by the aggregation service.
Initial command coordinates remain fixed.

The frozen Model Library section 27.2, printed pages 907-909, supplies the
allocation, diagram, scaling equations and minimum-time rule. Source SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The PDF diagram and notes were visually reviewed. Exact executable provenance
is retained in private manifests, not inferred from another vendor's dialect.

This is a kernel, not a loadable plant runtime. The HPLNTDU thirteen-state
controller, deferred ownership/binding and network attachment remain absent.
Renewable runtime presence stays **26/27**, with HPLNTD missing. This slice does
not claim full-solver coupled trajectories or independent acceptance of the
clipped command outputs.

## Equations and limits

For active power (and analogously for reactive power):

```
targetP = initialPref + weightedPlantIncrementP
Tp = max(enteredTz, 2*dt)
dXp/dt = (targetP - Xp) / Tp
Pref = clip(Xp, PminRef, PmaxRef)
```

Negative/zero entered lag times remain stored unchanged; only the effective
time is corrected. The lag state is not clipped. The new native limit
discriminators independently show all four lag states continuing beyond their
configured output bounds. Clamping those states would introduce a different
recovery response. Equation tests activate both upper and lower output limits
and retain the internal storage through release.

The kernel rejects unmatched/repeated integration stages, nonfinite data and
out-of-range initialization rather than inventing limit expansion. Its input
is the already weighted increment, not both raw input and sharing gain. An
ignored aggregation dispatch cannot be silently converted into a zero command.

## Independent recorded-input evidence

Eight four-bus/two-unit profiles compare all four lag states, using recorded
interface inputs rather than fitted commands or state reseeding. Initial states
come from the initial device commands, not from later reference state samples.
Every history row and pause duplicate is retained in the nine-column paired
traces; only forward-time simulation intervals advance the kernel.

Four profiles reuse the preceding foundation's flat automatic/fixed-base and
ordinary command-step evidence. Four new profiles activate output limits or
the minimum-time correction at dt=0.0005 and 0.00025 seconds. All four new
profiles were cold-run twice: eight successful runs, exact RAW/DYR/probe/CSV
pair equality, 88 native channels plus time, and 405 or 805 samples per run.
Embedded input/CSV hashes are checked before comparison.

The maximum ordinary-time state residual is 6.43e-6 pu at the coarse step and
1.65e-6 pu at the fine step. Each of the four channels must improve to less
than 35 percent of its coarse-step residual. Per-channel bounds are roughly
ten percent above observed maxima, not one broad family tolerance. Flat
profiles agree exactly. Activating output limits does not clamp state storage.

Minimum-time state residuals peak at 8.44e-5 pu coarse and 4.18e-5 pu fine;
separate per-channel bounds retain that observation. These are not a fixed-model
convergence claim: effective time itself changes with dt under the published
2*dt rule. Recorded endpoint inputs do not expose the reference solver's
internal predictor-stage plant inputs, so replay residuals remain explicit.

## Remaining work

- Implement the thirteen-state HPLNTDU controller and unique stage-coherent
  multi-unit advance, exact import and deferred attachment.
- Complete downstream zero-axis, ignored-unit held-output, trip/return and
  base-load lifecycle policies using independent activating cases. The kernel
  deliberately has no network eligibility or base-load policy yet.
- Verify initial commands outside configured limits and any initialization
  accommodation before widening the currently rejected input scope.
- Record actual downstream command outputs and verify limiter release in the
  independent simulator; native state traces alone do not prove those outputs.
- Add coupled reference/frequency/voltage/fault/MSS tests at two timesteps.
- Resolve the preceding foundation's explicit-base pause/resume counterexample.

No full Texas2k dynamic simulation is run or required for this slice.

## Validation and reproducibility

On plugin baseline `7e2491deb`, all final gates pass with zero failures/errors:
26 focused checks, 362 plugin-core tests (six optional skips), and 1,436
selected SMIB/model/solver tests (one existing diagnostic skip). Six new unit
tests cover equilibrium, named-state immutability, individual integration
stages, endpoint input, both output-limit directions, minimum times, invalid
stages/data and single application of sharing factors. The replay test covers
eight profiles and 4,438 raw rows, comparing all four states independently.

The generated evidence inventory matches its compiled generator. The wording
guard and its six policy tests pass. All report classpaths use the frozen
companion core `38e269ecf`, SHA-256
`fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
No shared dependency installation is replaced.

Private archive `2026-10-18-reaxd-lag-kernel` retains the eight new native runs,
all eight paired traces, exact scripts and inputs, scoped sources, final test
reports, and earlier diagnostics. It also verifies every file in the previous
aggregation-foundation archive without overwriting it. A source audit matches
the scoped production/test files to the tested snapshot and every other
baseline Java/POM to Git. Unrelated worktree changes are excluded.
