# WT3T1 shared numerical leaf

WT3T1 exposes its four numerical states to an enclosing integrator through
`readStaggeredState`, `evaluateStaggered` and `applyStaggeredState`. The converter
owner must supply its current electrical power explicitly. The leaf does not
sample a hidden host, run its own time step or advance an accepted clock.

Ordinary modified Euler and the shared leaf use the same RHS helper. Shared RHS
evaluation uses reusable rate storage and is read-only with respect to numerical
state, published aerodynamic power and attached controllers. State projection
validates the whole vector before publication; restoring a saved vector uses the
same path. There is no additional committed timer or transport history to restore.

Before initialization or while an ordinary predictor is pending, shared access
is rejected. An attached WT3P1 is also rejected: its states must first participate
in the enclosing solver rather than being advanced invisibly by WT3T1. The
converter bundle must retain its attachment guard until all participating leaves
and their integration order are qualified.

The focused checks compare shared Heun and ordinary updates throughout single-
and two-mass electrical/pitch disturbances, check staggered two-step convergence
against the independent single-mass energy solution, and exercise repeated RHS
evaluation, invalid-state atomicity, rollback and ownership rejection. Existing
import and ordinary coupled SMIB reference tests remain regression controls.

These checks qualify the leaf interface, not a complete staggered converter
bundle. Coupled WT3G2/WT3E1/WT3T1 event-side trajectories, pitch-controller state
participation and complete production-interval reference comparisons remain
required. This interface does not establish equivalence to any legacy vendor
model or add a legacy runtime alias.
