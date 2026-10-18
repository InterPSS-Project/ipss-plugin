# REPCGFMC1U fifteen-state kernel and native input replay

Status: kernel implemented, with bounded independent recorded-input comparisons.
No native import handler or network attachment exists yet. Approved renewable
runtime coverage remains 24/27; REPCGFMC1, HPLNTD and REPCCU remain missing
runtimes. This is not complete model verification or coupled network acceptance.

## Equations and integration

The [native foundation contract](repcgfmc-native-contract-2026-10-18.md), including
its source hashes and visually reviewed diagrams, remains authoritative.
`Repcgfmc1Kernel` implements fifteen semantic states in published order: measured
frequency and its output lag; remote/terminal voltage measurements and terminal
communication delay; GFM voltage-reference lag; branch P/Q measurements; voltage
integral and proportional lag; active integral and reference lag; reactive integral
and reference lag; and FFR power. Branch powers enter on system MVA base and
are converted once to plant base. Four outputs retain their distinct P/Q/V/frequency
definitions; no REPCA1 alias is inferred.

The kernel uses explicit modified-Euler predictor/corrector stages. Integrators
are bounded independently of clipped outputs: an interior voltage integral does
not freeze merely because the combined PI output is clipped. Zero-time lags are
algebraic bypasses, not artificial fast states. Getters are pure. Initialization
may defer the model-specific frequency-filter minimum until the first actual
positive predictor without guessing a default step or resetting references.

An independent discriminator shows that native remote-voltage storage remains
at its initialized value when both voltage paths are disabled, even while the
physical remote voltage changes. The kernel now preserves that unused storage.

FFR has separate idle, hold and ramp phases. The timer diagnostic is an absolute
activation timestamp, not elapsed duration. An opposite-frequency event cannot
interrupt an active response; the controller must finish ramping to zero before
re-arming. Accepted-endpoint sampling and an explicit zero-time event callback
allow instantaneous activation without advancing the simulation clock. A network
refresh during a pending predictor cannot create a discrete jump that its cached
start state would overwrite. Ramp polarity is latched through zero to prevent a
predicted zero from reversing the corrector derivative.

## Independent evidence

Twenty new accepted native runs form ten byte-identical cold pairs. The three-bus
testbed retains the existing converter, near-infinite-inertia synchronous reference,
system/machine bases and source impedance. Two steps are used: 0.5 and 0.25 ms.
Profiles independently exercise manual P/Q/frequency references, both active P/Q
integral bounds, ordinary voltage control, both voltage-integral bounds, and FFR
activation/hold/ramp/opposite-event/re-arm behavior. Every export contains 84
channels plus time, including all converter/plant states and VARs and the actual
frequency-measurement bus signal. Complete exports contain 608/1,208 samples,
two negative-time history rows and five duplicate API/event rows.

The replay initializes from solved physical P/Q/V, then uses recorded terminal/
remote voltages, frequency-bus voltage/frequency, physical branch MW/Mvar and
independently declared manual references. It never injects native controller
states or its computed voltage-controller output. All fifteen states are compared
at every nonnegative sample, including event duplicates. Maxima and their times,
four algebraic diagnostics and full comparison traces are retained. Fixed
per-profile/channel limits are approximately 25% above measured maxima, not fitted
gains, limits or initial states.

The earlier sixteen-run foundation archive and eight datasets remain unchanged;
the new twenty runs are additional evidence, not a claim that old runs were
regenerated. No full Texas2k dynamics are included.

## Residuals and explicit limits

The large reference-jump residuals are localized to stored derivative history.
At the first FFR ramp step the native increment is 1.5 times its Euler increment;
the explicit modified-Euler kernel does not imitate that history convention.
At both steps, a dedicated test proves this discriminator and retains both event
sides. Truncation-dominated P/Q transport, voltage-integral/proportional and FFR
power errors decrease under step halving. The largest Q-target residual in the
strong voltage-limit profile is about 1.107e-3/5.579e-4 pu on plant base; the
voltage-integral residual is about 7.925e-4/3.962e-4 pu. These are bounded replay
errors, not a complete numerical-parity claim.

Some absolute states do not converge uniformly toward the native floats. In the
fine reference profile, the measured frequency state can remain exactly one even
when the physical frequency deviation exceeds 4e-6 pu: each filter increment is
below half of a float spacing at one. The ideal double-precision filter therefore
moves while the native state locks. The associated droop residual is retained;
the kernel is not quantized or retuned to conceal it.

The FFR timer resets discontinuously to zero when the power ramp finishes. A
one-step zero-crossing difference can therefore produce a large raw timestamp
difference despite a small power residual. Every sample remains in the comparison:
when both responses are active, activation timestamps must agree within the
declared float-clock tolerance; any sentinel mismatch must be confined to at most
two samples near ramp zero, with both powers within two ramp increments of zero.
Raw timestamp maxima are still printed. This semantic boundary test is not an
exemption for an early/late FFR activation or a missing power response.

## Acceptance and remaining work

All scoped gates pass on baseline `b7529adf1`: 52 focused checks, 318 plugin-core
tests (six optional skips) and 1,364 registered SMIB/model/solver checks (one
existing diagnostic skip), with zero failures/errors. Eleven kernel unit tests
and five native replay/discriminator tests are included in the relevant gates.

The source audit checks 26 scoped source/resource files against the tested
snapshot and 2,064 baseline Java/POM files against the commit. All twenty new
native runs pass initialization/convergence, exact input/array and file-hash
checks; their ten cold pairs are byte-identical. Ten complete replay traces are
finite and hash-recorded. The prior sealed foundation archive's 1,021 files are
rechecked without modification, and the core dependency hash is unchanged.

The local private bundle is
`verification-artifacts/2026-10-18-repcgfmc-kernel/`, containing tested sources,
full reports/traces, exact native scripts/inputs/logs/raw outputs, the validation
summary and a SHA-256 inventory. Earlier diagnostic attempts are retained.

- [x] Fifteen-state equations, initialization, stages, limits, bypasses and named states.
- [x] Ten two-step recorded-input comparisons and cold-paired independent inputs.
- [x] Independent active P/Q/voltage bounds, FFR history/timer and unused-state witnesses.
- [ ] Strict reader and non-overwriting attachment; signed/transformer branch,
  frequency-bus and fallback measurement resolution with consistent bases.
- [ ] Four-output REGFMC1U routing, independent coupled flat/fault/reference/trip
  SMIB profiles and reconnection policy.
- [ ] Broader zero-time, correction, low-voltage gating and lifecycle discriminators;
  resolve remaining precision/callback gaps and add independent PowerWorld artifacts
  where supported before declaring complete verification.
