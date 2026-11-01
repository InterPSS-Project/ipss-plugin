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
