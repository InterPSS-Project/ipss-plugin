# Converter source-impedance contracts

The later [REGCC implementation](regcc-model-2026-10-28.md) adds the separate
PLL converter with bounded evidence and explicit remaining accuracy diagnostics.
The REGC_C status below describes this earlier checkpoint.

Converter models retain separate current-source and voltage-source interfaces.
The generator-record source impedance is preserved. An explicitly supplied
effective reactance is not inferred from that record or silently discarded.

| Model | Implementation | Compensation-removal qualification |
| --- | --- | --- |
| REGCA1 | Controlled-current interface using the existing REGC_A kernels | Existing guarded capability retained |
| REGCAU2 | Distinct exact wrapper and model identity; shares control kernels while retaining explicit `Xe` | Disabled, including the zero-`Xe` variant |
| REGCB1 | Existing voltage-behind-impedance model and current-limit logic | No compensation-removal port |
| REGC_C | No runtime is added by this change | Unsupported; never aliased to REGCA1 |

The [EPRI generic renewable-model guide](https://restservice.epri.com/publicdownload/000000003002027129/0/Product),
sections 3.1 and 4.1, distinguishes the effective-reactance extension from the
ordinary current-source model and from voltage-source converter interfaces.
The installed reference model contract and block diagrams were also inspected;
exact document and executable provenance remains in the private evidence.

## Import and runtime

`REGCAU2` accepts the exact wrapper:

```text
IBUS 'USRMDL' ID 'REGCAU2' 101 1 1 15 3 4 LVPLSW <14 control constants> Xe /
```

The catalog counts 22 fields after the model name: six allocation fields,
one selector and fifteen constants. Wrong allocations and flat extensions of
REGCA1 are rejected. REGCAU2 is not a REGCA1 alias. Converter hosts are attached
before electrical controllers, and an absent electrical-control record gets
an initialized fixed-command provider.

The typed builder entrypoint is `DStabNetworkBuilder.addRegcau2`, with
`Regcau2Data(Regca1Data controls, double xe)`. The existing `Regca1Model` shares
the numerical block kernels but stores an explicit model identity and reactance.
Existing REGCA1 constructors, state names and numerical operations are retained.

For nonzero effective reactance, the reactive regulator stores voltage:

\[
E_{q0}=|V_0|+X_e I_{q0},\qquad
I_q=\frac{E_q-|V|}{X_e}.
\]

An electrical controller supplies the voltage command through the explicit bridge
`getReactiveVoltageCommand(V, Xe, capturedIqCommand)`. Its ordinary conversion is
`V - Xe * capturedIqCommand`. This uses the captured current command without
another provider read. An initialized fixed-command provider instead retains
its initial voltage reference, matching the independent no-electrical-controller
discriminator. Holding current in that case would remove the observed immediate
fault-current response.

`Xe=0` uses the existing current-coordinate equations. Nonzero `Xe` currently
requires ordinary modified Euler; the unqualified converter two-step path is
rejected. Diagnostics expose `Reactive Voltage Regulator` for the nonzero-`Xe`
coordinate. Existing current-state diagnostic names remain unchanged for REGCA1.
Recording keys are prepared once, avoiding new per-sample string construction.
The current-coordinate local Q/V eigenanalysis rejects nonzero REGCAU2 reactance
instead of interpreting its voltage coordinate as a current state.

The original source impedance remains the Norton interface stamp. The returned
source includes its matching interface current, while the physical reactive
current still depends on `Xe` through the equation above. Neither the imported
source impedance nor the effective-reactance contribution is overwritten.
REGCB1 retains its existing internal-voltage states and physical source impedance.

## Independent checks

The reference matrix varies RAW source reactance and explicit `Xe` independently.
It contains eight controlled-source configurations and four fixed-command
configurations at two time steps, each regenerated cold twice. An additional
active reactive-recovery-rate discriminator is retained separately.

- REGCA1 source reactance is varied between 0.1 and 1.0 pu.
- REGCAU2 uses `Xe=0`, 0.2 and 0.8 pu; the 0.2 profile also varies RAW source reactance.
- REGCB1 source reactance is varied between 0.1 and 1.0 pu.
- Fixed-command REGCAU2 profiles use both 0.5 ms and 0.25 ms steps.

Every reference includes startup samples and both fault-event sides. The
zero-`Xe` REGCAU2 reference is byte-identical to the corresponding REGCA1 reference.
The explicit-reactance variants show the expected additional fault response.
Paired outputs must have identical complete CSV hashes, unchanged converter
constant readback, successful initialization and converged network solutions.

`Regcau2ReferenceTest` compares the four fixed-command coupled profiles at every
nonnegative recorded endpoint and event side, and checks both negative-time
initial regulator coordinates. Largest observed errors are:

| Quantity | Maximum absolute difference |
| --- | ---: |
| Terminal voltage | 0.000021413 pu |
| Active power | 0.000005089 pu |
| Reactive power | 0.000107099 pu |
| Active-current regulator | 0.00000000369 pu |
| Reactive-voltage regulator | 0.0000000523 pu |

The declared test limits are 0.001 pu for voltage/P/Q and 0.000001 pu for the
two regulator coordinates. No offsets, resampling, channel reduction or event-side
exclusions are fitted. Source-impedance, malformed-schema, initialization,
command-conversion, named-state and fast-path-exclusion checks are also registered.

This is bounded profile evidence, not complete model verification. Electrical
controller selectors, remaining active-limit combinations, filtered-voltage
history, trip/reconnection and full-system acceptance need further coverage.
No REGC_C implementation or new default optimization policy is claimed.

The additional active-rate discriminator confirms that the reactive recovery
limit applies directly to the stored voltage coordinate. Both limiter directions
are covered; the limit is not rescaled by `Xe`.

## Regression and retained evidence

The final focused validation passes 76 tests with no failures, errors or skips.
The 26 independent reference runs cover 13 configurations regenerated cold twice.
Texas2k Cases 1 and 2 retain byte-identical complete recordings against the frozen
pre-change baseline: 2,405 samples and 6,190 / 4,810 channels, respectively.
These are recording-parity checks, not new warmed timing measurements.

The full plugin reactor ran 4,265 tests with 86 skips and four failures. Plugin
core and three-phase tests passed. The four integration failures are the endpoint
snapshot fault-edge check, the REECC1 coupled native-fault check, Case 3's
three-cycle fault and Case 5's generator trip. Repeating the same affected classes
with the frozen pre-change plugin jar reproduced the same four failing methods.
They remain unresolved; the full reactor is not reported as passing. The later
Q/V-analysis guard and active-rate checks are included in the final focused run.

The [checkpoint](checkpoints/converter-source-impedance-2026-10-25.json) indexes
the retained source patch, final plugin jar, complete reference artifacts, test
reports and recording hashes. Its private manifest SHA-256 is
`c9b6eebe8b83b6bc64c38cb684cecb66f0f737eba9321b8197cd411b31df620e`.
The core source is unchanged by this implementation.
