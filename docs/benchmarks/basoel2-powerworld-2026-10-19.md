# BASOEL2 independent PowerWorld benchmark and integral-drive correction

## Production correction

The independent benchmark exposed a missing gain in the BASOEL2 integral path.
Visual review of the [published diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20BASOEL2.htm)
and the corresponding WECC figure confirms that the Ki/s block is fed from the
Kp output, not directly from the error:

```
proportional = Kp * (sensed - reference)
dIntegral/dt = Ki * proportional, with independent integral limits
```

The shared kernel now follows this connection for both explicit timer policies.
Two new equation tests check both modified-Euler stages and Kp=0 with nonzero
Ki. No gains, initial states, limits, clocks or tolerances are fitted. Native
import remains rejected while its separate timer/state contract is unresolved.

For the coarse Ifd pilot, limiter-output RMSE falls from approximately 0.00206
to 0.00003195 pu. Integral and exciter response improve as well. This is a
specific equation correction, not complete coupled acceptance.

## Repeatable references and full comparisons

Five fault profiles at two timesteps were cold-generated twice: twenty runs,
ten exact whole-CSV pairs, 30,060 raw rows and 31 exported fields. All machine,
exciter and limiter parameters round-trip before and after initialization.
Automatic correction and parameter overrides are off. The solver/load contract
is explicitly RK2 with impedance loads, matched by separate InterPSS exports.
The original constant-power typed tests keep their own declared load contract.

Ten complete original CSVs are embedded as immutable compressed resources;
both cold-source hashes are retained. All 15,030 representative rows, field
metadata and both occurrences of each fault boundary survive decoding. Permanent
artifact checks also verify the published limiter output identity to the
independent 1e-6-pu serialization bound; observed residuals are below 3e-8 pu.
This artifact/equation evidence is separate from network trajectory acceptance.

Every raw reference row is paired by exact time and event side. Thirty physical,
state and timer quantities are scored separately. P/Q use the 100-MVA base,
speeds are deviations from nominal, relative angle is in radians, timer clocks
are seconds on a one-second base and flags are dimensionless. Each channel
retains all-row RMSE, peaks and regular/pre/post-event metrics. No failed channel
is averaged with other channels and no event occurrence is discarded.

## Counterexamples retained, not copied into the model

The Efd-selected reference initializes its sensed state at zero despite a
nonzero physical Efd. A permanent physical-input guard rejects this discrepancy.
InterPSS retains its equilibrium-based filter initialization. The raw failed
reference remains available as a counterexample, not a valid physical oracle.

The reference export also has mixed event-channel phases: its zero-TR sensed
voltage differs from its simultaneously exported bus voltage after switching.
Field-current, lead-lag and rate-feedback differences are retained at those same
boundaries. Thus even cases with all regular-endpoint quantities below the user
RMSE criterion do not receive all-row acceptance.

Efd/Vfe timer transitions differ at a few threshold samples, and the zero-filter
case has further input/output phase discrepancies. These remain failed
comparisons. The results do not justify a fitted timing shift, a changed pickup,
or stale measurement behavior in InterPSS.

## Recorded sensed-boundary conformance

The follow-up replays all ten immutable references through the actual InterPSS
kernel at the boundary after the input filters. It injects only recorded sensed
input, sensed frequency and terminal voltage. The constant voltage reference is
derived from the source AC1C equilibrium equation; no reference/timer/integral
state is supplied to the kernel. Parameters come from the frozen model readback.

This is explicit sub-block isolation: the two input filters are bypassed in the
test adapter because their outputs are the injected boundary. Their dynamics,
physical-input routing and network feedback are excluded, not silently accepted.
The physical runtime initialization remains unchanged, including its rejection
of the reference's Efd initialization counterexample.

Every one of the 15,030 raw representative rows and both fault-side occurrences
is retained. Fourteen downstream quantities pass the user RMSE < 0.001 criterion
in every profile. All six active/above flags match exactly. The worst RMSE is
approximately 3.52e-6 for the zero-filter coarse integral, reducing to 1.76e-6
at the finer step; the largest pointwise residual is approximately 6.94e-6.

This localizes the previously observed coupled timer differences to different
sensed-input trajectories, rather than proving a fault in the tested downstream
timer equations. It does not resolve the installed native tool's distinct timer
contract or grant full coupled trajectory acceptance. No production equation,
model parameter, timestep, raw reference or established bound changes in this
follow-up. The full-reference decoder is shared with the artifact checks.

Recorded-boundary follow-up validation: 212 focused, 431 core and 2,164 scoped
SMIB/model tests, zero failures/errors and seven existing skips, plus 247 offline
checks. All 1,523 production class images, 70 prior SMIB exports and twelve raw
reference resources remain unchanged. Source reconciliation covers 1,912
Java/POM files. The private `2026-10-19-basoel2-sensed-boundary` archive retains
the exact test sources, structured per-channel metrics and linked prior evidence.

## Remaining work

Final regression: 202 focused, 431 core and 2,154 scoped SMIB/model tests,
zero failures/errors and seven existing skips, plus 247 offline checks.
Source reconciliation covers 1,911 Java/POM files. Only the BASOEL2 kernel
production class changes; core-machine and CML dependencies are unchanged.
The ten matched comparison exports and 32 typed exports repeat exactly between
focused and broad gates. Earlier other-model exports and all three preceding
evidence seals remain unchanged. The private `2026-10-19-basoel2-powerworld-v2`
archive retains before/after diagnostics, raw references, reports and hashes.

- [x] Correct and directly test the series Kp/Ki integral-drive connection.
- [x] Cold-repeat exact-model PowerWorld faults with complete parameter readback.
- [x] Preserve complete portable raw references and reject invalid initial inputs.
- [x] Compare all states, physical quantities and timers without dropping events.
- [x] Independently verify the recorded sensed-boundary PI/timer logic on all ten profiles.
- [ ] Reconcile reference observation phases and Efd initialization.
- [ ] Verify the remaining physical-input/filter and export-phase boundaries;
  downstream recorded-input timer agreement is not full routing acceptance.
- [ ] Add independent flat/V/Hz and additional host coverage.
- [ ] Establish RMSE < 0.001 for every declared coupled channel over every row.
- [ ] Resolve native semantics and implement exact automatic import.

The broader model-coverage goal is not complete. No full Texas2k dynamics are run.
