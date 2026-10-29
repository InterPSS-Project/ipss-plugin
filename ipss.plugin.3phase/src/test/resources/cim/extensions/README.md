# CIM distribution extension references

These 23 synthetic circuits are original test data. Each has a CIM100 XML file,
an independently specified native OpenDSS circuit, complex bus voltages in volts,
and external source-terminal power in kW/kvar. Cable cases also include native
R/X/C matrices in ohm/m and nF/m. Neither native circuits nor reference results
are derived from the Java importer's intermediate DSS output.

`generate_reference.py` constructs both input specifications from stated equipment
ratings and runs the native engine. To regenerate, run it in a Python environment
with `dss-python==0.15.7`, using DSS C-API 0.14.5 (revision
`87d85c2622c8281b92255335bc7c09b11191b21d`, OpenDSS SVN 3723).
Maven uses the checked-in references and does not require Python or OpenDSS.

- `split_a/b/c`: center-tapped 120/240-V service on each primary phase, unequal
  120-V loads, a 240-V load, two-conductor service-drop impedance, unequal winding
  ratings, fixed taps and no-load loss/excitation. The reversed `Ns2` terminal
  preserves the 180-degree displacement.
- `split_clock`: an ordered `Ns2` winding also carries clock 6, as in public
  CIMHub exports. The explicit conductor order sets polarity; clock 6 must not
  reverse it a second time.
- `three_yyy/dyy/ydy/yyd`: 12.47/4.16/0.48-kV transformers with unequal ratings,
  taps, unbalanced loads and core loss. Delta-side voltages are compared line-line
  because their common-mode voltage is not uniquely defined.
- `three_dyy_lead/ydy_lead`: the alternative leading vector groups.
- `three_mesh`: pairwise mesh impedances, including reversed winding references.
- `three_tank`: three short-circuit tests and a no-load test describe the same
  physical transformer as `three_yyy`.
- `three_zero/negative`: a zero/negative leakage star arm. Pair impedances remain
  nonsingular; the importer must not divide by individual star arms.
- `cable_cn/ts`: concentric-neutral/copper tape-shield cable with grounded screens,
  plus variants with bare neutrals, single B phase, phases AC and reordered CA.
  Earth return is simple Carson, 100 ohm-m, 60 Hz; length is 500 m.

Native power-flow tolerance is 1e-10. InterPSS uses its established 1e-8-pu
fixed-point tolerance; the complex-voltage gate is 1e-6 pu and the source P/Q gate
is 0.05 kW/kvar. Matrix gates are 1e-9 ohm/m and 1e-8 nF/m. Source power is measured
at the external source terminal in both engines, excluding internal Thevenin
losses. Additional JUnit tests cover rejected data, no partial result publication,
network-base invariance and primitive current conservation.

Model references:

- [CIMHub](https://github.com/GRIDAPPSD/CIMHub), pinned commit
  `5ba4c63fa525108928942d564fddc022c3fb031f`: `DistComponent`,
  `DistXfmrTank`, `DistXfmrCodeRating`, `DistCable`, `DistConcentricNeutralCable`,
  `DistTapeShieldCable` and the CIM100 queries.
- [EPRI cable modeling](https://opendss.epri.com/CableModelinginOpenDSS.html).
- [DSS C-API 0.14.5 cable equations](https://github.com/dss-extensions/dss_capi/tree/0.14.5/src/General):
  `LineConstants.pas`, `CNLineConstants.pas`, `TSLineConstants.pas`.
- [DSS C-API transformer primitive](https://github.com/dss-extensions/dss_capi/blob/0.14.5/src/PDElements/Transformer.pas).
