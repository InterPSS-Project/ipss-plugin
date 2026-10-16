# Additional public CIM distribution feeders

Source: [GRIDAPPSD/CIMHub](https://github.com/GRIDAPPSD/CIMHub/tree/5ba4c63fa525108928942d564fddc022c3fb031f),
pinned at `5ba4c63fa525108928942d564fddc022c3fb031f`.
The XML files are unmodified upstream bytes, compressed with gzip. The retained
[license](CIMHub-LICENSE.md) applies. [manifest.json](manifest.json) records exact
upstream paths, download links, XML SHA-256 hashes and source connectivity counts.
The compressed payload totals about 2.5 MB; tests need no network or simulator.

## Added automated cases

| Case | CIM connectivity nodes | Purpose | Upstream directory |
| --- | ---: | --- | --- |
| ieee13x | 21 | Small mixed-equipment feeder, service transformers and DER | CPYDAR/xml |
| IEEE13_PV | 24 | Small PV feeder | OEDI/xml |
| IEEE123 | 130 | Unbalanced distribution with reconfiguration switches | model_output_tests |
| IEEE123_PV | 214 | Secondary transformers and distributed PV | model_output_tests |
| IEEE390par | 390 | North American low-voltage network | lv_network/lvn_examples.zip |
| IEEE390_PV | 390 | Low-voltage network with PV | OEDI/xml |
| R2_12_47_2 | 853 | PNNL taxonomy feeder and residential services | model_output_tests |
| Transactive | 1,511 | Large secondary-load population | model_output_tests |

Counts come from these exact XML files, not the nominal case names or an upstream
summary table. InterPSS adds one internal Thevenin source bus.

`CimPublicFeederTest` verifies the XML and reference CSV hashes, preservation of
every ConnectivityNode, one swing bus, convergence, and independent native
OpenDSS **complex voltages and external source P/Q**. All 3,533 physical buses
and 7,837 phase voltages are covered; missing/duplicate reference buses or phases
fail the test. Floating delta buses `610` (IEEE123 variants) and `node_610`
(Transactive) use all three line-to-line phasors because their phase-to-ground
common mode is arbitrary. No angle alignment or fitted offset is applied.

### Independent references and matched settings

The path is public XML → **upstream CIMHub 1.1.0** → **native DSS C-API 0.14.5**
(OpenDSS SVN 3723), through DSS-Python 0.15.7. Neither the InterPSS importer nor its
intermediate DSS output is used to generate the references. The upstream release
jar is from the pinned repository above; its SHA-256 is recorded in
`reference-manifest.json`. `ExportCimHubReference.java` supplies local Jena queries
instead of a Blazegraph server. It increases the converter's DecimalFormat output
precision to 12 fractional digits, avoiding rounded nominal voltages (notably
208/sqrt(3) V); upstream electrical mapping code is unchanged. Some upstream
matrix formatting still uses six significant digits.

- Both engines use the exact CIM source impedance, voltage/angle, 60 Hz,
  load multiplier 1, fixed taps, fixed shunt sections and fixed DER P/Q.
- Automatic controls are disabled. Explicit CIM tap positions are restored after
  the native converter's `calcv` initialization.
- PV/storage elements are replaced by constant-P/Q injections using the CIM
  connection setpoints, excluding inverter controls and conversion losses.
  The generator checks that any phase-specific setpoints can be aggregated.
- Load voltage cutoffs are disabled (`vminpu=0`, `vmaxpu=1e6`), retaining the
  specified constant-P/I/Z or ZIP dependence.
- Native tolerance is 1e-10; InterPSS fixed-point tolerance is 1e-8 pu, both with
  at most 200 iterations. These are different solvers' convergence criteria;
  neither is the cross-engine accuracy limit.
- Native `ppm_antifloat=0` except for delta secondary transformers, which retain
  1 ppm to avoid singular floating windings. InterPSS uses its default fixed-point
  options, including its 1e-6-pu resistive transformer stabilization. These
  artificial admittances differ and leave small residual voltage/power differences.

### Measured parity, 2026-10-16

| Feeder | Max complex voltage error (pu) | Max angle error (degrees) | Source ΔP (kW) | Source ΔQ (kvar) |
| --- | ---: | ---: | ---: | ---: |
| ieee13x | 5.984e-9 | 2.064e-7 | +0.010003 | +0.000008 |
| IEEE13_PV | 6.939e-9 | 3.211e-7 | +0.010022 | +0.000010 |
| IEEE123 | 2.572e-6 | 0.000133392 | +0.020663 | +0.002161 |
| IEEE123_PV | 2.566e-6 | 0.000133065 | +0.019917 | +0.000077 |
| IEEE390par | 6.431e-6 | 0.000266304 | +1.423844 | +0.055654 |
| IEEE390_PV | 5.601e-6 | 0.000288362 | +1.544126 | +0.032686 |
| R2_12_47_2 | 5.579e-8 | 0.000002656 | +0.011244 | -0.003390 |
| Transactive | 1.251e-6 | 0.000065466 | +0.020554 | +0.002365 |

Δ means InterPSS minus native OpenDSS. The uniform complex-voltage gate is
**1e-5 pu**. Source P/Q gates are **0.05 kW/kvar**, except IEEE390's **2 kW / 0.1 kvar**
gates. Its largest P difference is 0.00446% of the native feeder import. The
larger absolute power allowance covers cumulative artificial grounding across
its many delta transformers plus finite-precision conversion/solution effects;
this is bounded agreement, not bitwise parity. Detailed maxima, including voltage
magnitude errors, are in `parity-results.csv`; tests regenerate reports under
the three-phase module's `target/cim-public-parity/`.

The combined distribution CIM/RDF suite passed **92 tests, zero failures/errors/skips**
(84 distribution and eight shared RDF tests). Seven physical-unit regressions cover
small positive phase impedances, off-nominal transformer leakage and single-phase
constant-P/I/Z/ZIP voltage bases. The shared tests include a fresh-JVM check that
local imports succeed when HTTP-client initialization is blocked.
The full extended reactor also passed under restricted permissions: **2,640 tests,
zero failures/errors, 108 existing skips**. Log: repository `target/cim-offline-full-tests.log`.

The comparison fixed doubled split-phase polarity (`Ns2` plus clock 6), artificial
line-impedance floors affecting closed switches, small impedances mistaken for
absent phases, rated-to-bus voltage scaling for single-phase loads/capacitor
equivalents, 208-V versus 240-V split-service ratings, and double application of
transformer rated-to-bus voltage ratios in leakage conversion.

Run:

```text
mvn -o -pl ipss.plugin.3phase -am test -Dtest=CimRdf*Test,CimDistribution*Test,CimPublicFeederTest -Dsurefire.failIfNoSpecifiedTests=false
```

Repackage from a local checkout at the pinned revision:

```text
python package_fixtures.py /path/to/CIMHub
```

Reproduce the independent references (Java 21 and Python with `dss-python==0.15.7`):

```text
java -Xmx4g -cp /path/to/CIMHub/releases/cimhub-1.1.0.jar ExportCimHubReference.java . /path/to/native-output
python generate_reference.py /path/to/native-output /path/to/CIMHub/releases/cimhub-1.1.0.jar
```

Run those commands from this fixture directory. Alternatively, extract
`native-circuits.zip` to an empty directory and run just the Python command.
The archive contains all base circuits, coordinates, UUID mappings and applied
snapshot commands. To run one case directly in OpenDSS, compile its `_base.dss`
then redirect its `_snapshot.dss`. The generator verifies unchanged XML hashes,
requires native convergence and finite results, and records engine/jar versions,
input/output hashes and source powers. Maven uses the checked-in references and
needs neither Python nor OpenDSS. Replaying the archived circuits on 2026-10-16
reproduced all eight reference datasets and their hashes byte-for-byte.

## Other screened candidates

On 2026-10-16, 24 upstream XML candidates were screened: all 11
`model_output_tests/*.xml`, four `CPYDAR/xml/*.xml`, four `OEDI/xml/*.xml`, both
`ieee9500/xml/*.xml`, and the three XML members of `lv_network/lvn_examples.zip`.
Local screening logs are `target/cim-public-candidates.log` and
`target/cim-public-candidates-extra.log`. These are exploratory results rather
than tests that assert permanent rejection of the unsupported cases.

| Candidate | First current blocker / disposition |
| --- | --- |
| model_output_tests/IEEE13 | Three-winding neutral grounding rejected at sub3_End_2 |
| model_output_tests/IEEE13_Assets | Noncanonical overhead conductor order at line 632645 |
| model_output_tests/IEEE37 | Delta regulator tank phase connection at reg1c |
| model_output_tests/IEEE8500 | Unsupported conducting equipment hvmv_sub_hsb |
| model_output_tests/IEEE8500_3subs | Unsupported winding grounding/connection at 2001-mt123_End_1 |
| model_output_tests/EPRI_DPV_J1 | No-load loss/exciting-current consistency check |
| model_output_tests/ACEP_PSIL and CPYDAR/xml/ACEP_PSIL | Magnetizing admittance on a two-winding delta secondary |
| CPYDAR/xml/j1red | Conflicting ACLineSegment.b0ch scalar values |
| OEDI/xml/SmartDS | No-load loss/exciting-current consistency check |
| ieee9500/xml/ieee9500bal and ieee9500unbal | Unsupported conducting equipment microturb-1 |
| lv_network archive: IEEE390 | Cable wire type unsupported by the geometry path at 1000aa |
| lv_network archive: LVTest | Native case is **50 Hz**; outside the importer's current 60-Hz scope, even though the numerical solve converged |
| CPYDAR/xml/ieee123x and OEDI/xml/IEEE123_PV | Imported and converged; additional closely related IEEE123 variants, omitted from the bundle to reduce duplication |

A first rejection is not an exhaustive equipment audit; more issues may appear
after it is resolved. Do not discard equipment or modify public XML simply to
make an import pass. The 50-Hz case must not be treated as an accuracy pass.

## Further sources and priorities

- [Powergrid-Models](https://github.com/GRIDAPPSD/Powergrid-Models/blob/develop/README.md)
  has newer CIM-Graph/CIM-Builder/CIM-Loader-based model exports, including
  IEEE13_OCHRE services, IEEE8500, PNNL taxonomy and EPRI PV feeders. Pin and
  qualify a specific revision before treating newer exports as interchangeable.
- [CIMHub low-voltage cases](https://github.com/GRIDAPPSD/CIMHub/tree/5ba4c63fa525108928942d564fddc022c3fb031f/lv_network)
  pair XML with original DSS circuits and comparison scripts.
- [CIMHub line constants](https://github.com/GRIDAPPSD/CIMHub/tree/5ba4c63fa525108928942d564fddc022c3fb031f/line_constants)
  supplies overhead and cable calculation examples; its source DSS files are
  useful for independent matrix validation, but are not themselves CIM fixtures.

Next priorities are IEEE13_Assets conductor ordering, IEEE37 delta regulators, then
IEEE8500/9500 equipment coverage and scale. European LVTest requires frequency
support first.
