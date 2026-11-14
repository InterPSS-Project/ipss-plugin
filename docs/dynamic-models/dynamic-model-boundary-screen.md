# Dynamic model boundary screening

The screen mapped all 181 loadable catalog entries to 169 distinct runtime
classes in the plugin and core sources. It flagged eight source patterns
for review. Counts below include catalog entries that share a runtime
class, and a match is a candidate for inspection.

| Source pattern | Entries with a match |
| --- | ---: |
| Raw or ambiguous voltage in sensing expressions after the correction | 6 |
| Manually managed integration stage history | 104 |
| Zero-time or algebraic bypass | 98 |
| Network current or output boundary | 28 |
| Limiter, saturation, or bounded rate | 101 |
| Explicit nonlocal measurement | 22 |
| Named or reference state observation | 119 |
| Unfinished or explicitly unsupported path marker | 9 |

The source scan locates likely instances of earlier defects. It does not
verify each model equation or prove the absence of a defect in an
unmatched class. The six remaining sensing matches were traced to existing
compensated input paths (AC8B, IEEEX1, ST1C, ST5B), an explicit remote
sensor (SVSMO1T2), or a physical terminal measurement (UEL2C).

## Recurring failure modes

- A selector survives import but the runtime uses a different signal.
  WT3E1 and WT4E1 discarded the remote sensor. A related implementation
  pattern bypassed the machine's compensated voltage in manually coded
  exciter equations. The initial reference, transducer derivative, and
  bypass path must sample the same electrical quantity.
- A differential block becomes algebraic for zero time. REGCB needs
  the voltage at the current network iterate. A reported native state
  slot can also follow the algebraic output while the internal numerical
  memory stays dormant, as in ST2CUT and WT4E1.
- A limit is applied at the wrong point in the signal path. AC7B clips
  the amplifier output and inhibits outward PI integration. WT3G2
  applies its reactive ceiling to the published Norton source. Physical
  terminal voltage still scales exciter supply and limits even when the
  regulator measures compensated voltage. Similar-looking limiters require
  an active-boundary discriminator before changing their equations.
- Reinitialization and event boundaries can retain stale numerical state.
  An unmatched corrector must be rejected before any child controller is
  stepped. Related earlier defects involved event timing, network
  initialization and the operating point used to initialize control loops.

## Corrections and verification

AC1C, AC2C, AC3C, AC4C, AC5C, AC6C, AC7C, AC8C (including its
AC8CU1 import mapping), AC9C, AC11C, BBSEX1, ESAC3A, ESST3A,
EXAC4, and EXST3 use the existing compensated machine-voltage
boundary during initialization, transducer integration, and bypass.
The physical supply and ceiling inputs retain their own terminal measurements.
The feedback contract is documented in
[Compensated sensing and physical exciter supply boundaries](compensated-exciter-voltage-boundaries.md).

Independent native 36.7.0 runs with IEEEVC reactance 0 and 0.08 pu on a
synthetic two-bus case confirm the sensing boundary for four profiles.
Replay reconstructs the compensated magnitude from the recorded terminal
voltage, active power, and reactive power. For a positive sensor time, it
applies trapezoidal integration to those input samples. These are
measurement-path errors over 2,005 samples, separate from coupled model
trajectory acceptance.

| Profile | Maximum error using terminal voltage (pu) | Maximum error using compensated voltage (pu) |
| --- | ---: | ---: |
| AC1C | 0.072758 | 0.0000000944 |
| AC8C, five-ICON record | 0.033684 | 0.0000111 |
| BBSEX1 | 0.053548 | 0.0000264 |
| ESST3A | 0.061711 | 0.0000515 |

WT4E1 now reports the algebraic sensor output when TRV is zero. The
native remote-voltage and sensor-state channels were identical at all
2,005 samples. The plugin regression changes the remote voltage with
zero elapsed time and checks that only the reported sensor changes.

WT4G1/WT4E1 and GEWTGCU1/GEWTECU1 now discard pending modified-Euler
history on reinitialization. The converter rejects an unmatched corrector
before calling its attached controllers. Synthetic regressions check
rejection immediately after reinitialization and the next valid stage pair.

The focused run passed 191 of 193 tests. All 18 added tests fail with the
pre-change implementations and pass after the correction. The two
AC5C/AC6C rectifier-limit tests fail on both revisions. In four additional
coupled SMIB checks, WT4G1 passes and three AC1C/AC8C parity checks
reproduce identical baseline errors. The coupled checks use existing
tolerances. The focused checks use the changed Java sources with local
InterPSS dependencies.

At this checkpoint, open verification included GEWTECU1 remote-mode equation boundaries,
other model-specific limiter and bypass combinations, and full-network
parity after these changes. The compensated feedback result does not
qualify the separate native AC8CU1 wrapper or unrelated equations.

## Follow-up screen of loadable and partial models

The source scan was extended to all 184 catalog entries with a runtime,
including the three partial entries. It resolved all entries to 171
distinct runtime classes. These counts describe catalog coverage and
flag source candidates for inspection.

| Source pattern | Catalog entries |
| --- | ---: |
| Raw or ambiguous voltage feedback | 7 |
| Manually managed stage history | 105 |
| Zero-time or algebraic path | 98 |
| Network injection or output boundary | 31 |
| Limit, saturation, or bounded rate | 102 |
| Nonlocal measurement | 23 |
| Named-state observation | 120 |
| Unfinished-path marker | 9 |

GEWTECU1 initialized the WindVAR voltage sensor and low-voltage power
logic (LVPL) from the terminal even when a remote bus was selected. Its
inner converter voltage equation also used that selected remote voltage.
The controller now initializes WindVAR and LVPL from the selected bus
and uses terminal voltage in the inner equation. A missing selector falls
back to the terminal and logs the missing bus once per initialization.
When the LVPL time constant is zero, both the active-current command
and the braking-power input use the selected voltage in the current
stage. The published LVPL block takes the live measurement at zero
time constant.

Three native two-bus probes varied only the voltage selector. With
line-drop compensation zero, the local and missing-bus sensor and LVPL
states initialized at 0.97866738 pu. The remote selection initialized
both at 0.98000002 pu, and the Mvar/Volt integrator initialized at
0.97866738 pu for every selector. The native solver reported suspect
initial conditions on the synthetic network, so these observations
qualify signal routing only. The nonzero line-drop experiment exposed
a separate open discrepancy. The diagram places the drop ahead of the
transducer, and the synthetic remote-selector/nonzero-XC combination
ended in a native access violation. Line-drop equations need an
independent native discriminator before correction.

WT12T1, WT12A1, WT2E1, and GEWTPTU1 now clear the stored predictor state
and derivative on initialization. The previous implementations could
accept a corrector against a snapshot from the previous operating point.
The new tests interrupt a predictor, reinitialize with changed inputs,
verify that a standalone corrector is rejected with no state change,
then complete a fresh step. The shared WT12T1 kernel also supplies the
history to GEWT2MU1.

The same 73 selected tests ran against the baseline and the corrected
classes. The baseline passed 66 and the correction passed 72. The two
new GEWTECU1 regressions and all four interrupted-step regressions
failed on the baseline and passed with the correction. An additional
missing-bus regression passed on both. The sole remaining failure,
GEWT2MU1's generator-speed equation test, differs by 7.1823e-10 pu on
both runs. The shared kernel applies the generator-to-turbine speed
ratio to shaft torque, while the GE-specific test equation omits it.
Native GEWT2MU1 evidence is needed to resolve that equation difference.

The later native GEWT2MU1 probe resolves the remaining test failure.
GEWT2MU1 uses direct shaft torque in the generator equation. Its shared
integrator now selects that equation, and the original GE regression
passes without a tolerance change. See [the shaft model notes](wt12t1-shaft-transfer.md).
