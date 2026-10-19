# WTDTA1 native mechanical convention and partial reference evidence

## Source contract and correction

The native five-value record contains H, DAMP, Htfrac, Freq1 and Dshaft.
Freq1 is in Hz. The source defines four states: shaft twist angle, turbine
speed deviation from one, generator speed deviation from one, and generator
rotor-angle deviation from its initialized datum. Three VARs contain physical
mechanical input power, initial slip and initial internal angle.

The installed model source, section 23.4, PDF pages 895–896/printed 883–884,
was visually reviewed. Its SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Exact executable/release provenance remains in the private evidence archive.
The [PowerWorld WTGT_A source](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTGT_A.htm)
and [ANDES implementation](https://github.com/CURENT/andes/blob/master/andes/models/renewable/wtdta1.py)
are separate conventions, not interchangeable record schemas.

The reader previously selected the older typed angular-frequency realization.
For H=5, Htfrac=0.8 and Freq1=1.5, its torque-derivative stiffness was 3.6
rather than 142.12230337568676. It also damped relative to initialized speed
rather than synchronous speed. At Pe=0.24, initial speed 0.92 and DAMP=0.1,
equilibrium requires physical Pm=0.23264, not 0.24. Both new guards fail before
the reader correction; the original reports are retained.

```text
Ht = H × Htfrac; Hg = H × (1 − Htfrac)
Kt = 2 Ht Hg / H × (2π Freq1)²
Kangle = Kt / (2π nominal_frequency)
shaft_torque = Kangle × shaft_twist_angle
Pm_initial = Pe_initial + DAMP × w_initial × (w_initial − 1)
```

`fromNativeRecord(data, nominalFrequency)` now selects the existing native
mechanical kernel's direct aerodynamic-input branch. Its four mechanical
coordinates are equivalent to this record; the fifth electrical-power filter
and inertia-emulation branch are absent from WTDTA1's state interface.
The builder changes only this model's construction. No shared mechanical
equation changes. Optional observation hooks report actual evaluation states
and rates without changing the shared kernel's numerical path.

The direct typed constructor retains the older angular-frequency/initial-speed
convention. Existing historical ANDES and PowerWorld tests explicitly select
that convention and retain every original parameter and bound. The six-value
PowerWorld constructor is unchanged. Those historical checks are not evidence
for the newly corrected native-record path.

## Reuse discriminator and state observations

Native generator feedback includes the factor wg/wt on shaft plus shaft-damping
torque. A conditional derivative witness distinguishes it from straight torque
feedback using independently exported physical powers and states, not fitted
coefficients. Over the smooth interior of speed and voltage-dip profiles,
ratio-feedback RMS residuals are 1.74e-7 to 1.95e-7, versus 1.06e-5 to 6.21e-5
for the straight-feedback alternative. This smooth-window derivative diagnostic
is separate from trajectory acceptance, which retains all history/event rows.

Four native coordinates are available as pure immutable observations, alongside
the three existing speed/torque aliases. One-mass mode retains unused native
storage but exposes only its two active integration coordinates. Tests cover
nonunit equilibrium, both explicit stages, zero-duration observation, two-step
event rebase, reinitialization, and 50/60 Hz conversion. The initial event-test
failure was a setup error: it requested two-step rebasing while the converter
still selected modified Euler. Selecting two-step integration fixes that test;
no production event logic was changed to make it pass.

## Independent reference matrix

`python tools/wtdta_native_probe.py fault coarse a` generates a new-only native
run with the configured reference runtime. Nine profiles at 0.5/0.25 ms, each
twice cold, cover flat, ordinary fault, nonunit speed curve, nonunit flat,
one-mass fault, one-mass nonunit fault, doubled torsional frequency, 50 Hz
network frequency and zero synchronous damping. All 36 native runs complete
initialization and convergence checks; all 18 raw CSV pairs match exactly.
All five entered constants read back unchanged after initialization.

Each export preserves sixteen channels and every history/event/API row.
Fixed-digest public fixtures retain all CSV rows and exact RAW/DYR inputs.
Fault timing is held at 0.05–0.10 s: three cycles at 60 Hz, **2.5 cycles at
50 Hz**, so the nominal-frequency discriminator changes only that setting.

Mechanical replay uses physical Pm and Pe inputs and physical initial speed,
not recorded controller states. All four states are checked on every raw row
with individual profile/coordinate bounds. Across the eighteen profiles, the
largest observed errors are approximately 1.25e-5 rad shaft angle, 5.07e-7 pu
turbine speed, 9.88e-7 pu generator speed and 1.02e-5 rad rotor angle. A separate
ten-profile reuse replay includes the stronger voltage-dip input witness.
Float and initialization floors need not decrease monotonically with step
halving; bounds preserve these measured floors without quantizing production
states or changing physical inputs.

## Coupled scope and remaining work

All eighteen exact seven-record strict imports run through the full SMIB
solver. Eleven coupled metrics retain every native row: three bus voltages,
terminal P/Q, generator speed, four mechanical states and mechanical input.
Interpolation is an explicit diagnostic alignment, not proof of exact
instantaneous event-side correspondence. Complete fault trajectories remain
**unaccepted**, including about 0.057 pu event-side voltage differences and
the separately established native raw-power versus published filtered-power
torque-curve discrepancy. No event rows are dropped or bounds widened to
convert those diagnostics into acceptance.

The generated inventory moves WTDTA1 from bounded dialect comparison to native
partial evidence: fifteen native-partial, four bounded-ANDES and eight recorded
families; none is marked VERIFIED. Complete coupled parity, further lifecycle
and boundary profiles, and an independent PowerWorld native-convention matrix
remain open. No full Texas2k dynamics are required or run for this slice.

## Validation checklist

Final gates use the isolated snapshot at plugin baseline `fd5ad1e36` and the
pinned core library SHA-256
`50569fa3d1c9972229478b40759b45358c38f615c358dc3be8889e3e8ab8854c`.
The newer core is required by the baseline network-lifecycle callbacks. Its
content-addressed provenance is archived; the previous frozen dependency cache
is unchanged. Failed intermediate compilation/dependency attempts and the
event-test setup failure are retained, not counted as passing validation.

Final results: 61 focused tests, 410 plugin-core tests and 1,649 scoped
SMIB/model/solver tests, with zero failures/errors. Six plugin optional tests
and one existing SMIB recovery diagnostic remain skipped. Four offline probe
guards and six wording-policy tests pass. Source identity includes 1,816
unchanged Java/build files and twelve scoped Java files; no unexpected Java
sources enter the isolated build.

- [x] Preserve two failing-before-fix equation/initialization guards.
- [x] Reuse the mechanical engine only after four-state and derivative witnesses.
- [x] Preserve 36 cold runs, 18 exact pairs and complete reference inputs/rows.
- [x] Separate bounded mechanical replay from unaccepted coupled diagnostics.
- [x] Finish final focused, plugin-core and scoped SMIB regression gates.
- [x] Seal source/reference/report hashes in the ignored private archive and
  include implementation, tests, public fixtures and documentation in one scoped
  commit. The archive contains 1,462 hashed evidence files before this final
  documentation-status refresh; its inventory is reverified for the commit.
- [ ] Close complete-family coupled and independent PowerWorld acceptance.
