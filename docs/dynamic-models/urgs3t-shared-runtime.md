# URGS3T shared runtime and independent qualification

URGS3T has an exact standard-record import path and a shared Java equation
implementation. Its catalog status is `LOADABLE`; complete trajectory acceptance
remains open. The GAST implementation is not used as a substitute.

## Numerical contract

The record contains 33 constants, in the order exposed by
`Urgs3tGovernor.CONSTANT_NAMES`. Import rejects a short or extended record and
invalid finite-time, rating, droop, limit and curve settings. Unsupported bypass
combinations are rejected rather than silently replaced with arbitrary times.
The entered constants remain unchanged.

The runtime stores five continuous coordinates: valve, engine compensation,
exhaust temperature, valve average and governor lead-lag compensation. Modified
Euler and the shared staggered solver evaluate the same right-hand side and
project the same valve bounds. Numerical stages use reusable primitive arrays;
state dictionaries are materialized only when requested. Tests measure warmed
per-step allocation in both paths.

Deadband memory and turbine-output publication advance at accepted endpoints.
Trial evaluations and zero-duration refreshes do not commit either history.
Discrete reference commands use `applyReferenceEvent`, which preserves the
first accepted left derivative across multiple command writes. Continuous
upstream reference updates use `setRefPoint`. Reinitialization resets the
reference revision, output history and state diagnostics.

The opening-rate limit is applied before division by `T1`; it does not impose
a symmetric closing-rate limit. The load-limit branch changes the opening
bound when the valve exceeds its filtered average by `Linc`.

## Explicit source and diagram conventions

The independent executable and its published diagram differ. These are
represented by immutable `RuntimeConvention` values, not by fitted parameters:

| Behavior | `PUBLISHED_DIAGRAM` | `SOURCE_RECORD` |
|---|---|---|
| Exhaust coordinate | First-order lag driven by engine output | Holds its initialized value |
| Turbine output | Live algebraic output | Previous accepted turbine output |
| Damping | On machine base after turbine rating conversion | Inside the turbine rating conversion, using current speed |
| Power deadband | Ordinary backlash/play function | Direction relative to accepted output selects input minus/plus the entered width |

Direct Java construction selects the diagram convention. The standard-record
builder explicitly selects the source convention. This source behavior is
qualified against the exact executable identified in the private evidence;
it is not a claim about every implementation or release of the model.

The thermal discriminator changes `T3` from 0.05 through 0.5 to 5 seconds with
the temperature limiter active. All recorded trajectories are byte-identical.
The valve-increment residual is below `1e-7` with held exhaust feedback; a
reconstructed dynamic lag produces an error above `2e-5`. Thus the held state
cannot be explained solely as a stale observation channel.

Mechanical-power timing is checked independently through the machine swing
increments. Using recorded mechanical power reconstructs the increment within
`1e-10`; substituting the live turbine output fails that discriminator. A
non-unit rating separately distinguishes damping before and after conversion.
No sample shifts are applied to the trajectory acceptance comparisons.

The isolated power-deadband controls vary the entered width over three values,
at two integration intervals, with faults and command reversals. The source
branch selects `input - Db2` above the accepted output, `input + Db2` below it,
and the accepted output at equality. Its reconstructed allocated output differs
by less than `3e-8`; ordinary backlash fails the independent discriminator.
This is an executable-specific behavior, not a replacement definition for
backlash in other models. Both branches remain pure during trial evaluation.

## Reference evidence and remaining work

The synthetic two-bus captures retain all controller states, allocated variables,
machine power/speed/angle/excitation, both bus voltages and angles, raw outputs,
exact inputs, runnable probes and hashes. Cold repeats must be identical.
Comparisons preserve the two held startup samples and every event-side duplicate.
The serialized reference clock is reconstructed from its documented single-
precision accumulation; physical samples are neither interpolated nor discarded.

The suite exercises equilibrium, nonlinear valve curves, rating/idle power,
governor and engine lead-lags, opening-rate limits, active temperature limits,
isolated and combined deadbands, command reversals and fault clearing. It
explicitly verifies that each run uses the requested integration path.

Qualification is incomplete. Resolve remaining source power-deadband and
command-response state/boundary errors without changing acceptance
limits. Extend bypass and time-preparation rules, bound expansion, reconnection,
longer duration and production-step coverage. Full-system tests without this
model establish non-regression only. Raw allocated variables and published
power are retained for diagnosis; passing selected state and boundary gates
does not certify every observation channel.

The command discrepancies must not be conflated. Modified Euler's largest
valve error occurs immediately after a reference reversal. In the sustained
opening-rate segment, reconstructing each reference state increment with
single-precision rounding matches the serialized state within `1e-12`; the
double-precision increment differs by more than `1e-8` per step. Production
continues to use double-precision integration. Small terminal-voltage responses
also fail the unchanged range-normalized gate despite micro-pu absolute errors.
These observations localize remaining work; they do not waive those gates.

## Validation checkpoint

The final registered suite executes 4,159 tests, with 36 failures, no errors
and 13 skips. The 16 pre-existing failure identities are unchanged; the other
20 are the explicitly registered URGS3T command-response comparisons. The core
plugin module passes 486 tests with four skips and no failures or errors.

Within URGS3T, nine runtime/ownership/history/allocation tests and three
independent equation discriminators pass. Fifty of seventy coupled comparisons
pass the declared state and voltage/power/angle gates, including every flat and
fault comparison. Command failures remain visible in the registered suite.
This checkpoint is not `VERIFIED` or system acceptance.

Private immutable capture archives retain the complete cold-run evidence; the
current fixture is `urgs3t-initial-20261102/captures-v3.zip`, pinned by the test to
SHA-256 `6045fffa8c31aad9668cfb8f6ebad78dd50be034523d2ccaef5395ad4c47f289`.
The adjacent validation archive retains complete final trajectories, test
reports, source and changed runtime classes over the preceding validated
runtime archive. Earlier unsuccessful comparisons remain documented in the
bounded diagnostic logs.
