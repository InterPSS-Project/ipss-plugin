# Core round-rotor reuse: native saturation and six-state SMIB evidence

## Scope and correction

GENROU and GENROE keep the existing core `RoundRotorMachineImpl` electrical
equations, Norton boundary and integration. No second round-rotor solver is
introduced. The native reader formerly canonicalized both identifiers to
quadratic saturation. A new regression fails before the correction because
both imports initialize with identical rotor angle and field voltage.

The reader now preserves the source identifier: GENROU retains the core
quadratic characteristic; GENROE selects the exponential characteristic
`S(flux) = S1 * flux^(log(S12/S1)/log(1.2))`. The typed GENROU builder's default
is unchanged. The existing machine extension serializes the optional selector
alongside IEEEVC sensing data, including absent compensation and historical
extension maps without the new key.

The native source library, PDF pages 56-57, defines identical fourteen-CON,
six-STATE layouts with no ICON/VAR allocation, and explicitly distinguishes
exponential from quadratic saturation. Source hashes and reviewed rendered
pages are retained privately. PowerWorld independently describes the same
[GENROE distinction](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENROE.htm)
and lists the [GENROU parameters](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENROU.htm).
Its correction rules and stator-speed convention are not silently substituted
for native input behavior.

## Observability and initialization

The core-backed machine exposes a fresh immutable, ordered named-state map:
`E'q`, `E'd`, `Psi'd`, `Psi'q`, `Speed deviation`, `Angle`. These are existing
core storage coordinates; speed is observed minus one and angle is radians.
Reading the map neither advances nor forces any state. Reconstruction tests
cover both saturation laws with and without IEEEVC data.

Twenty profiles cover flat and three-cycle faults, nonzero damping, quadratic
and exponential saturation, nonzero stator resistance and 50/100/200 MVA
machine bases. Ten configurations run at 0.5 and 0.25 ms. Each runs twice from
a cold simulator start: all twenty raw CSV pairs are exact. All fourteen
constants match binary32 readback exactly and remain unchanged through
initialization and the run. Every raw negative-history and event row is kept.

Initial conditions are derived only from the solved network and entered model
data. Separate bounds cover all sixteen state/output/boundary channels. Across
the matrix, initial electrical-state errors are below 2.0e-7 pu, rotor-angle
error below 1.6e-7 rad, field-voltage error below 4.1e-7 pu and reactive-power
error below 1.14e-6 system pu. Native P/Q channels are system-base power;
mechanical power and electrical states use machine base. This distinction is
checked with the nonunit-base profiles rather than an empirical scale factor.

## Coupled comparison and benchmark diagnosis

The public two-bus fixture has a constant-MVA load at the reference bus. The
native probe converts zero percent to current/admittance. The first new
InterPSS diagnostic mistakenly used the default all-loads-in-admittance path.
That mismatch produced nonshrinking state residuals as large as 0.00433.
Those twenty traces and their summary are preserved as counterevidence.

Explicitly selecting constant P and Q outside the all-loads-in-Y path corrects
the benchmark, without changing a generator parameter. A reduced-voltage load
law regression prevents recurrence. All twenty profiles then pass separate
six-state trajectory bounds over every raw native row. Negative history uses
the unforced initial endpoint; nonnegative native times use linear grid
interpolation. No event rows are removed. Bounds are per profile and state,
rounded upward from 1.25 times the measured maximum, with a 1e-10 floor.

The worst six-state error decreases from 6.12e-4 to 3.12e-4 pu/rad under step
halving. The worst speed error decreases from 3.28e-6 to 1.63e-6 pu. The four
electrical-state residuals decrease by approximately one half; angle residuals
near the native serialization/clock floor do not uniformly decrease. All
channel maxima and their times remain in the private diagnostic summary.
Existing ANDES line-trip bounds are unchanged and remain an independent gate.

This is native initialization and bounded six-state SMIB evidence, **not**
complete voltage/power/event-side or lifecycle acceptance and not VERIFIED.
No full Texas2k dynamic simulations are run.

## Tests, reproducibility and TODO

The executable probe is `tools/genrou_native_probe.py`; eight offline tests
guard exact input cardinality, one-parameter discriminators, bases, resistance,
fault selection and binary32 conversion. `GenrouNativeReference` hashes the
complete embedded histories and exact RAW/DYR inputs. `GenrouNativeSmibTest`
is registered in the core suite and discovered by the scoped SMIB suite.

- [x] Prove and fix the exponential alias, retaining core machine reuse.
- [x] Preserve saturation and sensing on state reconstruction.
- [x] Add ordered immutable named states without a new integration realization.
- [x] Cold-repeat all twenty native profiles and retain every raw row.
- [x] Check initialization and six-state coupled trajectories with separate bounds.
- [x] Explain the first fault discrepancy with a concrete load-law discriminator.
- [ ] Record terminal voltage angle/current boundary channels to localize the
  remaining instantaneous event-side differences without inferring phasors.
- [ ] Establish complete voltage, terminal P/Q and electrical torque versus
  power acceptance, including nonzero resistance and both machine-base scales.
- [ ] Add trip/reconnect, checkpoint continuation and further saturation-region
  cases; reconstruction equivalence alone is not lifecycle acceptance.
- [ ] Audit the analogous GENSAE/GENSAL alias using its own source and native
  discriminator; do not assume the round-rotor correction proves that family.
- [ ] Independently execute the corresponding PowerWorld matrix with its
  documented stator convention kept explicit.

Final gates pass 44 focused, 410 core-unit (six existing optional skips)
and 1,731 scoped SMIB/model/solver tests (one existing diagnostic skip), plus
eight offline probe and six wording tests. Terminal results and immutable
source/report hashes are recorded in the private
`2026-10-19-genrou-native-saturation` verification seal. The broader
model-coverage goal remains open.
