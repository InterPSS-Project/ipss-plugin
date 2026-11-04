# GEWTT1 record foundation and implementation gate

GEWTT1 has a strict model-owned record decoder, but no runtime attachment or
independent SMIB qualification. The decoder preserves the integer and constant
slots without assigning unproved equation meanings or registering a runtime alias.

## Established record boundary

A [published utility study](https://opsportal.spp.org/documents/studies/files/2015_Generation_Studies/GEN-2015-096%20Turbine%20Restudy_final.pdf)
and an [independent published study](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/REP0379_MISO%20Affected%20Systems%20SPP-DISIS-2016-001-1_Restudy_r8%20-%20PUBLIC.pdf)
contain the legacy USRMDL allocation 5/0/1/5/4/3. This identifies one integer
slot, five constants, four states and three output slots. These records establish
the import shape, not complete equations, bases or state coordinates.

The decoder checks exact model/wrapper identity, owner, allocation, cardinality,
integer syntax and finite constants. It retains immutable constants in source
order. It deliberately does not apply the newer GEWT2MU1 validation rules to
legacy constants until exact semantic equivalence is established.

The [conversion vendor release notes](https://www.powerworld.com/patch/october-21-2015)
describe converting this legacy suite to generic WT3 models. Successful generic
conversion cannot qualify an exact GEWTT1 runtime or prove identical initialization
and event behavior.

## Required implementation and verification

1. Obtain the exact legacy model data sheet and matching executable library.
   Record their hashes and version in private evidence. Resolve the integer
   setting, constants and units, four state coordinates and three diagnostics.
2. Establish turbine/generator inertia allocation, damping, shaft stiffness,
   speed bases, power-to-torque conversion and single-mass bypass semantics.
   Prove each proposed reusable two-mass kernel equation against that contract.
3. Bind the aerodynamic input and converter electrical power/speed interfaces
   explicitly; validate owner identity and initialization power balance before
   publishing outputs.
4. Implement shared equations and reusable state/rate buffers for modified Euler
   and staggered integration. Separate trial state from accepted clock/history,
   and expose exact named states, derivatives and physical diagnostics.
5. Independently compare synthetic SMIB equilibrium, mechanical-input changes,
   electrical faults, both inertia modes, damping variations and event timing
   at production and refined intervals. Preserve all samples and event sides.
6. Test repeated observations, predictor/corrector ownership, failed-network
   rollback, trip/reinitialization and host replacement under both methods.

Record tests do not replace these equation and trajectory gates. GEWTT1 remains
unsupported by the runtime catalog until the exact model contract is implemented.
