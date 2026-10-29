# TGOV3D benchmarking regression

The GENROU + TGOV3D SMIB regression now uses the benchmark reference
model. No production governor equation, parameter or initial state was changed.
The previous PowerWorld comparison assumed its reheater state differed by a
constant offset. That assumption is withdrawn: the benchmark reference initializes this
state at 0.5000100136, whereas PowerWorld initializes it at 0.1227318197.
PowerWorld's nonlinear realization is not established as equivalent by this test.

## Conditions and evidence

- Original `SMIB_v33.raw` and `SMIB_v33_genrou_tgov3d.dyr`, 100 MVA, 60 Hz.
- Both simulators: 0.0001 s integration step, 1e-6 network tolerance, 100
  maximum network iterations. InterPSS uses its default modified Euler solver.
- Bus 1 three-phase fault, j0.2 pu, applied at 0.05 s and cleared at 0.10 s;
  simulation ends at 1 s. Both sides of both events are compared.
- Native GENROU electromagnetic states, relative rotor angle/speed, bus 1/2
  voltages, terminal P/Q, and all six governor states are compared.
- The first native governor state is the stored contribution
  `K*(1-T2/T1)*filteredSpeedDeviation`; other governor states compare directly.
  There is no initial-angle or reheater-pressure offset removal.
- The single-precision benchmark reference clock is mapped by integer integration-step count,
  preserving duplicate event rows and original timestamps in `native_time_s`.

Two fresh native processes reproduce the complete CSV byte for byte:
`54f18a75412fb6e15582510d233cf23d7f59724978be99d41a74da4a090fa8b0`.
All 10,003 nonnegative-time samples pass. The public Java reference retains
1,021 checkpoints: every millisecond, both event sides, and every integration
step in the first millisecond after each event. The checkpoint hash is asserted.
Full traces, normalized CSV, manifests, logs and the exact native probe remain
in the private final IEEE39 evidence archive.

| Quantity | Full-resolution maximum error |
|---|---:|
| Bus 1 / bus 2 voltage | 3.72571e-5 / 1.22475e-5 pu |
| Terminal P / Q | 0.00229935 MW / 0.0352065 Mvar |
| Relative angle / speed | 0.000496214 degrees / 2.72975e-7 pu |
| Eqp / PsiDp / PsiQpp / Edp | 1.28163e-5 / 1.12727e-4 / 4.50654e-5 / 7.79096e-6 pu |
| Governor lead-lag contribution | 5.40322e-7 pu |
| Valve / steam bowl | 2.88677e-6 / 4.36648e-6 pu |
| Reheater / crossover | 4.25270e-6 / 1.60529e-5 pu |
| Intercept valve | 0 |

Every acceptance bound was retained or tightened from the earlier regression.
A matched 0.5 ms native run localizes the largest electromagnetic difference to
the first step after a fault; reducing both steps to 0.1 ms reduces that error
approximately fivefold. The intercept valve stays open in this disturbance:
this evidence does not verify the nonlinear fast-valving characteristic.

## Reproduction

Run `probe_tgov3d_native.py OUTPUT_NAME .0001` with the archived benchmarking
Python interpreter. The archived probe is restored under repository `target/`
so its relative fixture lookup remains valid. Regenerate into a new directory.
`embed_tgov3d_native.py` records the explicit timestamp normalization and
checkpoint selection. Neither script nor full external trajectories are added
to public Git, consistent with the repository's existing artifact policy.

The normal registered Java test uses the embedded checkpoints. To compare the
full normalized CSV, set `tgov3d.nativeReference` in the test's JSON configuration
to its path. Its full-reference mode requires exactly 10,003 samples.

| Input | SHA-256 |
|---|---|
| RAW | `986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b` |
| DYR | `51299c569ba2e08947466e9f54b313bd0dec7215317d39ba89cbeb76f70850a2` |
| Benchmark model-library binary | `c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6` |

The archived benchmark model documentation TGOV3D pages 475–476 establish the state
order and parameters. The independent native run resolves the realization
ambiguity that the diagram alone did not resolve. The retained PowerWorld
artifact remains unchanged; its stored numeric values also reproduce exactly
after normalizing only the current exporter's trailing empty CSV field.
