# Battery energy and state-of-charge limits

The energy-output state must integrate terminal active power divided by battery
discharge time even after the state-of-charge (SOC) output reaches its limit.
The limit belongs to the derived SOC signal and current-command bounds. Residual
converter current can therefore produce energy-state overshoot while commanded
active current is zero. On reversal, this retained overshoot must be repaid before
SOC leaves its bound.

The implementation previously froze and clipped the energy state at the SOC
bounds. It now retains the unrestricted energy integral and clips only
`initialSoc - energyOutput` when reporting SOC and selecting current bounds.
The other six states, existing integration path and parameter preparation are
unchanged.

The installed model diagram places the limiter after the energy integral. Two
independent cold runs each for charging and discharging reproduce this behavior;
each pair has byte-identical CSV output, unchanged parameter readback, clean
initialization and no network-convergence warning. All rows and startup samples
are retained. Final independent energy states are +0.074067913 and -0.074146397,
while SOC remains at 0.5 and 0.6 respectively and the active-current command is
zero. The old clipped integral could only reach +0.05 and -0.05, causing final
state discrepancies of approximately 0.02407 and 0.02415.

Analytic tests cover both signs, retained overshoot under reversal, current
blocking/release, and reinitialization. Private recorded-input replay tests check
energy and SOC for every sample, including startup, with a 0.00035 pu bound covering
the explicit one-step quadrature/startup discrepancy. These tests qualify the
energy/SOC contract only; they do not establish all-selector trajectory parity
or staggered controller support. The existing full-solver trajectory retains all
15 previous channel ceilings.

Exact source provenance, paired runnable inputs/probes, raw output and hashes
are retained privately at
`verification-artifacts/reecc1-energy-limits-20261104`. The public probe is
`tools/reecc_native_probe.py`; the private replay is an extended test.

Validation: 11 tests passed, with no failures/errors/skips. Recorded-input maximum
energy errors are 0.000300087 (charging) and 0.000300077 (discharging); maximum
SOC errors are 0.000299988 and 0.000300012. Both are dominated by startup.
