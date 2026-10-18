# REGFMA1 current base, plant input and Texas2k timestep investigation

## Scope and source contract

This slice fixes two independently demonstrated equation errors. It does not
claim that the Texas2k post-fault overvoltage is fixed or that every REGFMA1
input dialect is supported. Physical parameters and comparison tolerances
were not fitted to a simulator curve.

The tested environment uses plugin 1.4.6 and the locally rebuilt core 1.4.3
from sibling core commit `e886019ce` (shared CML initialization lifecycle fix).
This does not claim that the same core binary has been published remotely.

The [WECC/PNNL September 2023 REGFM_A1 specification](https://www.wecc.org/sites/default/files/documents/products/2024/Model%20Specification%20of%20Droop-Controlled%20Grid-Forming%20Inverters-REGFM_A1.pdf)
(PNNL-32278, Table 1 and droop diagrams) defines the inverter-rating base and
the plant-input junctions. The downloaded PDF SHA-256 is
`7ab9be47e33ae6d7e8a9e5fc366efc76cac89e77140464bcd9a29f0e0a3b2267`.
The [PowerWorld REGFM_A1 reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_A1.htm)
documents the QVflag selection and zero-current-limit bypass. Its explicit
Re/Xe fields belong to that tool's data representation.

An additional native-format gap was found in the installed reference simulator Model
Library, printed pages 807-810 (PDF pages 819-822). That version has two ICONs
(Vflag, QVflag), eighteen CONs, and coupling impedance supplied by the generator
RAW record. Library SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The currently accepted Texas2k exchange has only nineteen values (one flag
plus eighteen constants), with a compatibility Re=0, Xe=0.15 interface. The
source case's separate PowerWorld AUX explicitly uses that interface for all
five GFM units. Do not silently reinterpret those legacy records as the current
twenty-value native schema or replace their coupling with RAW impedance.

## Corrected equations and focused evidence

1. The network current is on system base, whereas Imax is on inverter base.
   The cap is now `Imax * inverterMVA / systemMVA`. Before the fix, a 50-MVA
   inverter on a 100-MVA system delivered 2.5 pu instead of its 1.25-pu cap.
   The new test checks 50, 100 and 200 MVA, actual Norton injection minus the
   shunt current, P/Q magnitude, and repeated output reads without state drift.
2. With QVflag=0 the plant Q offset enters before Mq; with QVflag nonzero the
   voltage offset enters after Mq. The old implementation always added a
   voltage offset. A discriminator with Mq=0.2 and offset=0.04 now gives
   respectively 0.008 and 0.04 pu. An older assertion that encoded the wrong
   junction was corrected without changing its tolerance.

Both new tests were observed failing before their corresponding production
fix. The final focused command passes:

```text
mvn -q -pl ipss.test.plugin.core -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PsseRegfma1ModelTest,Regfma1IndependentTrajectoryTest,Texas2kTopologyFaultMatrixTest,RepositoryTestConfigurationTest test
```

The existing PowerWorld SMIB trajectory comparison is unchanged: maximum
errors include terminal V=0.00192362880 pu, P=0.276393568 MW,
Q=0.846321735 Mvar and internal E=0.0000120864428 pu. Its inactive current
limiter does not verify the newly fixed active-boundary case.

## Independent active-current-limit probes

PowerWorld 24.2026.9.3 was cold-started twice for each of three inverter
ratings, using a 0.5-ms step, 0.05-0.10-s three-phase fault at Bus 3 and
Imax=1.25. The event impedance is j0.01 pu; this is a small discriminator,
not the bolted full-system event. Auto-correction is disabled. Pmax=2 provides
explicit control headroom. The rest of the declared profile is preserved
beside the staged AUX/DYR and executable runner in the private archive.

Each paired export is exactly repeatable after declared newline normalization:
603 samples, fourteen channels and two event-side duplicate timestamps.
The permanent `PowerWorldCsvRepeatability` checker passes all three pairs.
The current computed as `hypot(P_MW,Q_Mvar)/(MVA*Vpu)` during the depressed
voltage region agrees with 1.25 pu to export precision (within 1.6e-7 pu).

| Inverter MVA | Canonical CSV SHA-256, identical for both cold runs |
|---:|---|
| 50 | `429b483b03316314a852a45199e588d8b04a6e45c45f93a8a0351d483b32f615` |
| 100 | `7621f40a9d88531fe84ac3988359e66489a4958d9f4f8855f9c438e17e6e0cd7` |
| 200 | `3f5d4027aa85e97cf691bfb69f6cec658e0bc1864a63c9fac716647d91a26e21` |

Executable SHA-256:
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
Runner SHA-256:
`0d6abbe93113afcc7113957d30278dda69e4392973a5da3aab611f27b4c9f61e`.
These establish independent boundary semantics and cold repeatability, not a
new complete InterPSS-versus-PowerWorld trajectory acceptance at all ratings.

## Permanent timestep diagnostic

`Texas2kTopologyFaultMatrixTest` now accepts class-scoped JSON settings:
`texas2k.fault.matrix.step`, `.end`, `.sites` and `.tag`. Defaults retain the
reviewed fifteen-event, 1/240-s, 0.5-s matrix. For a diagnostic use, for example,
step `0.0020833333333333333`, sites `5:weak-grid,6:gfm-terminal`, and a fresh
tag. Enable `.enabled` explicitly. Inputs remain native RAW/DYR/preparation
IDVs; no DYD is read.

Unknown/duplicate sites, unsafe tags, nonfinite/nonpositive steps and event
times off the timestep grid are rejected. Every changed profile requires a
tag; existing tagged output is protected against replacement. Six unit tests
cover these boundaries. Summaries record step, end and tag; trajectories retain
the existing voltage, generator and controller channels.

The selected events were rerun at h/2 and h/4 with unchanged physical inputs.
Event-side rows are aligned by timestamp and occurrence, not nearest time alone.

| Event | Last exported V at h=1/240 | Last exported V at h/2 | Last exported V at h/4 |
|---|---:|---:|---:|
| Case 5, weak grid, Bus1011 | 1.51931220004 | 1.47707021591 | 1.51114236826 |
| Case 6, GFM terminal, Bus1016 | 1.69941852707 | 1.69929883491 | 1.69574922581 |

Case 6 maximum common-time bus-voltage difference from h is 0.02675135 pu at
h/2 and 0.04768262 pu at h/4, both near 0.3208333 s. Its Bus1051 speed
difference increases from 0.00189754 to 0.00431442 pu. This is not a demonstrated
convergence result and does not rule out integration/coupling errors. Other
controllers can also have timestep-dependent minimum-time corrections.

There is an additional observation-time limitation in the inherited fault
writer. The core emits the newly integrated endpoint with the step's starting
time label, then increments `simuTime`. The writer separately records controller
states after that increment and joins them by the numerical timestamp. Thus
its monitor and custom-state columns are offset by one integration step, and
its last monitor labels are respectively 0.4958333, 0.4979167 and 0.4989583 s,
not 0.5 s. The voltage comparisons above use raw export labels and retain
duplicates; they are diagnostic evidence, not correctly aligned physical-time
cross-solver comparisons. Correct endpoint/event-side recording before using
these files for first-divergence or independent acceptance. Preserve the old
raw files rather than silently rewriting their timestamps.

The GFM internal E remains at or below 1.2 pu even when terminal voltage is
larger; its final Q is negative. Emax caps an internal controller output, not
every network-bus voltage. Clipping terminal voltage would hide the problem.
Neither new boundary fix explains these cases directly: the five GFM units
have 100-MVA ratings on the 100-MVA network and no plant-offset input.

The existing trace also localizes an unresolved vendor difference: Bus1016's
two REGCA1 units have negative initial Q and Iqrmin=0. Their stored reactive
current cannot decrease under the native lower-rate rule, while their REECA1
commands eventually reverse. At the last exported row the two units inject
about 1.48 pu Q each while the GFM unit absorbs about 2.94 pu Q. These are
stored-state observations subject to the timing limitation above, not proof
of an incorrect equation. PowerWorld explicitly disables Iqrmin at zero;
the current native implementation and regression tests keep the zero bound
active. Check an exact native negative-Q fault discriminator before changing
this rule or comparing unchanged numeric inputs as equivalent vendor profiles.
The [PowerWorld REGC_A documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
identifies this zero-limit behavior explicitly.

A new three-bus reference simulator discriminator was then cold-run twice. It uses the
existing public Bus1062 control DYR, a 0.98-pu plant setpoint, two 0.1-pu
reactance branches, and a Bus1 j0.01-pu three-cycle fault. Initial generation is
24 MW/-10.97093868 Mvar and initialization reports OK. At a 0.5-ms step both
exports contain 1,005 samples with identical raw CSV SHA-256
`9384646e27c3cbfc466c8683de61ef5a4ba12cbe468ad4963bf7e1ba6307699f`.
The final native REGCA Iq state is 0.94644308 pu (initial -0.11194882 pu),
and terminal voltage is 1.20232344 pu. The state maximum is 0.94684905 pu,
so this is not evidence of strict mathematical monotonicity at every native
integration sample. It does demonstrate a repeatable high-current post-fault
response with the zero-rate input in native reference simulator, not only InterPSS.
Matched InterPSS execution of this new profile remains necessary.

The isolated case is built through the documented native bus/plant/machine
APIs and saved beside the output. Initial attempts to import a legacy RAW
failed before simulation (including a version-explicit conversion attempt);
their logs are retained, not presented as model results. The successful probe
script SHA-256 is
`65b8328eda90ca4c21a0ad4ac8c459bbb379a073e47b0f84a4565cc879219c65`.
Full scripts, native inputs, binary outputs and manifests remain private.

## Full regression and remaining work

Full registered regression exits zero with both strict flat and fault opt-ins
enabled. Fresh Surefire reports give 2,704 tests: 2,615 passed, 89 skipped,
zero failures/errors. Only reports written after the run began were counted.

```text
mvn -q test -Pextended-tests --fail-at-end -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| ipss.plugin.core | 129 | 4 | 0 |
| ipss.plugin.3phase | 468 | 28 | 0 |
| ipss.test.plugin.core | 2,107 | 57 | 0 |

All six strict one-second flat runs pass at 1/240 s, with maximum voltage drift
3.13e-12 pu and speed drift 5.67e-14 pu. All fifteen broad fault execution
checks pass, and every baseline trajectory's raw hash matches the preceding
source-recovery archive. Their physical overvoltages remain unresolved.
The unrelated untracked WECC240/JAX test is excluded. The core suite aggregator
runs its registered classes; its zero-count wrapper is not counted as extra
passing tests. Existing unrelated caught diagnostic exceptions in the reactor
log are not represented as an exception-free claim.

The two private simulation opt-ins are restored to false after validation;
the actual enabled configuration, full log, fresh XML reports and raw benchmark
bundles are preserved under the ignored private verification archive
`2026-10-17-regfma1-boundaries`, outside build directories. The full log's raw
SHA-256 is `40ca4ce7720c1dcf36f582ac05b0230812e2f029cf18528d1085394808a8eafa`.

- [x] Fix and discriminate MVA-base current limiting and QVflag input routing.
- [x] Cold-regenerate all three PowerWorld boundary probes twice.
- [x] Add protected, repeatable selected-site/timestep diagnostic settings.
- [x] Execute h/2 and h/4 fault studies without tuning physical parameters.
- [x] Complete and record the full registered regression before committing.
- [ ] Correct monitor/custom-state endpoint and event-side observation
  alignment; retain historical exports and add exact timing regression tests.
- [x] Cold-run a native negative-Q, Iqrmin=0 fault discriminator twice and
  distinguish PowerWorld's documented zero-disables convention.
- [ ] Compare the exact new negative-Q profile and every named state in
  InterPSS after correcting observation-time alignment.
- [ ] Add the exact reference simulator twenty-value schema and RAW coupling path with
  strict parser tests; preserve the separate legacy exchange contract.
- [ ] Compare complete new active-limiter trajectories, not just the boundary.
- [ ] Localize the first differing state and network iteration in the severe
  Texas2k events against an independent exact-profile reference.
- [ ] Obtain full-case reference-tool execution and physical fault acceptance.

Existing immutable references are not overwritten. Proprietary model-library
pages, licensed inputs and full raw simulator bundles are not published.
