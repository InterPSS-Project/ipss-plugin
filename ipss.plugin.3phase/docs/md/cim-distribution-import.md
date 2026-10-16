# CIM distribution import

`CimDistributionDataParser` reads local CIMHub **CIM100 RDF/XML** into a
`DStabNetwork3Phase` for unbalanced, static **60 Hz** distribution power flow.
It does not need Blazegraph, a running CIMHub service, OpenDSS, or Python.
The transmission CGMES and distribution importers share RDF infrastructure;
their electrical mappings remain profile-specific.

```java
IpssCorePlugin.init();
var parser = new CimDistributionDataParser();
var network = parser.parse(Path.of("feeder.xml")); // 10 MVA base, already per unit

var powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network);
powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
powerFlow.setTolerance(1e-8);
powerFlow.setMaxIteration(200);
if (!powerFlow.powerflow()) {
    throw new IllegalStateException("Distribution power flow did not converge");
}
parser.getWarnings().forEach(System.out::println);
```

Imports are in `org.interpss.threePhase.dataParser.cim`,
`org.interpss.threePhase.powerflow`, `org.interpss.threePhase.util`,
`org.interpss.IpssCorePlugin`, and `java.nio.file.Path`.
`parse` throws `IOException` for file/XML errors and `IllegalArgumentException`
for unsupported or inconsistent electrical data. A failed import clears the
previous result; it never publishes a partial network.

For multiple equipment/state files or multiple feeders:

```java
var network = parser.parse(
    List.of(Path.of("equipment.xml"), Path.of("state.xml")),
    "the-feeder-mRID", 5.0);
```

The selector accepts a Feeder mRID or full RDF URI. Omit it only when exactly
one Feeder is present. Files are merged into one RDF graph; conflicting scalar
values are errors, not last-file-wins overrides. Relative RDF IDs use a shared
base URI. DTDs and external XML entities are rejected.

## Supported electrical data

| CIM data | Import behavior |
| --- | --- |
| Feeder, Terminal, ConnectivityNode | Feeder selection, terminal ordering, stable node identities; names need not be unique |
| EnergySource | One ABC source, voltage magnitude and angle (radians), positive/zero-sequence Thevenin impedance |
| ACLineSegment | Instance sequence impedance, per-length sequence impedance, or per-length phase matrices; line length in metres |
| PhaseImpedanceData | Complete symmetric triangular matrix, including mutual coupling and shunt susceptance; honors ACLineSegmentPhase sequence order |
| OverheadWireInfo / WireSpacingInfo | Overhead geometry, grounded-neutral Kron reduction, Carson calculation at 60 Hz using specified AC resistance |
| ConcentricNeutralCableInfo / TapeShieldCableInfo | One-, two- and three-phase cable geometry, grounded shield/neutral reduction, insulation capacitance, optional bare neutral and conductor-order mapping |
| PowerTransformerEnd | Two- and three-winding three-phase transformers, solidly grounded wye and delta, winding resistance, mesh or end reactance, compatible clock 0/1/11 |
| TransformerTank / TransformerEndInfo | Two- and three-winding single-phase or ABC tanks, including center-tapped services; direct tank-info link or Asset association; ordered phase codes; short-circuit and no-load test conversion |
| RatioTapChanger | Fixed operating step, falling back to normal/neutral step; validates bounds while preserving nominal voltage bases; three-winding taps enter the coupled primitive |
| Split-phase s1 / s2 / s12 | Separate 120-V legs, 240-V line-line loads and two-conductor service drops; ordered Ns2 reverses the winding polarity |
| EnergyConsumer / EnergyConsumerPhase | Balanced totals or individual phase powers, grounded-wye and delta, constant P/I/Z and independent P/Q ZIP coefficients |
| LinearShuntCompensator / Phase | Fixed section count, phase connection and conductance/susceptance; represented as constant impedance |
| PowerElectronicsConnection / Phase | Fixed P/Q snapshot using CIMHub's positive-generation sign convention; represented as negative load, not an inverter/dynamic model |
| Switch / LoadBreakSwitch / Breaker / Fuse / Recloser / Disconnector / Jumper / Sectionaliser | Fixed open/closed status; open overrides normalOpen; closed contact uses 0.0001 ohm resistance |

CIMHub load-response coefficients may sum to 1 or 100; the importer checks and
normalizes them. Voltages are in volts and powers in watts/vars, except CIMHub
transformer **test losses in kW**. A short-circuit test's `leakageImpedance` is
**impedance magnitude**; reactance is recovered after subtracting resistance.
No-load exciting current includes the active-loss component, which is removed
to obtain magnetizing susceptance. These conversions follow CIMHub's reference
implementation and are checked against native OpenDSS results.

The importer uses an internal OpenDSS text representation to reuse the existing
three-phase equipment builders. That text is never executed by an external
program. A temporary file is removed after import. `getMappedDss()` exposes it
for inspection; fixed tap/clock adjustments are applied separately to the
returned network, so that text is not a complete standalone simulation export.
Do not call `convertActualValuesToPU` again on the returned network.

### Shared implementation

`ipss.plugin.core` provides `CimRdfUtils` for RDF/XML loading, DTD/entity rejection,
resource-type queries and scalar property lookup. Both `CGMESRdfParser` /
`CGMESModel` / `CGMESPropertyBag` and the distribution importer use these helpers.
The XML precheck streams through SAX, avoiding a second in-memory document tree.
Local RDF/XML parsing bypasses Jena's HTTP-client initialization, so it does not
require loopback socket access. A fresh-JVM regression verifies this behavior.
CIM100 namespace constants also come from the existing core CIM package.

CGMES retains its UUID compatibility repairs and permissive numeric defaults.
Distribution retains strict conflict, finite-number and resolved-reference checks.
Both importers now reject DTDs; CGMES callers must provide self-contained RDF/XML.
Phase connectivity, equipment mapping and physical units remain profile-specific
(CIMHub distribution uses W/V where CGMES uses MW/kV). Network construction,
line geometry, transformer/load models and per-unit conversion reuse the existing
OpenDSS implementation rather than duplicating electrical calculations.

`getBusIds()` maps ConnectivityNode RDF URIs to InterPSS bus IDs.
`getEquipmentIds()` maps equipment URIs to element names. For multiport transformers,
this is the parent element name; `getEquipmentBranchIds()` returns every actual
network branch ID for that equipment. Balanced and phase-resolved loads may produce multiple
winding-load objects beneath one equipment name. `getCimNames()` retains display
names. The maps and warning list are immutable. The parser is not thread safe.

### Split-phase, three-winding and cable models

Split-phase bus slots A/B represent **s1/s2**, separated by 180 degrees, rather than
ordinary phases separated by 120 degrees. Split-phase wye load ratings accept
the service line-line convention (240 V, divided by two) and CIMHub's equivalent
three-phase convention (208 V, divided by sqrt(3)). Explicit ordered winding
phases set polarity even when a phase-angle clock is also supplied. A wye `s12` aggregate
is divided equally between the legs; use individual phase records for unequal
loads. A delta `s12` load is connected across the two legs. Service conductors
can use phase impedance matrices or supported wire/cable geometry.

Three-winding transformers use `ThreeWindingTransformerAdmittance` in the shared
three-phase model layer. A two-loop impedance inversion preserves coupling and
supports valid zero or negative star arms. Each winding's impedance is referred
to winding one's power base, even with unequal winding ratings. Tank models
require all three pairwise short-circuit tests and check their losses against
the winding resistances. Fixed taps and winding polarity enter the primitive;
excitation is placed on winding two, matching the OpenDSS reference model.

The existing core `IBranch3Phase` explicit-admittance API represents this primitive
exactly. Co-located split-phase secondary windings share one bus block. Three
distinct winding buses use three coupled branch blocks; sum the incident block
currents to obtain each winding's current. These blocks are not three independent
physical transformers. Use fixed-point power flow; the branch blocks must not be
treated as ordinary series impedances by OPF or radial-sweep algorithms. No change
to the external `ipss-core` dependency is required.

`CableLineConstants` supplies format-independent line constants in SI units.
It implements simple Carson earth return at **60 Hz and 100 ohm-m soil resistivity**,
using specified AC conductor resistance and OpenDSS's engineering constants.
Concentric neutrals and copper tape shields are grounded and Kron-reduced, as are
optional bare neutrals. Insulation permittivity/thickness determine phase-to-shield
capacitance. Unbonded/cross-bonded shields, explicit neutral voltages, frequency
sweeps and alternative soil models are outside this calculation.

## Scope and explicit limits

This is a CIMHub distribution power-flow profile, not general support for every
CIM/CGMES release or every CIMHub example. The public-fixture catalog identifies
the exact validated IEEE13/IEEE123 variants and other feeders; support for those
files does not imply support for every upstream variant.
Unsupported conducting equipment with terminals causes an error.

The current importer rejects transformers with more than three windings,
cross-phase two-winding transformers/switches, explicit-neutral impedance
matrices, noncanonical conductor ordering in overhead geometry (use
phase matrices instead), nonzero line shunt conductance,
non-solidly-grounded transformer windings, incompatible vector groups,
magnetizing admittance on two-winding delta secondaries, exponential load response and
out-of-service equipment. Open switches that leave buses without a voltage
base also fail; island reconstruction is not provided.

Automatic regulator/capacitor/inverter controls, storage energy state, protection
logic, dynamics, profiles, coordinates and operational ratings are not mapped.
Warnings identify snapshot equivalents. This result is suitable for power flow;
operational constraints must be supplied before using supported ordinary branches
for OPF; the multiport transformer blocks also require a compatible OPF formulation. The distribution
network type alone does not imply that a dynamic model has been created.

On floating delta secondaries, compare **line-to-line** voltages: line-to-ground
common-mode voltage is not uniquely defined by the ideal circuit. The reference
tests use complex voltage comparisons, including phase angle.

## Verification

Run the focused tests (including dependency modules):

```text
mvn -o -pl ipss.plugin.3phase -am test -Dtest=CimRdf*Test,CimDistribution*Test,CimPublicFeederTest -Dsurefire.failIfNoSpecifiedTests=false
```

Run the complete plugin suite, including extended tests:

```text
mvn -o --fail-at-end test -Pextended-tests
```

Fixtures and provenance are in `src/test/resources/cim/README.md`.
Eight unmodified CIMHub IEEE four-bus cases cover balanced/unbalanced YY, DY,
grounded-Y/delta and delta/delta configurations. A ninth case adds three-phase
core losses. A separate mixed-device feeder covers phase matrices, their
ordering, single-phase transformer test data, tap position, source angle, ZIP,
capacitor, generation and an open tie. Native reference results come from
DSS-Python 0.15.7 / DSS C-API 0.14.5, using independent input circuits, not from
this importer. The voltage gates are 0.0002 pu for the overhead-geometry cases
and 0.00001 pu for the mixed-device case.

The tests also exercise multi-file RDF descriptions, feeder selection, per-unit
base invariance, immutable metadata, invalid matrices/numbers/ZIP/taps,
unresolved references, unsupported equipment and XML entity rejection.

Implementation reference: [GRIDAPPSD/CIMHub](https://github.com/GRIDAPPSD/CIMHub),
commit `5ba4c63fa525108928942d564fddc022c3fb031f`, especially
`DistSubstation`, `DistPowerXfmrWinding`, `DistXfmrCodeRating`, `DistLoad`,
`DistPhaseMatrix` and `queries/q100.xml`.

The extensions are checked against 23 separately specified native OpenDSS circuits
in `src/test/resources/cim/extensions/`. They cover services on primary phases
A/B/C, unequal leg loads and 240-V loads through a service drop, three-winding
YYY/DYY/YDY/YYD connections, leading/lagging vector groups, reversed mesh references,
split-phase clock polarity, tank tests, unequal ratings, fixed taps, core loss,
zero/negative star arms, and both cable types with one/two/three phases, bare
neutrals and reordered conductors. Complex voltages (including angles), source
terminal P/Q, and cable R/X/C matrices are compared. See the fixture README for
reference-engine versions and reproduction steps.

Eight additional unmodified public CIMHub feeders (IEEE13 variants, IEEE123 with
and without PV, IEEE390 with and without PV, R2_12_47_2 and Transactive) now have
independent native OpenDSS parity coverage. They span 21 to 1,511 connectivity nodes.
See `src/test/resources/cim/public/README.md` for exact versions, hashes and the
screening results for 24 candidate files. These checks preserve every source
connectivity node and compare all 7,837 physical voltage phasors, including angles,
plus external source P/Q. Maximum complex error is **6.43104e-6 pu**, below the
uniform **1e-5-pu** gate. Floating delta buses use line-to-line phasors. Source
differences are at most **1.54413 kW / 0.05566 kvar**; the fixture README explains
the distinct artificial grounding treatments and the power gates. Reference
circuits come from the upstream CIMHub converter, independently of the InterPSS
mapper. All 92 focused CIM/RDF tests pass, including seven numerical regressions
and the fresh-JVM offline initialization regression.

### Validation recorded 2026-10-16

- Full extended reactor after public OpenDSS parity and the offline-reader fix:
  **2,640 tests, 0 failures, 0 errors, 108 skipped** (128 in plugin core, 468 in
  three-phase and 2,044 in core integration tests), under restricted permissions.
  Build log: `target/cim-offline-full-tests.log` at the repository root.
- All **84 distribution CIM tests** and **8 shared RDF tests** passed. Shared tests
  cover cross-file identity, property types, strict versus permissive access,
  DTD/entity rejection and CGMES-only UUID repair. Existing skips remain unchanged.
- Maximum complex-voltage error: **0.000184827 pu** across the eight original
  CIMHub circuits, **0.000015296 pu** in the added three-phase core-loss case,
  and **0.000001206 pu** in the mixed-device circuit.
- Maximum complex-voltage error across the **23 extension circuits**:
  **1.68218e-7 pu**, against a 1e-6-pu gate. Source-terminal P/Q and cable R/X/C
  comparisons also passed, together with six existing center-tapped OpenDSS regressions.
- All eight public-feeder comparisons pass their voltage and source-power gates.
  The archived independent native circuits reproduce the reference hashes exactly.
- No simulator is required for these regression tests. Reference CSVs were
  generated separately using the native engine described in the fixture manifest.

The comparisons also exposed and fixed omitted transformer terminal shunts in
`Dstab3PBranchImpl`, including the single-phase path, and required an explicit
Carson AC-resistance choice in the shared overhead-geometry builder. Existing
OpenDSS behavior retains its previous default resistance calculation.
Public parity additionally corrected ordered split-phase polarity, preserved
small physical line impedances, normalized single-phase load/shunt voltage bases,
and removed double application of transformer rated-to-bus voltage ratios.
The latter shared OpenDSS fix is checked against physical terminal admittance;
existing conversion assertions now use the rated winding impedance base.
