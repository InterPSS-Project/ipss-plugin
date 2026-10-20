# OEL3C event-side observations and per-channel RMSE diagnostics

## Scope and outcome

This slice adds read-only InterPSS observations at both three-cycle fault
boundaries, field current on exciter base, reference-machine angle and permanent
all-row comparison tooling. It changes no production model or solver equation,
parameter, initial state, timestep or established regression bound. It does not
run full Texas2k dynamics or claim complete trajectory acceptance.

All 22 previous InterPSS exports remain byte-identical. The six impedance-load
profiles additionally retain every regular endpoint and exact pre/post-event
occurrence at 0.05 and 0.1 s. The observer runs after INITIAL network convergence
and before the positive-time predictor, then delegates unchanged to the existing
solver. Tests assert the instantaneous fault/clearing voltage changes and
continuity of rotor speed, limiter integrator and internal exciter voltage.

## Repeatable reference evidence

Three additional fine-step PowerWorld profiles at 0.00025 s each repeat exactly
twice cold. The complete 22-channel CSVs, including metadata and both duplicate
event occurrences, are stored in `oel3c-event-reference` under the integration
module's test resources. The new index links the unchanged coarse-step index by
hash. Combined evidence comprises six cold pairs and 18,036 raw rows. All model
parameters and solver/load options round-trip without correction.

The nominal Efd input still fails the earlier physical-measurement check; its
reference trajectory is not an Efd-selected oracle merely because its readback
matches the documented selector. The Ifd initialization remains ambiguous and
the Vfe initialization consistent, neither proving complete dynamic routing.
See the [measurement guard and exact source contracts](oel3c-powerworld-switching-2026-10-19.md).

## Comparison contract

`tools/compare_oel3c_events.py` pairs all 9,018 representative raw rows at both
timesteps by nominal integration tick and explicit event side. It rejects
missing, duplicate, extra, nonfinite or off-grid observations. A 1e-10 s grid
serialization allowance does not move raw timestamps; both clocks remain in
the paired evidence. There is no interpolation, extrapolation or clock fitting.

Each of eighteen physical/state quantities has independent all-row RMSE, peak
error and peak time, plus separate regular/pre/post statistics. The user's new
criterion is strictly RMSE < 0.001 per channel. P/Q use the common 100 MVA network
base, speeds use deviations from 1 pu, and other signals use their declared pu
bases. Relative angles are in radians, using both recorded machine angles.
Absolute relative angles remain scored. Relative-angle excursions are an
additional declared coordinate, with both initial angles retained, not a fitted
offset that replaces a failed absolute-angle comparison. Four reference-only
status channels remain in the raw evidence and are explicitly unpaired rather
than treated as verified controller states.

## Counterexamples retained, not accepted

Across all six profiles, the largest all-row bus-voltage RMSE is approximately
0.000219 pu; speed RMSE is below 3.33e-6 pu and relative-angle RMSE below 0.000119
radians. These quantities satisfy the new numerical criterion, but several
exciter/limiter quantities do not.

The all-row Efd RMSE at coarse/fine steps is approximately 0.003621/0.003622 pu
for the nominal Efd profile, 0.018441/0.018467 pu for Ifd and
0.002233/0.002234 pu for Vfe. Halving the step does not remove these coupled
differences. The nominal Efd case also retains its independent routing failure.

The [published AC1C diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC1C.htm)
defines the voltage-sensing signal. In these zero-TR, uncompensated probes,
simultaneous sensed and terminal voltage should coincide. At reference post-event
rows they differ by as much as 0.2177572254 pu at both steps, while InterPSS's
simultaneous identity residual is zero. Reference field-current post-event errors
also approach 0.946 pu RMS, whereas most regular field-current errors are much
smaller. The evidence therefore contains mixed network/controller observation
phases, not just a missing InterPSS post-event voltage observation. No reference
value is replaced by a reconstructed value, and no event row is excluded from
RMSE. These observations do not by themselves identify a production equation
defect or resolve the remaining regular-endpoint mismatch.

## Regression and remaining work

The focused gate passes 91 tests, the core gate 431 and the scoped SMIB/model
gate 2,043, with zero failures/errors and seven existing skips. All 197 offline
checks pass. The six enriched exports repeat exactly between independent
focused and broad runs; the previous 22 exports and production class contents
remain unchanged. Expected-rejection tests and diagnostic passes do not accept
the coupled physical discrepancies above.

- [x] Exact InterPSS pre/post observations at both fault events and both steps.
- [x] Relative-angle and physical field-current channels, with declared bases.
- [x] Whole-file fine-step references, cold repeatability and linked coarse evidence.
- [x] Permanent event-aware, per-channel RMSE and peak diagnostics with all rows retained.
- [ ] Resolve reference Efd routing and mixed-phase event-channel semantics.
- [ ] Localize remaining regular-endpoint controller/state discrepancies through
  source-backed input replay before changing any production equation.
- [ ] Complete other approved model/host/profile coverage in the development plan.

Two initial build attempts exhausted the system disk while copying resources;
they did not run tests. Their failure logs are retained; only their reproducible
partial build directories were removed. Completed evidence contents were
hash-checked across lossless filesystem compression. Successful verification
uses separate build and temporary output on another local volume with the same
source snapshot, dependency hashes, profiles and test selection.
