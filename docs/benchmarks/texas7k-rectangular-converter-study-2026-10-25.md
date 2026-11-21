# Texas7k converter current-frame experiment

Replacing repeated trigonometric converter-current orientation with direct
rectangular-voltage normalization does not establish a whole-run speedup.
After six complete warmups, the candidate median is 25.432 seconds versus
25.057 seconds for its control. The ranges overlap. The candidate remains an
experiment and is rejected as the preferred performance path. The 20-second
objective remains open.

| Complete ten-second run, including recording | Three measurements | Median |
| --- | --- | ---: |
| Fingerprint-checked endpoint control | 24.424 / 25.057 / 25.696 s | 25.057 s |
| Direct rectangular converter frame | 26.551 / 25.432 / 23.685 s | 25.432 s |

Both modes run in one JVM on allowed logical processors 2, 4, 6 and 8, with
the same Java solvers, quarter-cycle step, 1e-4 pu network tolerance and
50-iteration maximum. Neither mode enables the rejected closed-case shortcut.
Compilation counters and phase timings are retained. These small samples do
not establish a stable speedup or a regression attributable solely to the
current-frame calculation.

## Numerical qualification

For eligible stored-current converter states, the candidate uses
`cos(theta) = Vreal / |V|` and `sin(theta) = Vimag / |V|` directly. It retains
the existing current equations, limits, integration stages and MVA scaling.
The calculation can differ by rounding; complete trace identity with the
trigonometric implementation is not claimed.

The initial unrestricted variant changes nonfinite classifications at extreme
finite voltages and is rejected. The guarded version retains the original
calculation for algebraic current states, voltage-iteration memory, nonstandard
complex objects, nonfinite currents, and voltage magnitudes outside
`[Double.MIN_NORMAL, 10]`. This guard selects an implementation; it does not
clip voltage or current.

The guarded arithmetic probe compares 603,456 current components over all
quadrants, current limits, signed zeros, subnormals, large values, infinities
and NaNs. The maximum scaled finite difference is 6.1023e-16, below its
2e-15 bound, and exceptional classifications are preserved. The probe also
checks rejection of converter two-step history by the separate closed-case
reuse qualification.

Four existing independent converter checkpoint profiles pass with unchanged
tolerances. Against the tighter internal complete-network control, the guarded
full trajectory has worst fault/postfault voltage NRMSE 0.00069694, peak
voltage difference 0.00160156 pu and worst full-run recorded rotor-angle RMSE
0.00061611 degrees. Every voltage channel passes the agreed period limits.
Complete independent network-reference acceptance remains open.

Every complete trajectory retains 2,403 sample boundaries, all 6,717 bus
voltages and 15 channels from three machines: 16,176,996 float64 measurements.
No channels, samples or event-side duplicates are removed. The guarded result
is reproduced in a fresh JVM, then checked by exact per-mode checksums through
all twelve phases of the successful timing batch.

The first timing attempt mistakenly used the pre-guard trace checksum and
stopped at its checksum gate. That attempt and the rejected unrestricted
variant remain in the evidence; neither supplies accepted performance figures.

## Evidence and preferred baseline

The [checkpoint](checkpoints/texas7k-rectangular-converter-study-2026-10-25.json)
indexes the private manifest, source and compiled-class archives, arithmetic
probe, independent checkpoint results, all-channel errors, complete trajectories,
affinity records, compilation counters and logs. Its manifest hash is
`292e34a3a64f4d338ac1a637cc32a0584cf7300cdac2d3893a7facbd7c6af164`.
Only byte-identical generated traces share storage; supplied references are
preserved.

The preferred experimental runtime remains the fingerprint-checked path from
the [accepted-endpoint study](texas7k-accepted-endpoint-study-2026-10-25.md).
The production traversal fix remains `43b00f109`; the current-frame experiment
does not change production model equations or defaults.
