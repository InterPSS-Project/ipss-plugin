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

## Distinction from newer and converted models

The existing GEWTGCU1 implementation uses `101/1/2/18/3/3`. Although its constant
and state counts match, the integer and observable allocations differ. A shared
equation implementation requires explicit proof of the legacy parameter, state,
limiter and network-interface mapping. No automatic alias is introduced.

[Vendor conversion notes](https://www.powerworld.com/patch/october-21-2015)
describe conversion of the legacy suite to generic WT3 models. Such conversion
does not establish exact legacy trajectory equivalence.

## Remaining implementation and validation

1. Obtain the matching vendor equation/parameter document and executable library;
   record their hashes and version in private evidence.
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
