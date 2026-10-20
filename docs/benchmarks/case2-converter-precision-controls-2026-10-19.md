# Case 2 converter precision controls

The remaining converter active-power discrepancies at 3028/1 and 3098/1
persist under both voltage-export resolution controls and uniform parameter
rounding. These diagnostics do not establish a model correction or change any
acceptance limit. They narrow the investigation of the current-limit-sensitive
path identified in the [fleet replay report](fleet-boundary-localization-2026-10-19.md).

## Fixed controls

Both converter bundles retain the computed physical cold point, original
model equations, prescribed supplied voltage/angle histories, 1 ms stepping,
two cold-history rows, and both sides of each event. Each run contains 10,005
rows per device. Filtering the existing 96-device replay to these two units
reproduces every original output byte.

Four predetermined voltage controls use values just inside the lower or upper
binary32 rounding boundary, either continuously or alternating each step.
Every perturbed voltage rounds back to the supplied value. The maximum input
change is 5.84126e-8 pu, and the ordinary cold states remain unchanged.
The same alternating sign is retained on duplicate event rows.

A separate control uniformly rounds real DYR literals to nearest-even binary32
values. Integer and quoted fields, RAW data, terminal inputs, and solver
equations are unchanged. Models derive their own initial states; the largest
initial-state difference is 3.13e-9. No value is selected by comparison error.

| Full-window diagnostic | Unit 3028/1 | Unit 3098/1 |
| --- | ---: | ---: |
| Original peak active-power error (MW) | 7.296660 | 7.145548 |
| Peak-error range across four voltage controls (MW) | 7.182843–7.352612 | 6.765011–7.641405 |
| Largest change from original power across those controls (MW) | 0.209275 | 0.991080 |
| Peak active-power error with rounded parameters (MW) | 7.296082 | 7.145459 |
| Largest power change from parameter rounding (MW) | 0.001019 | 0.004241 |

The voltage controls demonstrate sensitivity to small measurement changes near
current limiting, but none removes the discrepancy. They are four tested
patterns, not an exhaustive uncertainty bound or proof of the precision of
unexported internal voltage values. Parameter rounding has a much smaller
effect in this paired diagnostic and does not explain the observed residual.

## Evidence and scope

The private evidence set `2026-10-19-case2-converter-precision-controls`
retains runnable producers, frozen runtime/input hashes, all seven completed
runs, byte-equivalence controls, per-channel metrics, numerical plot data and
PNG/PDF figures. The neutral-language check passes. Production sources and
original inputs are unchanged.

P/Q are diagnostic quantities here; the required voltage, rotor-angle and
direct bus-frequency checks remain separate. Prescribed-voltage replay is not
an autonomous network run. The six-case, 36-event accuracy goal remains open.
