# Texas2k no-event smoke contract

The supplied Case 1 operating point is not a stationary dynamic initialization,
as confirmed by the user. No-event motion is expected. The former smoke test
incorrectly required Bus1051 and Bus2056 complex voltages to remain within
0.0002 pu of their initial phasors and machine speeds within 0.00002 pu.
Those equilibrium assertions were not a valid acceptance condition for these
supplied operating points.

The test is now named `allSixCaseStacksInitializeAndRunNoEvent`. It checks
execution health rather than treating the six operating-point cases as
equilibrium fixtures:

- Existing load-flow, generator initialization, model coverage, netting,
  converter power-base and dynamic output-state checks remain active.
- All six cases must initialize and complete the 0.1-second no-event simulation
  with the existing quarter-cycle integration step.
- Every active bus must have a finite final complex voltage.
- The two monitored buses and machines must record multiple finite voltage,
  angle and speed samples.
- Complex-voltage and speed changes remain visible as diagnostic output.

The independent `AllsymEquilibriumTest` retains its stationary-dynamics
requirements on the supplied equilibrium fixture. Reference-trajectory accuracy
criteria and simulation settings were not changed by this test correction.

## Focused verification

The complete six-case smoke class and ALLSYM equilibrium test pass: **four test
methods, zero failures, errors or skips**. Each six-case method executes all six
operating points, including no-event, fault and generator-trip scenarios.

The no-event diagnostic reports these Bus1051 complex-voltage changes from the
pre-simulation operating-point phasor:

| Case | Absolute complex-voltage change, pu |
| --- | ---: |
| 1 | 0.030024 |
| 2 | 0.054578 |
| 3 | 0.076187 |
| 4 | 0.099429 |
| 5 | 0.144312 |
| 6 | 0.069067 |

These values include phase motion; they are not voltage-magnitude errors or
errors against a supplied trajectory. Passing an execution smoke test does not
establish reference accuracy.

Bounded log: `target/texas2k-no-event-contract-20261023.log`. Fresh reports:
`.verification-build/texas2k-no-event-contract-20261023/ipss.test.plugin.core/surefire-reports/`.
The preceding full-validation evidence remains historical. The
[subsequent complete merge gate passes](full-merge-validation-g-2026-10-23.md)
after this contract correction.
