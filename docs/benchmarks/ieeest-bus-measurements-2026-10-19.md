# IEEEST local and remote bus measurements

## Implemented scope

Mode 2 samples the selected bus frequency deviation; mode 5 samples the actual
selected bus voltage. BUSR=0 selects the terminal bus. Positive BUSR selects an
existing active dynamic bus. Rotor-speed and power modes reject nonzero BUSR;
missing, inactive and negative measurement buses cannot silently fall back.
Mode 6 remains explicitly unsupported, rather than aliasing another signal.

The pinned native selector table is the schema authority: two integer selectors,
seventeen constants and seven allocated states. The consulted source hash is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld IEEEST reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20IEEEST.htm)
is independent diagram context; its additional delay field is not added to the
native reader.

An algebraic CML measurement block samples the bus at each predictor/corrector
stage without adding a dynamic state. Existing five named block keys and the
shared integrator remain unchanged. Absolute engine bus frequency is converted
to the deviation coordinate; the T5=0 DC restoration includes the physical
frequency baseline. Voltage input is independent of IEEEVC exciter compensation.
Voltage cutout still senses the local terminal bus, even with remote input.

## Independent evidence

Sixteen profiles cover frequency/voltage, local/remote, flat/three-cycle fault,
and 0.5/0.25 ms steps. Thirty-two cold runs converge; each pair is byte-exact.
All entered constants/selectors are read back before and after simulation.
Every CSV retains eighteen allocated state channels, both bus phasors, both bus
frequencies and machine/exciter/stabilizer boundaries: 37 columns and 1,005 or
2,005 rows. Negative history and both duplicate event rows are retained.
Unused voltage-derivative VAR memory is not exported as a physical signal.

`tools/ieeest_measurement_probe.py` creates the references;
`tools/check_ieeest_measurement_artifacts.py` checks allocation, file hashes and
cold-repeat equality. Lossless binary32 fixtures reconstruct the original CSV
bytes and verify their stored SHA-256. The optional frequency channels do not
change legacy runner behavior: two control runs match the old round-rotor fault
CSV exactly.

Every raw sample is replayed through the selected input at both integration
stages. Maximum Vstab playback errors and committed bounds are:

| Profile | Coarse maximum / bound | Fine maximum / bound |
|---|---:|---:|
| Frequency local fault | 9.19033e-6 / 1.02e-5 | 2.31468e-6 / 2.6e-6 |
| Frequency remote fault | 1.75935e-6 / 1.95e-6 | 4.43447e-7 / 4.95e-7 |
| Voltage local fault | 2.27241e-3 / 2.5e-3 | 8.96908e-4 / 1e-3 |
| Voltage remote fault | 1.14665e-3 / 1.27e-3 | 4.84670e-4 / 5.4e-4 |

Frequency flat bounds are local 2.2e-11/1.1e-11 and remote 3.5e-10/1.8e-10.
Voltage flat retains a native 5.34058e-6 initialization residual under a 6e-6
bound; no reference adjustment removes it. Fault refinement is required below
0.30 of the coarse error for frequency and below 0.45 for voltage. Input
selection itself is checked at 1e-12 on every raw row, independently of output
bounds. Zero-duration rows cannot advance dynamic storage.

## Tests and remaining work

The final focused gate passes 118 tests with no failures/errors. The complete
plugin-core gate passes 415 tests with six existing skips and no failures/errors.
All 57 offline checks pass. Source reconciliation covers 1,869 Java/POM files
and 56 lossless fixture parts; the three older pinned core jars are unchanged.
The full scoped SMIB/model/solver gate passes 1,809 tests with one existing skip
and no failures/errors. The private seal preserves all raw runs, reports and
tested source/dependency identities.

- [x] Failing-before strict import regression and selected-bus stage equations.
- [x] Invalid selector/remote-bus combinations and unresolved buses reject.
- [x] Compensation discriminator, pure observer reads, inactive runtime bus,
  reference refresh and local-cutout/remote-input separation.
- [x] Sixteen raw-exact cold pairs, lossless fixtures and bounded all-row playback.
- [x] Sixteen coupled flat/fault SMIB runs retain voltage, bus-frequency,
  speed and stabilizer diagnostic histories.
- [ ] Complete seven-state coordinate and network-coupled trajectory parity;
  coupled finiteness is not an independent trajectory acceptance gate.
- [x] Mode 6 sampled memory and settled-event callbacks are implemented in the
  [derivative follow-up](ieeest-voltage-derivative-2026-10-19.md); its fault-output
  trajectory acceptance remains open.
- [ ] Broader machine-base, denominator, resistance and lifecycle coverage.
- [ ] Complete model acceptance under the development plan; this slice does
  not declare the entire model VERIFIED.

No full Texas2k dynamic simulations are run. Exact executable/library provenance,
raw runs, live state readbacks, failed-before/test-setup counterexamples, tested
source identities and reports are retained in the private evidence seal for
`2026-10-19-ieeest-bus-measurements`.
