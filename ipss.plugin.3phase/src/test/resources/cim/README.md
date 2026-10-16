# CIM import test data

## Public reference feeders

The eight `cimhub/{YY,DY,GYD,DD}{Bal,UnBal}.xml` files are unmodified members
of `ieee4/xfmr_examples.zip` from GRIDAPPSD/CIMHub commit
`5ba4c63fa525108928942d564fddc022c3fb031f`.
The corresponding `.dss` files are unmodified `ieee4/base/` files from that
commit. The upstream copyright, license and disclaimers are retained in
[cimhub/CIMHub-LICENSE.md](cimhub/CIMHub-LICENSE.md).

Source: https://github.com/GRIDAPPSD/CIMHub/tree/5ba4c63fa525108928942d564fddc022c3fb031f/ieee4

`YYBalCore` is a clearly marked derivative of `YYBal`: its transformer adds
0.1% no-load loss and 0.2% magnetizing current. The RDF core conductance and
susceptance use the primary winding impedance base; the DSS input uses
`%noloadloss=.1 %imag=.2`. All other electrical data are unchanged.

`cimhub/reference-manifest.json` records engine versions, source hashes, source
power and circuit losses. Voltage CSV files contain complex phase-to-ground
voltages in volts. The regression compares line-to-line complex voltages on
floating delta secondary buses; it compares phase-to-ground voltages elsewhere.
Each case includes all 12 physical bus/phase measurements. The extra InterPSS
Thevenin-source bus has no counterpart in the exported DSS bus list.

## Mixed-device fixture

`mixed.xml` and `mixed.dss` independently specify the same five-node test
circuit. These are new InterPSS fixtures, not CIMHub exports. The source angle
is 0.1 radians, base voltage is 12.47 kV, and load-side base voltage is 480 V.

The first line uses sequence parameters per metre. The next uses a two-phase
matrix **ordered C, B in RDF and B, C in DSS**. A B-only line feeds a 100 kVA
single-phase transformer, whose total series R/X are 0.4%/2%, and whose
secondary tap is 1.0125. The transformer also has 0.1% core loss and 0.2%
magnetizing current. Its CIM short-circuit impedance is the magnitude, and
its no-load exciting current includes both active and reactive components.

A BC delta load uses distinct P/Q ZIP coefficients; the other load is on
phase B. A two-section C-phase shunt supplies 2.073345333 kvar at rated voltage.
A B-phase connection injects 5 kW/1 kvar. An open BC tie is present in RDF;
it contributes no admittance in the independent DSS circuit.
The reference contains **all 10 energized physical bus/phase measurements**.

## Reproducing the native references

Use DSS-Python 0.15.7 with DSS C-API 0.14.5 (OpenDSS SVN 3723). For each
independent `.dss` circuit:

1. Compile the circuit in a fresh DSS context. IEEE four-bus files specify
   `earthmodel=carson`; frequency is 60 Hz.
2. Set `controlmode=off tolerance=1e-10 maxiterations=100`, then solve and
   require `ActiveCircuit.Solution.Converged`.
3. For every `ActiveCircuit.AllBusNames` entry, call `SetActiveBus`, then
   export each `ActiveBus.Nodes` entry with the paired real/imaginary values
   from `ActiveBus.Voltages`.
4. Preserve the header `bus,phase,real_volts,imag_volts`. Do not use InterPSS
   voltages or the importer's intermediate DSS text as reference inputs.

DSS compilation can change the process working directory, so use absolute
paths when generating files. `mixed-manifest.json` records the mixed-circuit source/reference hashes.
These Java regression tests consume checked-in
reference CSVs and require neither Python nor a native simulator at test time.

The [extension fixtures](extensions/README.md) add 23 independent native-reference
circuits for split-phase services, three-winding transformers and shielded cable
geometry, with voltage, source-power and cable-matrix comparisons.

The [additional public feeders](public/README.md) add eight unmodified CIMHub
cases, from 21 to 1,511 connectivity nodes, for import, convergence and independent
OpenDSS complex-voltage/source-power comparisons. Their manifests pin upstream
files and reference hashes; the catalog records measured errors, acceptance
limits and remaining blockers found while screening 24 public XML candidates.
