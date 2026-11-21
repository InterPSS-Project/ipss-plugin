# GEWTA2 record foundation and runtime gate

GEWTA2 remains unsupported for simulation. `Gewta2NativeRecord` decodes the
legacy aerodynamic record without attaching a runtime or marking the model
loadable. It preserves source location, machine identity and all nine finite
constants in their original order. It rejects a different wrapper, allocation,
constant count or model identity, including GEWTARU1.

The publicly published [modeling-data appendix](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/GEN-2016-050_LOISIS.pdf)
establishes the legacy allocation `505 0 0 9 1 4`. The newer aerodynamic model
uses a different model type and an additional integer slot. Similar constants
and allocation sizes do not establish equivalent equations or initialization.
The decoder deliberately does not assign unproved parameter meanings, validate
physical ranges from another model, or silently insert that integer slot.

## Missing implementation contract

Obtain the original aerodynamic data sheet and matching executable library or
an exact independent implementation. Establish the power-coefficient surface,
tip-speed and pitch constraints, wind-speed units, machine/turbine bases,
conversion-lag coordinate, zero-time behavior, initial wind/pitch solution and
adjustment rule. Identify the four published outputs and their evaluation stage.
Resolve the mechanical and wind-input bindings to GEWTG2, GEWTT1, GEWTP2 and
GEWGD1 before choosing interfaces or reusing numerical kernels.

The [independent vendor release notes](https://www.dsatools.com/wp-content/uploads/2021/07/TSAT-21.0-Release-Notes.pdf)
list the exact model identifier as supported, making that implementation a
candidate reference if available. This listing alone does not prove equation
equivalence. The [conversion vendor notes](https://www.powerworld.com/patch/october-21-2015)
describe conversion to generic wind models; such conversion cannot qualify an
exact legacy aerodynamic runtime.

## Integration and verification work

Use one equation implementation and common state/derivative storage for modified
Euler and staggered integration. Sample the intended wind, blade pitch and shaft
speed boundary at each explicit stage; observation must not advance history.
Preserve limiter semantics, rejected-stage rollback and reinitialization.

After the exact source contract is established, test equilibrium and independent
wind/pitch/speed changes, every constraint boundary, zero and positive conversion
time, both integration methods and timestep refinement. Capture complete SMIB
fault trajectories with physical boundary channels and all published internal
signals, retaining event-side duplicates and immutable raw evidence. No SMIB
acceptance is claimed by the record decoder tests.
