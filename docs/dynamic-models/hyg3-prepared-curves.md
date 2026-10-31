# HYG3 prepared gate curves

The governor evaluates its equations directly in Java. Gate-curve interpolation
now reuses normalized point arrays instead of cloning and rebuilding them during
every forward or inverse evaluation.

`PsseHyg3GovernorData` advances a curve revision when a scalar or array point
setter is called. The governor prepares both interpolation directions together,
keyed by data-object identity and that revision. Repeated evaluations reuse the
prepared arrays. Invalid point updates fail preparation without publishing a
partial descriptor. Built-in curves, implicit endpoints, extrapolation and
source-specific output clipping retain their existing behavior.

Gate scaling continues to use the live power base and convention. The prepared
geometry does not capture numerical gains or integration state.
Replacing the data owner rebuilds the descriptor even if revisions coincide.
Custom governor classes and custom point providers retain the original getter
dispatch because their getters may carry behavior not represented by a revision.
Public array getters continue to return defensive copies.

`Hyg3PreparedCurveTest` covers forward/inverse evaluation, scalar and array
writes, ownership changes, built-in and partial curves, scaling, invalid updates
and custom-provider fallback. Its allocation check warms the actual methods and
then exercises both directions. The measured loop allocates fewer than 1 KiB in
total, including measurement overhead; it does not allocate point arrays for
each evaluation. This is a component allocation result, not a complete-simulation
speedup claim. The test is registered in `CorePluginTestSuite`.

Before changing the runtime, fresh flat and fault SMIB runs were frozen with
source, bytecode and complete state/boundary traces. The independent boundary
checks passed in both control modes and the deadband/partial-curve profiles.
After rebuilding the optimized runtime, all those complete traces match the
frozen traces byte for byte. The existing independent equation, initialization,
limiter and coupled governor checks also pass.
The registered core suite retains the same previously recorded failures in
endpoint monitoring, converter conformance and disturbance smokes.

The prepared curve is derived numerical data. It does not introduce a physical
state or replace governor history. Further work remains on complete native-state
coordinate qualification, extended limit/event trajectories and integration
coverage. Existing stored-state access and the distinct source/typed deadband
conventions remain part of that work. This change does not classify the complete
model as `VERIFIED`.
