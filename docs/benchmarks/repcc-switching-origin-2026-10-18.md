# REPCC accepted origin and coupled switching comparisons

## Confirmed startup bug and correction

The imported plant previously first sampled its switching measurement after
one complete timestep. The independent traces start the slow/fast threshold
timer at time zero. Comparing eight coupled cases before the correction
finds 38 endpoint device-status mismatches, beginning at 7-14 ms.

The solver adapter now samples REPCC once at the accepted initialized network
endpoint, after dynamic Y and operating-point compensation have been set up.
It does not sample inside a predictor, advance continuous states or modify
integration coordinates. Inactive buses/generators do not dispatch. The same
hook is used when a caller supplies its own hybrid current map, without
replacing that map. Repeated sampling at time zero is idempotent.

Local zero-delay regressions verify an actual origin switch, unchanged
continuous initial states, the exact physical Y change, and compensation
current that does not absorb the switch. Both ordinary and caller-supplied
current paths are tested. A separate inactive-owner initialization check
leaves the physical shunt unchanged. These local zero-delay checks are not
independent zero-delay trajectory certification.

The inactive-owner check also exposed a REGCA1 observation defect: the output
map acquired its ID only during dynamic initialization. The ordinary monitor
therefore failed on an initially inactive device. `getStates` now always
includes the existing extended device ID, without fabricating initialized
controller states or bypassing the monitor. The original failure is retained.

## Independent evidence and observation sides

The eight existing cold-paired native profiles are reused without changes:
positive/negative slow and fast bands, each at 0.5 ms and 0.25 ms, ending at
0.3 s. RAW shunts are reconstructed from their frozen initial status and
nominal-admittance API records; strict standard import attaches the plant.
The Model Library source and hashes are recorded in the
[shunt report](repcc-shunts-2026-10-18.md).

Every raw continuous row is retained: 1,202 coarse or 2,402 fine rows, including
two initialized-history rows and 599/1,199 repeated restart times. An end-run
sample and the next run's network-resolved start at the same time are distinct
observations. The test-only solver observer captures the normal pre-predictor
network endpoint without changing the model or network. No duplicate is
dropped or shifted to a different timestamp.

Each 39-column paired trace contains three bus voltages, physical converter
system-base P/Q and all thirteen continuous plant states. A separate
22-column status trace retains all ten device statuses and both time labels.
The initial status API record is negative-time history and is explicitly
compared to the initial physical statuses, not relabeled as a dynamic step.

## Bounded fast-band acceptance

All ten statuses match at every sampled endpoint in all four fast-band
profiles. Every raw continuous row passes a separate profile/channel bound,
approximately ten percent above the measured maximum with numerical floors
for inactive states. Maximum error times are retained in the test output.

| Fast-band profile | Bus 2 V (pu) | P (system pu) | Q (system pu) | Largest plant-state error |
|---|---:|---:|---:|---:|
| Negative, 0.5 ms | 5.114e-6 | 4.937e-6 | 1.195e-6 | 1.212e-4 |
| Negative, 0.25 ms | 5.112e-6 | 2.417e-6 | 1.194e-6 | 6.078e-5 |
| Positive, 0.5 ms | 1.167e-5 | 6.011e-6 | 8.126e-5 | 2.917e-4 |
| Positive, 0.25 ms | 5.793e-6 | 3.161e-6 | 4.016e-5 | 1.488e-4 |

Leading state discrepancies approximately halve with timestep. Some voltage,
initialization and other channels retain a numerical floor; complete precision
or integration parity is not established. No gains, curves, physical limits,
native inputs or existing comparison bounds were fitted.

## Slow-band clock discrepancy: retained, not accepted

The origin correction does not make the four slow-band trajectories agree.
Their device-status mismatches total 22 (one per coarse profile and ten per
fine profile), compared with 18 before the correction. The former origin
error partly masked the clock-quantization difference at the finer step.
Large instantaneous voltage differences at asynchronous switching samples
must not be treated as accepted small continuous-state errors.

A clock-only discriminator replays the identical recorded reactive-power
input through the unchanged shunt equations twice. The native timestamp
sequence reproduces every native status; the actual solver clock reproduces
every coupled InterPSS status. Thus changing only the clock accounts for the
observed discrete mismatch in these four profiles. This does not establish
that every remaining continuous discrepancy has the same cause.

An independent audit of every status API timestamp proves exact equality to
repeated binary32 accumulation of the declared step. The solver uses its
ordinary accumulated double clock. Delay and breaker comparisons can therefore
cross on different steps near a threshold. Production time and delay equations
are not quantized, offset or patched to imitate an export. The permanent
diagnostic retains this counterexample rather than loosening status equality
or granting slow-band trajectory acceptance.

## Remaining work

- Close slow-band clock/integration acceptance with a declared source-backed
  realization policy; do not hide it with timestamp shifts.
- Independent zero-delay, severe-fault and lifecycle profiles, longer horizons,
  remaining selector/initialization/limit and host/alias coverage.
- Independent activating PowerWorld switching and discharge-lockout evidence;
  the prior native premature-reclosure discrepancy remains unchanged.
- Generic non-adapter solver initialization and persistent nominal-G
  serialization if these workflows must support automatic switching.
- HPLNTD and the other outstanding coverage-plan requirements.

This is bounded fast-band evidence, not full REPCC verification. Runtime
family presence stays 26/27. No full Texas2k dynamics are run.

## Validation and preserved evidence

On plugin baseline `674583ce0`, 54 focused tests, 347 plugin-core tests
(six optional skips), and 1,417 selected SMIB/model/solver tests (one existing
diagnostic skip) finish with zero failures/errors. The original all-profile
failure, post-origin slow-band counterexample and initially inactive output-ID
failure remain preserved as separate diagnostic runs, not relabeled as passes.
The Java snapshot was refreshed after the concurrent recording-helper commit;
its two small-network tests are included without running full-system dynamics.

The private archive `2026-10-18-repcc-switching-origin` retains tested sources,
logs, reports, all sixteen paired/status traces and the clock audit. Each
reused reference archive and every new archived file is hash checked.
Generated evidence documentation is compared to the compiled inventory.
All report classpaths use the isolated companion core `38e269ecf`, SHA-256
`fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`;
the shared dependency installation is not replaced.
