# OEL3C equation foundation and excitation-limiter source audit

## Scope and status

The user-supplied May 2026 source list has nine approved excitation-limiter
rows on page 2. The previous source catalog stopped before those rows, even
though three limiter families already had runtime paths. The new immutable
`limiters()` catalog and [generated coverage table](../excitation-limiter-coverage.md)
make all nine rows visible, including six without runtime paths. Eight rows
have concrete native mappings; OEL1 is explicitly unconverted. OEL5C maps to
OEL5CU1, not an invented OEL5C native alias. UEL2 is not UEL2C.

This slice implements the OEL3C data and equation kernel in plugin.core and
registers its tests. It does **not** add a DYR descriptor, network device or
exciter attachment. The loadable limiter count remains three. This is a
normal implementation prerequisite, not completed OEL3C support or completion
of the broader generator/controller plan. No full Texas2k dynamics are run.

## Source contract

The approved-list identity is
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.
The page-2 limiter rows were visually checked against the rendered PDF,
including the separate source, native and PowerWorld columns.

The installed model specification, printed pages 158-159, defines two selectors,
ten constants, two states and four diagnostic variables. Its PDF SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld OEL3C diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_PDF/Generator/Others/Over%20Excitation%20Limiter%20OEL3C.pdf)
was independently downloaded, hashed and visually reviewed; SHA-256 is
`9d1140631ffa8ef156acf3c0097493776fb6a1aa10f8d461552b096ab5120f62`.
Both diagrams agree on the nonlinear error, independent integral limiter and
strict negative-integral switch. Public source lookup and rendering do not
constitute a PowerWorld simulator trajectory.

For a selected raw physical input u, the positive-filter equations are

```
dx0/dt = (Kscale*u - x0)/TF
reference = ITFpu^K1
error = reference - x0^K1
gainedError = Koel*error
dx1/dt = nonwindup(gainedError/Toel, x1, integralMin, integralMax)
proportional = x1 < 0 ? Kpoel*gainedError : 0
VOEL = clamp(x1 + proportional, outputMin, outputMax)
```

At TF=0, effective sensed input is exactly Kscale*u with no hidden epsilon
filter. Unused lag storage remains frozen; it must not be confused with the
native algebraic first observation coordinate. The strict switch uses zero
branch A at zero, including signed zero, and branch B only for a negative
integral. Output clipping does not stop an interior integral. Only its own
integral bounds impose outward freezing; inward release remains permitted.

Entered data remain immutable. Effective inverted bound pairs are swapped as
explicitly documented by the native source. No PowerWorld-specific automatic
gain/time corrections or sign repairs are silently imported. The native input
flag mapping is 1=field voltage, 2=generator field current, 3=exciter field
current; other integers fall back to field voltage. These flags are not the
same enum as PowerWorld's input selector. The kernel receives an explicitly
selected input; host measurement routing still needs implementation and proof.

The kernel uses ordinary modified-Euler stages with atomic failure, matching
predictor/corrector step sizes, no duplicate predictor advance, deterministic
immutable named-state observations and finite-intermediate checks before
clipping. Its zero-time observation cannot clear pending integration history.
The initialized integral is zero projected into the effective declared bounds;
this mathematical initializer does not claim native active-at-start or
takeover-limit expansion behavior.

## Independent native evidence

The published example parameter set first passed a separate allocation/flat
pilot. Final probes retain the existing core-compatible GENROU network,
AC1C excitation with a summation-point OEL connection, and a three-cycle Bus 1
three-phase fault with j0.2-pu shunt impedance. Four profiles run at 0.5 ms and
0.25 ms, twice cold: flat, active linear characteristic, active quadratic
characteristic, and active zero-filter characteristic. Active profiles declare
ITFpu=0.4 before comparison; the example flat retains 4.47. Gains, bounds and
other source constants are unchanged between profiles. These are controlled
branch discriminators, not parameter fitting to an error trace.

All sixteen final runs pass allocation, initialization and the permanent Java
log/CSV quality audit. Each cold pair repeats complete CSV, raw/dynamic input,
probe/helper and captured STATE/VAR bytes exactly. Every retained file is
hash-checked independently, including logs and binary outputs. Entered and
actual constants/selectors retain the exact two/ten/two/four contract and
float32 readbacks; the standard OEL3C record is not an inferred wrapper.
Exciter parameter round-trip coverage is not claimed by this OEL-only
allocation probe; the selected field-voltage boundary is directly recorded.

The eight pairs retain 24,080 raw rows, including all initialization-history
and repeated boundary samples. Both integral and proportional paths activate
in the three fault profiles. Coarse integral minima are approximately
-7.69842e-5, -6.16741e-5 and -1.11637e-4 pu for linear, quadratic and zero-filter
profiles. These mild native profiles do not activate the -1 integral bound;
hard saturation and release are independently tested blockwise, not claimed
as native active-bound trajectory evidence.

`tools/check_oel3c_artifacts.py` records each same-row algebraic residual and
its raw timestamp without fitting a bound. A float32 arithmetic reconstruction
is not treated as universally exact: the fine quadratic trace has a same-row
rounding counterexample. The decimal-data/float32 differences remain visible
rather than changing canonical parameters to native-rounded values.

## Kernel verification and complete-row replay

Twenty-two registered kernel tests cover linear/quadratic errors, independent
endpoint inputs, both full-vector stages, strict switch behavior, integral
saturation/outward freeze/inward release, independent output saturation,
algebraic zero filtering, immutable ordered observations, zero-time behavior,
reset and invalid-stage handling, exact input-flag fallback, inverted bounds,
nonfinite data and overflowing intermediates. Four additional catalog tests
guard all nine rows, exact mapping and absent-runtime status.

Eight recorded-field-voltage replays use the original declared constants and
fixed entered step. They do not inject native states or tune initialization,
interpolate clocks, drop rows or optimize the input. Each output retains every
raw row with an explicit history, zero-origin, event-refresh or integrated
endpoint label. At TF=0 the comparison uses the effective algebraic signal,
not frozen unused storage.

The largest sensed-input residual is 2.56503e-7 pu and the largest integral
residual is 4.87621e-9 pu across these diagnostic replays. Sensor residuals do
not uniformly improve under step halving. No replay bound is fitted from these
maxima, and none of these exports declares complete trajectory acceptance.
The raw observations and exact input streams are retained for the runtime
adapter's next phase.

## Final validation and reproducibility

Final report-based gates are 40 focused tests, 431 plugin-core tests and 2,004
scoped SMIB/model/solver tests, with zero failures/errors. The focused gate has
no skips; six existing optional core checks and one opt-in SMIB diagnostic
remain skipped. All 151 offline checks pass. Counts are overlapping gates,
not a distinct-test total or a claim that all approved models are verified.

The source audit reconciles 1,895 Java/POM files and 139 unchanged earlier
fixture parts. New kernel/data and source-catalog classes are byte-identical
across the three final builds. The existing runtime descriptor catalog and
REGFMA1 model classes remain unchanged from the preceding tested slice.
The generated limiter table matches output from the tested compiled renderer.
The isolated build retains core `1.4.3-salient-refresh-20261019`, jar SHA-256
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`,
offline dependencies and reactor `-am` for integration gates. Run IDs are
`oel3c-foundation-focused-2`, `oel3c-foundation-units-1` and
`oel3c-foundation-smib-1`.

Exact sources, source PDFs/renderings, the separate pilot, sixteen final native
runs, eight replays, quality audits, parameter hashes, reports and dependency
identity are retained in the ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-oel3c-foundation`.
Its SHA-256 inventory links and verifies the preceding 1,213-file evidence seal
without replacing it. No original reference, acceptance bound, shared integrator,
protected checkout/cache or unrelated user file is changed or removed.

## Remaining implementation and acceptance

- Attach a real auxiliary dynamic device without replacing the core GENROU
  machine or its exciter. Add exact standard DYR parsing, deferred attachment,
  duplicate/removal accounting, strict rejection and catalog discovery.
- Prove all physical input sources separately, especially exciter field current.
  Do not map an unavailable signal to generator field voltage silently.
- Establish explicit summation/takeover compatibility and native initialization,
  including required limiter expansion, active startup and zero-filter output
  coordinates. Existing target interfaces alone do not prove compatible routing.
- Add imported flat, three-cycle fault, status/trip/reinitialize/reconnect and
  alternate-base/host tests; compare raw STATE/VAR/output and nearby voltages at
  both steps and every event side.
- Generate repeatable PowerWorld trajectories with exact data corrections
  audited separately. The diagram review is not a simulator benchmark artifact.
- Implement OEL4C, exact OEL5CU1, BASOEL2 and UEL2 paths; handle unconverted OEL1
  as its own typed/tool-specific model rather than inventing native input.
- Continue auditing the other non-renewable source sections. This nine-row
  correction does not certify all rows in the five-page list.
