# GEWTP2 record foundation and runtime gate

GEWTP2 remains unsupported for simulation. `Gewtp2NativeRecord` preserves the
legacy owner, source location, integer slot and ten constants in source order.
It validates record allocation, cardinality and finite constants without
attaching a runtime or claiming equation or trajectory acceptance.

The public [modeling-data appendix](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/GEN-2016-050_LOISIS.pdf)
establishes the legacy allocation `505 0 1 10 3 3`. The newer GEWTPTU1 wrapper
uses another type and two integer slots. The decoder does not insert a slot,
reinterpret the legacy integer or alias the model to the newer controller.
Similar constant and state counts cannot prove equation equivalence.

## Required implementation contract

Obtain the exact legacy data sheet and matching executable or independently
documented exact implementation. Establish parameter meanings and units,
speed-reference initialization, power-order boundary, the three state
coordinates, individual PI constraints, combined pitch constraints, rate limits,
anti-windup, zero-lag behavior and the three published observations. Establish
the shaft, aerodynamic and electrical host bindings and loss-of-host behavior.
The [conversion vendor notes](https://www.powerworld.com/patch/october-21-2015)
describe conversion to generic wind models; that does not qualify an exact
legacy runtime.

Use one equation implementation with common state/derivative storage for both
modified Euler and staggered integration. Sample speed and power at explicit
stages, preserve accepted limiter history, and keep observation read-only.
Rejected stages must restore state and history; reinitialization must clear
pending stages and resolve current host ownership.

After the exact contract is available, verify equilibrium, independent speed
and power perturbations, each PI and actuator bound, rate saturation and
release, zero/positive lag, trip/reconnect and repeated observations. Compare
both integration methods at production and refined intervals using complete
SMIB boundary and internal-state traces, including event-side duplicates.
Record-decoder tests do not establish any of these runtime acceptance claims.
