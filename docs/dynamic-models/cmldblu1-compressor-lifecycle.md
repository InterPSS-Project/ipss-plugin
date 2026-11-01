# CMLDBLU1 compressor stall and restart histories

Status: the restartable compressor history has independent recorded-input
qualification under modified Euler and staggered integration. Complete model,
continuous-state and coupled-reference qualification remain open; the catalog
continues to classify the model as `LOADABLE`.

## Shared implementation

`LD1PACImpl` retains its four shared voltage/frequency/temperature coordinates
and reusable numerical buffers. Stall and restart timers are accepted histories
updated by `afterStep`, outside trial derivative evaluation. The restartable
group has a separate `StallTimerB` history and named-state observation. Existing
`StallTimer` and `RestartTimer` keys remain available.

Previously, the single stall timer stopped updating once the non-restartable
group A stalled. Group B could restart, but a later voltage dip could not stall
it again. Its restart timer also remained latched after restarting. The shared
implementation now updates each running group's stall timer independently,
clears inactive timers, and resets the restart timer at its action. Both
integration methods call the same accepted-history implementation.

Fresh initialization resets timer, run, UV, contactor and thermal-trip history.
Invalid accepted intervals fail before any history mutation. Repeated derivative
evaluations leave physical coordinates and protection history unchanged;
accepted time advances once per integration interval.

## Independent experiment and acceptance scope

The synthetic fixture uses a linear background load and an active restartable
compressor fraction. Two faults are applied at 0.05 and 0.20 seconds and cleared
at 0.10 and 0.25 seconds. The complete run lasts half a second at 0.5 ms and
0.25 ms. The earlier nonlinear-background attempt failed network convergence;
its inputs and output remain retained as a rejected attempt.

Cold pairs and channel parts preserve all allocated source states, diagnostics,
startup samples and both sides of every event. The reference's accumulated
binary32 clock is checked against the declared grid. The history replay uses
the recorded compressor observation channels `VAR117` and `VAR118`. Frequency
`VAR118` is already absolute per unit: every captured value matches one plus
the source frequency state, clipped to 0.95–1.05 pu within storage precision.
It is a filtered observation, rather than the upstream phase-derived input.
The replay passes it without another nominal-frequency offset. The distribution
voltage diagnostic `VAR29` remains a separate recorded quantity. Initial
conditions come from the normal fixture initializer.

Additional uncleared cold captures at 0.5 ms reproduce every computed state and
boundary value in this profile. Only inactive diagnostic slots differ in one
capture. Both original uncleared outputs and explicitly initialized outputs
are retained; this observation does not establish storage-clearing equivalence
for arbitrary models or profiles.

The history gate compares every stall/restart action and its value. Action
times may differ by at most one physical integration interval. Timer values
must agree within one interval while the corresponding run states agree;
inactive timers must be zero. Every unmasked pointwise error is also reported,
including reset-edge errors when the two methods place an action on adjacent
grid points. Such an error can reach the full timer threshold; the action-time
gate does not imply strict pointwise timer parity. No channel-specific time
shift or event-side sample removal is used.

The frozen old compressor and the maintained compressor are replayed through
the same harness with identical inputs. The old implementation only performs
the first B stall/restart cycle and leaves A's timer latched. The maintained
implementation performs both B cycles, matching the reference action sequence
under both methods and at both intervals.

## Coupled diagnostics and remaining work

Complete coupled two-fault runs also execute both cycles under both methods.
They retain all compared voltage, generator P/Q/angle/speed and component
channels. Their source parity remains incomplete: the current bus-voltage peak
error is about 0.01038 pu and changes little when the interval is halved. The
compressor's recorded voltage input differs from the distribution diagnostic
during these faults. Input preparation, network coupling and remaining
continuous-state discrepancies require investigation before accepting the
complete trajectory. Replaying published observations into the measurement
filters does not independently qualify those filters. Their unmasked errors
remain diagnostic, with this input-coordinate limitation recorded explicitly.

The current coverage isolates stall/restart histories with inactive UV,
contactor and thermal-trip actions. Extend independent checks to active
protection combinations, bypasses, parameter boundaries, warm restoration,
production intervals and longer runs. The separate induction-motor angle
observations also remain open.

`Cmldblu1CompressorLifecycleTest` maintains the immutable reference checks,
recorded-input history gates, initialization reset checks and complete coupled
diagnostics. The runnable probe and exact raw captures, rejected attempt,
frozen old/new replay executables, inputs and validation provenance remain in
the private verification-artifact chain.

Validation: all eleven lifecycle/coordinate checks pass, alongside the maintained
composite/motor checks in a 160-check focused reactor run. The core module
passes 458 checks with four existing skips; the three-phase motor gates pass
15 checks. The registered suite executes 2,902 checks with the same four
baseline failures, zero errors and 13 skips. These results establish no new
regressions and retain the qualification limits above.

The corrected replay and frozen old/current compressor comparisons are retained
separately from the earlier harness. The frequency-coordinate correction changes
only the test harness and its diagnostics; production model bytecode is unchanged.
