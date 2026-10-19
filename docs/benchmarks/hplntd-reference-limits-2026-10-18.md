# Hybrid plant direct-reference limits and native voltage discrepancy

## Outcome

Direct-reference probes distinguish an observed native raw-VAR voltage-reference
bypass from an InterPSS initialization defect. InterPSS retains the published
voltage clamp and strict equilibrium-reference validation. No production model
equation, limiter, integration method or existing trajectory bound is changed.

The installed model-library diagram places the voltage-reference clamp ahead
of the voltage error. [PowerWorld's related REPC_D reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Bus%20REPC_D.htm)
also defines minimum/maximum voltage references. It is a separately identified
dialect, not evidence of identical runtime callbacks. Its published parameter
description alone is not a PowerWorld trajectory benchmark.

In the observed native build, VAR0 steps above and below the configured bounds
feed the voltage error without that clamp. This happens with initial references
inside and outside the bounds, zero and positive reactive integral gain, and a
large +2 pu reference step. Thus it is not explained by expanding a bound only
to accommodate the initialized reference. The downstream error/output limiters
remain active: the large step reaches Emax rather than bypassing the entire
reactive path. Native raw-VAR callback behavior is the established scope of
this finding; no claim about unseen simulator code is needed.

The separate Q-reference and PF-reference paths do apply their published limits.
These source-conforming disturbances now have four accepted two-step state-replay
and bounded imported-SMIB trajectory profiles. The twelve voltage profiles are
diagnostics, **not** accepted voltage trajectories or a full-model verification.

## Reproduction and evidence classification

The eight configurations are declared in `tools/hplntd_reference_limit_probe.py`.
Each has 0.5/0.25 ms versions and two independent cold runs, retaining all 89 raw
channels and all 405/805 samples, including event/pause duplicates. Every one of
the sixteen cold pairs has byte-identical CSVs; no columns are normalized away.

```text
reference-python tools/hplntd_reference_limit_probe.py UNIQUE_TAG 0.0005 MODE
reference-python tools/hplntd_reference_limit_probe.py UNIQUE_TAG 0.00025 MODE
python tools/audit_hplntd_reference_limits.py
```

`v-outside-up/down` start above Vrefmax; `v-inside-up/down/large` start inside
the limits and cross one; `v-integral-narrow` uses positive Ki with a narrowed
reference interval. They are diagnostic-only profiles. `q-upper` steps Qref
above its maximum, and `pf-lower` crosses the minimum permitted positive PF
magnitude. Only these last two configurations enter trajectory acceptance.

The native voltage-error algebraic residual is below 1e-10 pu for the unbounded
reference equation, while its disagreement with the published limited equation
reaches about 0.928208 pu. Both high- and low-side probes distinguish bypass
from limiting. Q/PF algebraic residuals are below 2.236e-8 pu and their limits
must activate in the tests. These are per-sample checks, not conclusions drawn
from successful process exit or a finite final state.

Every plant state plus four absolute/incremental outputs is replayed for the
Q/PF acceptance profiles without injecting native states; the largest error is
approximately 3.601e-6 pu. Imported coupled tests retain four voltages, four unit
powers, thirteen plant states and four downstream states. Their largest Qref
slew disagreement decreases from about 0.012380 to 0.006192 pu on halving the
step. The known native STORE/event-history versus modified-Euler difference is
kept explicit; the source-modified-Euler scalar recurrence is checked independently
at every accepted endpoint. New per-channel limits are approximately 10% above
observed maxima with a 1e-12 floor. No prior limit is relaxed.

## Regression protection and provenance

The reference runner accepts explicit external reference offsets; its default
active/Q reference steps remain unchanged. Fresh default fault and controller
runs at both steps reproduce the earlier frozen CSV hashes exactly.

Historical audits now pin the generating helper's original SHA-256 rather than
requiring that its archived code equal a subsequently extended helper. Every
archived file hash is still checked, and historical raw outputs and sources are
unchanged. New profiles require the exact current generating helper. Offline
tests guard permitted reference offsets, explicit event contracts, token-only
mutations, unchanged defaults and the historical provenance boundary.

A mutation control removes only the voltage clamp in the isolated verification
snapshot. The published-clamp regression then fails exactly once; the original
kernel is automatically restored. Final gates run after restoration. This proves
that the green diagnostic suite does not bless removal of the published limiter.

## Final gates and retained artifacts

Final baseline `1b199a318` differs from `aa9c26bcf` only by a concurrent
documentation commit. After restoring the mutation, 41 focused checks,
393 plugin-core tests (six existing optional skips), and 1,519 selected
SMIB/model/solver tests (one existing diagnostic skip) pass with zero
failures/errors. Thirty-two offline probe/audit/wording checks pass as well.
The mutation gate has one intentionally failing assertion, separately retained.

The private archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-hplntd-reference-limits`.
It contains 32 new raw discriminator runs, four default-run regressions, exact
inputs, scripts, source/runtime hashes, all accepted paired traces, maximum-error
times, the mutation/restoration evidence and final Surefire reports. Earlier
sealed archives and all production Java are checked for immutability. Unrelated
working edits are excluded from this slice and the isolated test snapshot.

## Remaining work

The raw-VAR voltage-bound discrepancy is localized, not converted into native
voltage parity. An independent published-diagram voltage benchmark, any explicit
native-compatibility boundary decision, the suspect nonzero-deadband initialization,
branchless/alternate hosts, switching and remaining lifecycle/fault-callback
coverage are still open. The broader coverage goal remains incomplete. No full
Texas2k dynamic simulation is run or required for this slice.
