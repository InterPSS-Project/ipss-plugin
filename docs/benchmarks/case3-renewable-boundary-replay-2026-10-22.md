# Case 3 renewable boundary and recovery-selector diagnostics

Prescribing the supplied terminal voltage isolates a large initialization sensitivity: measured initial terminal P/Q reduces the worst reactive-power error from 321.080744 Mvar to 0.000101 Mvar across all 290 converters through 0.2 s at the reference 1 ms interval. Substituting only the initial recovery selector reduces that error to 0.009758 Mvar while leaving differential states unchanged. A coupled selector-only quarter-cycle run then completes the 1.2-second fault window but still has large voltage and rotor errors. No production change, full-system acceptance or new warmed timing result is established; the complete eight-second goal remains open.

## Replay construction and controls

All 290 active converter bundles are loaded from the unchanged Case 3 records. Their computed cold P/Q/V reconstruct ordinary initialization; 3,458 differential coordinates match the retained coupled cold snapshot within 4.89e-15. No internal state is injected. The measured-initialization arm instead uses the first supplied terminal P/Q/V observations, explicitly as a conditional diagnostic. Its maximum initial-state difference is 3.91e-5 and it changes 110 recovery-selector signs.

Every model is checked for local electrical/plant sensing and absence of a frequency-control branch; unavailable remote/branch measurements would reject the replay. Terminal frequency remains 1 pu. The replay advances the existing binary32 two-step model, including plant/wind controllers, at 1 ms or two 0.5 ms local updates. Voltage is linearly interpolated at internal half-step boundaries. The supplied 203 rows from -2 ms through 0.2 s are all retained, including cold history; scoring uses nonnegative rows only. The fixed integration interval is 1 ms while output labels retain the supplied binary32 clock accumulation. This is prescribed-input localization, not a quarter-cycle network simulation.

Each run retains 580 P/Q channels plus controller-state observations for every converter and row. The selector-capable disabled control reproduces the full physical/state CSV exactly. In the selector-only arm, the stored initial-Q value used by recovery selection is replaced from the measured initial terminal Q; all 3,458 differential states remain identical to computed initialization. This borrowed selector is not a production rule and is not a corrected independently calculated initial point.

| Initialization / diagnostic | Local updates per 1 ms | Worst P maximum error, MW | Worst Q maximum error, Mvar |
| --- | ---: | ---: | ---: |
| computed | 1 | 1.759961 | 321.080744 |
| computed | 2 | 39.550614 | 336.109940 |
| measured | 1 | 1.756995 | 0.000101 |
| measured | 2 | 39.558966 | 87.391795 |
| selector | 1 | 1.759961 | 0.009758 |

The computed-initialization Q maximum occurs at bus 1033 unit 2, at the last sample. Measured initialization moves the maximum to bus 2087 unit 2; selector-only moves it to bus 1083 unit 1. The one-step active-power maximum remains at bus 1046 unit 1 near 0.166 s. All per-channel errors, peak times, references, outputs and coincident controller values are retained.

Halving the local interval is not an accuracy-preserving substitute for the observed 1 ms recurrence. With measured initialization, two local updates increase the worst Q error to 87.391795 Mvar and the worst P error to 39.558966 MW. This diagnostic changes the local integration/sample boundary and interpolates additional inputs; it does not establish which timing, limit or history detail explains the difference. It specifically rejects assuming that more local substeps automatically improve reference agreement.

## Coupled quarter-cycle discriminator

One separate coupled run changes only the stored recovery selector after ordinary initialization. All 290 converters are covered, 110 signs change, and converter differential states are checked unchanged. The network interval remains 1/240 s, convergence tolerance 1e-5, both ST1 and renewable local counts one, the sparse solver pure Java, and process affinity one logical CPU. Existing two-quarter-cycle startup history is retained; it differs from the reference two-millisecond history. Full recording is retained.

The ordinary one-step coupled control fails at 0.0458333 s. The selector-only diagnostic completes 1.2 s, including both fault-event sides. Its errors remain unacceptable in magnitude; removing the early failure is not an accuracy fix. The following retained four-step control is shown over the same window, not as a matched one-step accuracy control.

| Coupled variant | Max voltage error through 0.1 s, pu | Prefault max voltage error, pu | Worst rotor RMSE through 1.2 s, degrees | Prefault max rotor error, degrees |
| --- | ---: | ---: | ---: | ---: |
| Ordinary initialization, four local renewable updates | 0.028609 | 0.263015 | 18.265288 | 33.709128 |
| Measured selector only, one local renewable update | 0.078795 | 0.372999 | 16.127232 | 30.719670 |

All 5,579 supported physical channels are compared, with duplicate event sides preserved and no angle/time offset fit. Four unrecorded P/Q channels for previously netted units remain listed. Physical errors and full-reference-range NRMSE are diagnostics only. Historical helper pass flags were removed: the [current acceptance contract](texas2k-current-acceptance.json) uses NRMAE for voltage/frequency and is not applied as a full-run gate to these conditional short probes.

## Consequence and evidence

This localizes most of the isolated Q mismatch to initialization/recovery selection, while leaving the coupled quarter-cycle error unresolved. The next work must independently recover the converted-network starting powers and investigate the integration boundary separately. Using measured selector signs or changing physical parameters to force agreement would not satisfy autonomous simulation accuracy. The earlier [power-recovery arithmetic audit](network-reactive-limit-release-2026-10-22.md#conditional-power-recovery-arithmetic-audit) already rejected partial sign-matching arithmetic; this replay does not overturn that result.

Six complete replay outputs include one disabled-control reproduction, plus one coupled short run. All are terminal with affinity mask one verified before and after. The initial compile rejected two optional model types without the common state-access interface; the corrected diagnostic uses an explicit runtime access guard and all active Case 3 components pass. Initial source/error, corrected sources/classes, exact inputs or content-hashed dependencies, commands, raw measurements, all-channel scores and runtime identities are retained. Full coupled binary output remains a content-hashed external dependency. Supplied references and production source are unchanged.
