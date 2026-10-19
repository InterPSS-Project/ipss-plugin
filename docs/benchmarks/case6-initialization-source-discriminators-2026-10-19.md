# Case 6 initialization selectors and source impedance

## Scope

This follows the [machine and network boundary localization](case6-machine-boundary-localization-2026-10-19.md).
The coupled Case 6 trajectory remains outside the original acceptance limits.
The experiments below distinguish two initialization inputs while retaining the
original computed operating point, I-P/Z-Q load conversion, cold derivative
history, model constants and supplied reference samples. They are diagnostics;
none changes the production importer or the acceptance limits.

The frozen runtime is the same complete production snapshot used by the
preceding 0.3-second Case 6 control. Concurrent machine-model work is excluded.
The step is the original 0.5 ms. Network tolerance is 1e-4 unless explicitly
identified otherwise. Comparisons use the common prefault observations from
the six original Case 6 events: 30 bus voltages and 23 synchronous units.
Every compared channel retains its own original full-ten-second reference
range. No offset, gain, state or time alignment is fitted.

## Recovery direction at zero declared reactive power

The original RAW records contain 117 active REGCA1 units with Q, Qmin and Qmax
all zero, compared with 17 in Cases 1 and 2. Their computed converted powers
are small nonzero values. In the Case 6 snapshot, 101 select positive recovery
and 16 select negative recovery. All 117 have both reactive recovery rate
bounds set to zero. The sign therefore selects a consequential one-sided
bound; it is not simply an insignificant printed rounding difference.

Three predeclared runs retain the existing signs, select positive for this
entire cohort, or select negative for the entire cohort. The alternatives
change only the recovery-direction selector after normal initialization.
Continuous states and all physical cold observations remain bit-for-bit equal.
The unchanged instrumented run reproduces all 603 rows and 5,268 physical
channels of the previous control exactly.

| Recovery selection | Worst voltage error (pu) | Worst rotor error (degrees) | Worst P error (MW) | Worst Q error (Mvar) |
|---|---:|---:|---:|---:|
| Original computed signs | 0.0669425 | 0.631768 | 89.3161 | 118.234 |
| All positive in the declared zero-Q cohort | 0.0540678 | 0.550109 | 74.0444 | 97.0730 |
| All negative in that cohort | 0.0619635 | 0.270364 | 87.8238 | 111.178 |

Values are maximum absolute errors over 0–0.3 s. Neither alternative resolves
the voltage error. These experiments do not identify the original reference
run's individual recovery directions and do not justify choosing directions
by whichever trajectory looks closer. The full Case 2 reference exposes
positive initial Q for its 17 corresponding units; the Case 6 references do
not expose the required converter Q channels.

## Source impedance of the five grid-forming units

All five original REGFMA1 records, at buses 1016, 1023, 1051, 1066 and 1078,
use machine ID 99 and contain nineteen values after the ID. Their RAW records
specify 100 MVA and source impedance **j1.0 pu**. The legacy import path
replaces this with its compatibility impedance **j0.15 pu**.

The [WECC specification, Table 1](https://www.wecc.org/sites/default/files/documents/products/2024/Model%20Specification%20of%20Droop-Controlled%20Grid-Forming%20Inverters-REGFM_A1.pdf)
lists 0.15 pu as an example reactance, not a mandatory default. Its PDF hash is
`7ab9be47e33ae6d7e8a9e5fc366efc76cac89e77140464bcd9a29f0e0a3b2267`.
The production comment is corrected to describe the compatibility constant
accurately; executable behavior is unchanged.

The installed native model contract uses two flags and eighteen constants,
with source impedance from the generator power-flow record. This is distinct
from the supplied nineteen-value records. The original run's handling of those
records remains unconfirmed. The [earlier schema report](regfma1-native-schema-2026-10-17.md)
already distinguishes these formats. An improved trajectory alone does not
establish the missing format contract.

A separate experiment changes only the five generator source impedances to
their original RAW values, before ordinary model initialization. It preserves
the legacy control constants and selectors. The no-op version again reproduces
every control sample exactly. The RAW-source version changes the largest cold
physical observation by only 1.31e-11 Mvar. All other devices' stored integration
states remain bit-for-bit equal; recomputed observation/rate differences stay
below 5.56e-12 in their reported coordinates.

Each grid-forming unit's internal voltage and angle is independently checked
against `E = V + Z * conjugate(S / V)` on the common 100-MVA base. Both variants
agree within 3.0e-15 pu/radians, and their initial derivatives are below 1e-9.
The changed internal coordinates are the normal consequence of the specified
source impedance, not substituted reference states.

| Source impedance | Worst voltage error (pu) | Worst rotor error (degrees) | Worst P error (MW) | Worst Q error (Mvar) |
|---|---:|---:|---:|---:|
| Legacy j0.15 | 0.0669425 | 0.631768 | 89.3161 | 118.234 |
| Original RAW j1.0 | 0.0104672 | 0.391879 | 11.6403 | 19.2680 |

These are maxima over the same 0–0.3-second samples. The voltage maximum stays
at bus 5083. The rotor maximum moves from bus 5297/1 to bus 5302/1. Voltage
error decreases by approximately 84%, but still fails. Total network iterations
increase from 7,556 to 9,151 across the same 602 calls, so this is not a runtime
optimization result.

## Longer prefault and convergence controls

The RAW-impedance version completes the full prefault second at 1e-4 network
tolerance, recording 2,003 rows and 5,268 physical channels. Its first 603 rows
reproduce the shorter RAW-impedance experiment bit-for-bit. Scoring every
available common prefault channel gives:

| Quantity | Worst acceptance metric | Required limit | Passing channels |
|---|---:|---:|---:|
| Bus voltage NRMAE | 0.0480782 at bus 6141 | 0.001 | 0 / 30 |
| Rotor maximum absolute error | 14.7619 degrees at bus 5300/1 | 0.02 degrees | 0 / 23 |
| Generator-speed NRMAE | 0.0941664 at bus 5300/1 | 0.001 | 0 / 23 |

The worst absolute bus-voltage discrepancy over the full second is 0.0334858
pu; its channel need not be the same as the worst normalized mean-error
channel. Worst active/reactive power errors are 37.2084 MW and 46.7875 Mvar.
The network takes 41,091 iterations across 2,002 calls. These results confirm
that the short-prefix improvement is insufficient for prefault acceptance.

At 0.3 s, terminal phase inferred from the supplied machine states and P/Q
provides another localization observation. Across the 23 units, the original
phase errors span 1.32579–4.17891 degrees. The RAW-source variant spans
0.326109–0.409149 degrees. The reduced spread leaves a common phase error.
No mean phase or other offset is removed from any scored trajectory.

A separate one-second 1e-6 convergence discriminator is not included in this
evidence set; its completion and acceptance are not claimed here.

## Evidence and remaining work

The private evidence preserves exact producers, frozen runtime provenance,
original inputs, cold snapshots, all physical rows, reference ranges,
per-channel errors, iteration counts and source-contract excerpts. The first
comparison harness rejected tiny recomputed observation differences despite
identical stored states. Its corrected version checks stored states exactly
and separately reports the bounded observation differences. Both source
versions are retained. The source-extraction guard was also corrected to use
the model document's original spelling of its source-impedance symbol.
The completed artifacts are sealed in the private evidence set
`2026-10-19-case6-initialization-source-discriminators`.

All six cases and all 36 original disturbances remain required. Complete
coupled trajectory acceptance, direct bus-frequency validation and the
20-second runtime target remain open. Resolve the original REGFMA1 input
contract before selecting a production import change; retain the recovery
selector sensitivity as localization evidence rather than a fitted rule.
