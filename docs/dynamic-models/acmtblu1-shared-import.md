# Standalone compressor import and shared execution

Status: exact bus/load import, independent preparation and zero-frequency-
sensitivity coupled controls pass under modified Euler and staggered integration.
Complete frequency, protection and restoration qualification remains open;
`ACMTBLU1` is cataloged as `LOADABLE`.

## Import and ownership

The importer requires the seven allocation fields `12 1 0 35 9 29 16`, followed
by exactly 35 constants. It binds every constant, including the previously
omitted frequency-measurement time constant `Tf`. Wrong allocation fields,
missing/extra constants, nonfinite values and missing owners are rejected before
attachment. Subsystem variants are not inferred as aliases.

`CompLF` controls motor rating and preparation. It does not select a fraction of
the source load. The compressor now owns the entire selected active load record,
using its solved voltage-dependent P/Q at initialization. A wildcard creates one
owner per matching active load; inactive records are excluded. Duplicate owners
and overlapping standalone/composite ownership are rejected in either source
order. Other load records remain represented separately.

The shared `LD1PACImpl` retains the source-load binding only for initialization.
It prepares nominal-voltage characteristics and physical reactive compensation
using the already qualified legacy compressor policy. No separate state vector,
expression interpreter or numerical implementation is introduced. Typed clients
which do not bind a source load retain their existing percentage-based allocation.

Independent lighter-loading and above-limit controls check rating, thresholds
and prepared polynomial coefficients. An entered loading factor above one is
prepared as one, matching the reference correction; zero also selects the
documented default of one. The original source records and entered/prepared
constants remain in the private captures.

## Accepted timer arithmetic

Native imports select `AcceptedTimerPrecision.BINARY32` before initialization.
Typed clients default to `DOUBLE`. Changing precision after initialization is
rejected. The option affects committed UV, stall and restart counters only;
continuous states, integration intervals and network clocks remain unchanged.
Trial currents and derivative evaluations cannot advance the counters.

The distinction matters at strict timer comparisons. With a 0.5 ms interval,
binary32 accumulation leaves the 20 ms restart counter just below its threshold
after forty increments. At 0.25 ms, the corresponding crossing also follows the
recorded binary32 sequence. The coupled controls check every run/stall flag at
the same grid point under both methods. No delay parameter or timestamp is fitted.

## Observe the declared output boundary

The independent source reports a newly committed run flag alongside the voltage
from the preceding endpoint solve. The next network solve responds to that flag.
For example, in the synthetic restart control, the new flag is already reported
at 120.5 ms while the voltage response appears in the following sample.

The comparison records the accepted endpoint after history commit and before
the next network solve, plus each event-side solve at its own grid time. It also
retains a second complete recording after network repolishing. Both recordings
use their actual timestamps and all required samples; there is no sample shift,
offset correction or action-edge exclusion. Flat controls preserve the two
pause/resume duplicates, and fault controls preserve all four event duplicates.
Negative-time startup history remains in the immutable reference archives.

Across the coupled fault controls, observed worst errors are approximately:

| Quantity | Error | Regression limit |
|---|---:|---:|
| Bus-voltage range-normalized RMSE | 4.42e-5 | 6e-5 |
| Generator P range-normalized RMSE | 4.30e-4 | 6e-4 |
| Generator Q range-normalized RMSE | 5.40e-5 | 8e-5 |
| Rotor-angle RMSE | 0.00364 degrees | 0.005 degrees |

Flat controls use absolute error rather than normalizing by numerical noise in
an essentially constant reference. Both complete observation streams and every
published state/diagnostic remain available in the private evidence chain.

## Remaining qualification

The frequency-sensitivity coefficients are disabled in these isolating controls.
The published frequency coordinate and the upstream phase/frequency measurement
path still need independent qualification with nonzero sensitivity. Filtered
voltage and temperature trajectories are retained as diagnostics; successful
boundary gates do not establish equality of every continuous state. Active UV,
contactor/thermal actions, restoration, production intervals and long runs also
remain open. No complete-run performance claim is made from this import repair.

The severe composite discrepancy is separate. Cold controls with all motors
disabled reproduce a low-voltage static-admittance latch; removing frequency
sensitivity does not remove that discrepancy. Some internal diagnostics do not
describe the same endpoint as the externally solved network. The retained
controls include an independent external network-power balance audit. They are
diagnostic evidence, not grounds for copying an unexplained latch or claiming
that the complete composite model is verified.
