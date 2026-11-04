# Compressor startup phase-measurement clock

Status: the shared compressor runtime preserves the independently observed
startup phase history under modified Euler and staggered integration. The
controls cover initial partial/complete thermal shedding, active P/Q frequency
sensitivity and repeated faults. Complete composite-load qualification,
restoration and production-interval/long-duration coverage remain open.
Coupled zero-filter raw-frequency trajectories remain diagnostic; their
input-increment discrepancy is retained and localized below.

## Physical time and accepted measurement history

An initial physical action can change terminal phase before time zero. The
independent captures retain the original phase-measurement memory throughout
the negative startup intervals and at time zero. The first positive endpoint
samples the new phase. With a positive frequency-filter time constant, the
filtered state first responds at the following endpoint.
Runs starting at time zero retain the existing positive-endpoint sampling
sequence. Negative startup intervals remain an explicit caller configuration.

Previously the compressor used elapsed accepted intervals to sample phase
during negative startup. That processed the initial phase jump too early.
This was a measurement-history error even when zero frequency sensitivity
prevented it from changing terminal power.

`IntegrationStepContext.prepareIntegrationStep(startTimeSec, stepSec)` now
delivers physical time separately from the integration duration. The common
solver prepares its active bus-device recipients once per step through a list
built during initialization. Both solver entrypoints share this dispatch.
There is no discovery, expression evaluation or context allocation in the RHS.

Preparation changes only pending clock context. It cannot sample a voltage,
integrate a state or commit a timer. The compressor consumes that context in
its accepted `afterStep` boundary. Its phase memory and sample clock wait
through nonpositive endpoints; voltage, thermal and protection coordinates
continue integrating. No state is seeded from reference measurements in a
coupled run, and no recorded trajectory is shifted.

The selected binary32 or double sample clock remains distinct from the global
simulation clock. Positive accepted intervals advance it using the existing
precision policy. Scheduled network-event preparation remains a zero-time
operation with its separately qualified frequency-input convention.

Reinitialization clears pending context and measurement history. Repeated RHS,
current and zero-duration calls cannot consume a positive interval. Low-level
clients that drive negative startup directly must supply the physical context;
duration-only callers retain their existing positive-time sampling convention.
The bus-frequency input mode retains its existing measurement behavior.

## Independent checks and acceptance

Cold-repeat captures preserve all original channels, both negative startup
samples and every event-side duplicate. Recorded-phase replay independently
integrates the frequency coordinate; its other continuous coordinates are fed
explicitly and do not constitute coupled-state acceptance. The replay checks
the sample clock and wrapped phase-derivative equation at every accepted row.
Filtered staggered state/output replay retains a 2e-7 pu maximum-error gate. Modified
Euler is checked against its held-input Heun equation to 2e-15 and the exact
one-pole response with its accumulated truncation bound; its complete errors
against the different source integration are also retained. With zero filter
time constant, both methods check the direct phase-derivative coordinate to
2e-6 pu and the published output to 2e-7 pu, retaining their distinct endpoint
observations. The new controls qualify the bypass during nonflat startup too.

Full imported SMIB controls integrate every state under each method. They
include no initial shedding, partial shedding and complete shedding, nonzero
active/reactive frequency gains, and two integration intervals. Both methods
must keep the frequency state and sample clock unchanged through time zero.
Separate tests prove that other coordinates continue moving during startup.

For the complete-trip control at 0.5 ms, the staggered frequency-state peak
error decreases from approximately 0.00687 pu to 0.00000180 pu. Coupled staggered
gates are range-normalized RMSE 2e-6 for voltage, 4e-5 for P and 3e-6 for Q;
absolute RMSE is limited to 0.0002 degrees for rotor angle and 2e-7 pu for speed.
Filtered frequency state/output maximum error is limited to 5e-6 pu. The new controls use
a 3e-6 temperature-state maximum limit, 5e-5 for thermal fractions and 2e-8
seconds for timers. Earlier control limits are unchanged.

Modified Euler retains the previously declared thermal-control boundary
criteria: at 0.5 ms, range-normalized RMSE 1e-4 for voltage and 1e-3 for P/Q,
absolute RMSE 0.01 degrees for angle and 5e-6 pu for speed. These limits scale
with the smaller interval, and paired refinement must reduce boundary RMSE
with explicit observation-precision floors. It does not claim pointwise
frequency-state parity with the source two-step integration: discontinuity
peaks of approximately 0.022 pu remain in the unmasked reports. The corrected
startup clock does not change that numerical-method distinction.

The zero-filter coupled controls retain their complete raw frequency differences
separately. A direct derivative amplifies small changes in successive terminal
phase inputs. At the declared 1e-4 network tolerance, the largest staggered
raw-frequency difference in these controls is approximately 0.00030 pu;
modified Euler reaches approximately 0.00461 pu. These are diagnostic raw-state
trajectories, rather than a claim of frequency-state parity. Every sample's
state difference is decomposed into the two independently recorded terminal
phase-increment inputs; both the source equation error and unexplained residual
must remain below the existing 2e-6 pu bypass-observation limit. Separate
recorded-input tests qualify the equation and output lifecycle. Coupled boundary
criteria and startup-clock checks remain active for the bypass controls.

A retained 1e-6 network-tolerance diagnostic reduces some differentiated errors,
but does not remove all of them. It is evidence for further network/input
qualification and does not change the production or acceptance settings.

No complete-run speedup is claimed. Warm restoration, other initial-action
interactions, parameter validation, and the composite's separate internal
network/static-load discrepancy remain outside this qualification.

## Localizing the remaining startup thermal errors

After the machine event-history correction, the no-initial-shedding controls
retain two failing coupled staggered temperature gates at the 0.5 ms interval.
The maximum errors are 3.183542e-6 pu with the frequency filter and 3.165888e-6 pu
with its bypass, against the unchanged 3e-6 pu limit. Both motor groups have the
same error. These are incomplete trajectory qualifications, not new thermal
model acceptance.

The regression now completes both integration intervals even if the coarse
comparison fails. It retains the declared method gates before evaluating the
boundary and state assertions, reports both temperature coordinates, and keeps
all startup samples and event-side duplicates. Modified-Euler criteria and
paired refinement checks remain unchanged.

An independent thermal reconstruction uses the entered resistance and thermal
time constant, the solved initial temperature, accepted heating-current squared,
and the staggered two-step equation. Fresh left and right event rates remain
separate from older integration history. The reconstruction reproduces the
coupled implementation temperatures to the 2e-13 pu equation-residual gate.
It introduces no production allocations, equation changes or precision changes.
Retained negative controls reject both substituting older integration history
for the fresh left rate and omitting event-rate averaging. The smallest error
among those deliberately incorrect reconstructions is 1.920835e-6 pu; the
correct reconstruction has zero residual across the retained profiles.

A second reconstruction substitutes the independently recorded heating currents
while retaining the same entered coefficients and solved initial temperature.
It explicitly applies the recorded complete-trip projection; the test separately
requires agreement of the coupled trip sample and side, so projection cannot
conceal a trip-timing mismatch. This is input localization, not a replacement
for a complete coupled comparison.

| Frequency input | Interval | Coupled temperature maximum error | Recorded-current reconstruction maximum error |
|---|---:|---:|---:|
| Filtered | 0.5 ms | 3.183542e-6 pu | 4.516389e-7 pu |
| Bypass | 0.5 ms | 3.165888e-6 pu | 3.369259e-7 pu |
| Filtered | 0.25 ms | 1.563264e-6 pu | 1.040704e-6 pu |
| Bypass | 0.25 ms | 2.005494e-6 pu | 9.023780e-7 pu |

The prescribed-current residual is not monotonic with the interval; reference
observation and integration behavior remain part of that diagnostic. Its gates
are 4.6e-7 pu at 0.5 ms and 1.1e-6 pu at 0.25 ms. Neither changes the coupled
temperature gate. The complete finer-step runs pass that original gate.

Separate retained controls at network tolerances 1e-4, 1e-6 and 1e-8 show that
tightening the network solve does not remove the coarse temperature discrepancy.
At 1e-8, the filtered and bypass maxima remain 3.194262e-6 and 3.172278e-6 pu.
The evidence localizes the dominant coarse discrepancy to the coupled heating
inputs rather than an incorrect thermal recurrence. Resolve those upstream
electrical/frequency inputs without fitting thermal parameters or changing the
acceptance limit. Full compressor and composite-model qualification remain open.

### Stalled-voltage contribution to the remaining error

A further independent decomposition separates the heating-input difference into
stalled terminal-voltage history, stalled current-law residual, and running
current history. Each contribution obeys the same linear thermal recurrence,
with separate event-side rates and complete-trip projection. Their sum reproduces
the recorded-current reconstruction difference at every retained sample within
`2e-13` pu. The stalled current law independently satisfies
`I² = V² / (Rstall² + Xstall²)` within `5e-13` pu; it has no frequency factor.

At the two failing coarse-step temperature peaks:

| Input | Temperature error | Stalled voltage contribution | Stalled current-law residual contribution | Running current contribution |
|---|---:|---:|---:|---:|
| Filtered | 3.183541e-6 | 2.947112e-6 | 1.178617e-7 | -1.486782e-9 |
| Bypass | 3.165888e-6 | 2.806275e-6 | 1.144255e-7 | -1.069041e-9 |

All values are pu temperature. The difference between total temperature error
and the three input contributions remains in the independently recorded-current
thermal reconstruction. Thus about 89–93% of the failing coarse peak is explained
by the stalled terminal-voltage history. Changing the thermal time constant,
frequency equation, or integration history would target the wrong subsystem.
This evidence does not establish why the coupled voltages differ or prove a
reference precision floor. Upstream machine/network attribution remains open.

Four complete replay profiles pass the new decomposition and current-law checks;
the original coupled temperature gates remain unchanged and failing. No production
code changes follow from this diagnostic. The private
`acmtblu1-thermal-voltage-decomposition-20261104` archive preserves full samples,
source, independent scripts, bounded test log and file hashes. Archive SHA-256:
`b9bc7090f05ea3b1dc60bbb318a224d97e408395e42e7735517b496eb46e15ac`.
