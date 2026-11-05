# GEWTG2 legacy source contract

GEWTG2 remains unsupported as a dynamic runtime. The model-owned decoder validates
the published legacy USRMDL allocation `1/1/4/18/3/5`, preserves generator identity
and ordered integer/constant slots, rejects malformed or nonfinite data and stores
immutable values. It neither adds a catalog entry nor attaches a converter.

The wrapper is independently published in two
[utility-hosted model appendices](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/GEN-2016-050_LOISIS.pdf)
and [technical-report model data](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/REP0379_MISO%20Affected%20Systems%20SPP-DISIS-2016-001-1_Restudy_r8%20-%20PUBLIC.pdf).
The tests use synthetic identities and slot values, rather than supplied case data.
These sources establish record shape; they do not establish the complete model
equations or meanings of all integer and observable slots.

A [published utility model listing](https://www.oasis.oati.com/woa/docs/EES/EESdocs/SISR_PID260.pdf)
identifies all 18 real constants in record order:

| Slots | Source labels |
| --- | --- |
| 1–6 | `PRATE`, `XEQ`, `VLVPL1`, `VLVPL2`, `GLVPL2`, `VHVRCR2` |
| 7–12 | `CURHVRCR2`, `VLVACR1`, `VLVACR2`, `RIp_LVPL`, `T_LVPL`, `LVPL1V` |
| 13–18 | `LVPL1P`, `LVPL2V`, `LVPL2P`, `LVPL3V`, `LVPL3P`, `XLVPL` |

The existing `Gewtg2NativeRecord.equivalentReactance()` exposes the second
constant without altering the entered record or registering a runtime alias.
The listing does not identify the four integer controls, three state coordinates,
five observable slots, or the executable's revision.

The manufacturer-authored [modeling report, version 4.5](https://www.researchgate.net/publication/267218696_Modeling_of_GE_Wind_Turbine-Generators_for_Grid_Studies_Prepared_by)
describes the converter's low-voltage active-current management, high-voltage
reactive-current management, recovery, and three-state block diagram. Its
revision history specifically identifies a version 4.2 change to source-current
calculation and the network interface. The report uses a different model
record dialect and does not establish which report revision corresponds to
the legacy `1/1/4/18/3/5` executable. Consequently, matching constant names
and state count do not prove trajectory or network-interface equivalence.

## Distinction from newer and converted models

The existing GEWTGCU1 implementation uses `101/1/2/18/3/3`. Although its constant
and state counts match, the integer and observable allocations differ. A shared
equation implementation requires explicit proof of the legacy parameter, state,
limiter and network-interface mapping. No automatic alias is introduced.

[Vendor conversion notes](https://www.powerworld.com/patch/october-21-2015)
describe conversion of the legacy suite to generic WT3 models. Such conversion
does not establish exact legacy trajectory equivalence.

## Remaining implementation and validation

1. Identify the exact executable revision for the legacy allocation and obtain
   its model documentation and executable library; record hashes and version in
   private evidence. In particular, distinguish network-current behavior before
   and after the documented version 4.2 revision.
2. Establish all integer meanings, state coordinates, current-limit and recovery
   equations, source-impedance treatment, initialization and host ownership.
3. Implement the exact converter and bind its electrical, aerodynamic, pitch and
   drive-train dependencies. Reuse existing kernels only after proving their
   equation and state equivalence.
4. Expose common numerical states and derivatives under both modified Euler and
   staggered integration. Keep observation, accepted histories, event projection,
   local substeps and failed-network rollback explicit.
5. Capture independent synthetic SMIB equilibrium, voltage/frequency disturbance,
   current-limit, recovery, trip and reconnect profiles with all named states and
   physical boundary channels. Retain event-side duplicates and compare the full
   trajectories at the production and refined integration intervals.

Parser tests provide no SMIB or dynamic-accuracy acceptance. Runtime attachment
and exact independent trajectory verification remain open.
