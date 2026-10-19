# Case 6 convergence and recovery-boundary localization

## Scope and unchanged acceptance

This completes the one-second convergence control left open in the
[initialization source report](case6-initialization-source-discriminators-2026-10-19.md).
Both runs use the original 0.5 ms timestep, computed converted operating point,
I-P/Z-Q loads, original controller constants, cold derivative history and the
five grid-forming units' RAW source impedance of j1.0 pu. The original
nineteen-value model-loading contract remains unconfirmed. These are diagnostic
controls, not accepted replacements for the original six-case benchmark.

The only numerical difference between the paired runs is network stopping
tolerance: 1e-4 versus 1e-6. Initialization still uses 1e-8. The runner source
diff contains just two literal substitutions: the reported tolerance and its
dynamic stopping value. Original inputs and converted points are byte-identical;
all parsed cold snapshot values are exactly equal. Different JSON object-key
orders initially failed a byte-comparison guard; the corrected guard ignores
only key ordering. Both guard versions are retained.

Each run completes 2,003 physical rows and 5,268 channels through the full
prefault second. Comparisons use all 30 bus voltages and 23 synchronous units
observed across the original Case 6 events. Every normalized error uses its
own full-ten-second supplied reference range. No offset is removed.

## Tighter convergence does not resolve the reference errors

| Quantity | 1e-4 | 1e-6 |
|---|---:|---:|
| Worst voltage NRMAE | 0.0480782 | 0.0385142 |
| Worst voltage maximum absolute error (pu) | 0.0334858 | 0.0222294 |
| Worst rotor maximum absolute error (degrees) | 14.7619 | 18.2028 |
| Worst generator-speed NRMAE | 0.0941664 | 0.116093 |
| Network iterations across 2,002 calls | 41,091 | 111,227 |
| Diagnostic elapsed time (seconds) | 479.771 | 1,050.386 |

All 30 voltage channels improve in both maximum and mean absolute error.
All 23 rotor-angle and all 23 speed channels worsen in both metrics. Neither
run passes any of these observed prefault channels at the original limits:
0.001 voltage/speed NRMAE and 0.02-degree rotor maximum error. The rotor maximum
is at bus 5300/1 for 1e-4 and bus 5302/1 for 1e-6. Speed is a frequency proxy.
The diagnostic timings are not the requested production performance benchmark.

## Power accounting and an independently reproduced local transition

Power accounting covers every recorded dynamic generator: five grid-forming,
290 grid-following and 113 synchronous units. At 0.75 seconds, tightening the
tolerance changes their aggregate active powers by -26.1131 MW, +896.637 MW
and -1,216.378 MW respectively. Bus 1046/1 alone contributes +716.733 MW to
the grid-following difference. These are differences between local runs, not
reference errors or proof of causal attribution; the supplied observations do
not expose the complete converter fleet's powers.

Three selected converters, bus 1046/1 and bus 1016/1 and /2, are then replayed
on each run's recorded terminal-voltage phasors. They initialize normally from
the unchanged computed P/Q/V; their stored cold coordinates agree within
2.23e-16. No internal states or later P/Q outputs are prescribed. Original
controller parameters, integration precision and cold steps are retained.
The replay checks that plant frequency and remote measurements require no
unrecorded inputs for these devices.

Across both histories, all three units and all 2,003 samples, replayed P/Q
reproduce the coupled local outputs within **3.42e-13 MW/Mvar**. This establishes
that their local power divergence is reproducible from the changed voltage
history through the unchanged controllers. It does not establish the supplied
reference controller's internal branch.

At bus 1046/1, the relevant lower-voltage threshold is **0.85 pu** and the
active-current recovery hold is **0.5 seconds**. The decisive observations are:

| Local run | Recorded transition | Consequence |
|---|---|---|
| 1e-6 | At 0.667 s, the preceding timer reaches zero during the update that publishes recovery | No new hold starts; active current recovers |
| 1e-4 | At 0.6685 s, the preceding timer expires while the voltage excursion remains active | The unit still awaits recovery |
| 1e-4 | At 0.6725 s, recovery is published after that expiry | Another 0.5-second hold starts at the retained zero current limit |

These timestamps describe recorded controller memory; the existing integration
order between voltage measurement and history publication is preserved. The
boundary explains why a small change in voltage history can produce a large
power difference later in the prefault interval. It does not justify adjusting
the threshold, timer or stopping tolerance to fit the reference. The earlier
independent recovery-interval checks remain applicable; this investigation
does not change their production implementation.

## Model-format evidence and remaining work

The installed reference documentation's correction history records an omitted
second model flag in an older exported REGFMA1 listing and a separate reactive
path sign correction. Exact document hashes, versions and excerpts remain in
the private evidence. These facts do not establish the version or export path
used to produce the supplied nineteen-value records. In particular, nineteen
values alone do not prove a valid one-flag schema. The original loading contract
must still be resolved before selecting a production importer change.

The private evidence set
`2026-10-19-case6-convergence-and-recovery-boundary` retains the completed tight
run, both-tolerance comparison, input-isolation checks, release-note audit,
fleet accounting, conditional replay sources, frozen runtime hashes, raw
outputs and inspected plots. Earlier coarse/source evidence is referenced by
content hash. No executable production behavior or acceptance limit changes.

The next localization boundary is the voltage trajectory entering the recovery
threshold, including converter/network coupling and the unresolved original
grid-forming source contract. All six cases, all 36 original disturbances,
direct bus-frequency validation and the 20-second runtime target remain open.
