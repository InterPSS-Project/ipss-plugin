# Composite load partial-capacity actions

The bus-specific legacy composite exposes a source-owner capacity action because
its consuming leaves live on expanded internal buses. The parent bus's ordinary
dynamic-load list is not a complete list of those leaves. Actions must resolve
the original source load ID against `getTargetLoadIds()` and call the owner once;
matching internal suffixes or shedding a phantom parent static load is incorrect.

`Cmldblu1Model.canChangeLoad(sourceLoadId, factor)` accepts an exact singleton
source ID or a whole-owner `*`/`#` target. The factor is an increment relative to
initial capacity: two `-0.25` actions leave 50%, rather than 56.25%. A selected
individual ID within an aggregated owner is rejected because its separate
capacity allocation is not retained. Positive restoration, nonfinite factors
and zero/negative remaining capacity are also rejected before mutation. The
whole-owner `changeLoad(factor)` applies only a successful prepared action.

Independent matched shed/no-shed captures establish these physical changes:

| Quantity | Change for remaining capacity ratio r |
| --- | --- |
| Static/electronic allocated P and Q | Multiply by r |
| Internal feeder R and X | Divide by r |
| Near and far compensation susceptance | Multiply by r |
| Transformer impedance and tap | Unchanged |
| Substation susceptance | Unchanged |

The implementation updates both branch/shunt objects and their current matrix
stamps; subsequent matrix construction therefore retains the same physical
network. Numerical compensation pairs remain paired: they are not additional
physical shunts. Expanded motors and algebraic leaves receive the same initial-
capacity decrement, preserving each leaf's own states and protection history.
The prepared leaf array is reused, and both integration methods use this owner.

Four independent captures comprise two cold repetitions of a 25% action and a
matched zero-action control. Each pair matches exactly across all 1,006 samples,
including startup and event-side rows. Reconstructing internal voltages from
external network phasors, branch currents and the recorded distribution data
supports fixed transformer/substation values and scaled feeder/compensation;
scaling the transformer or substation instead is distinguishably incorrect.
The retained archive SHA-256 is
`09a82dd77febe6dceb050ef837b24da9f82c3def9a80d5ac726d947a22feadf9`.

`CompositeLoadChangeTest` first reproduced the unsupported owner action under
both methods. It now checks repeated partial actions, physical element scaling,
rejected-action nonmutation, motor online fractions and endpoint current balance,
as well as all-sample independent reference ratios. Complete relay-triggered
trajectory qualification is separate. Full shedding needs independently verified
disconnection/topology handling; no infinite feeder impedance or artificial
small admittance is substituted for that missing behavior. This is not a claim
of complete composite-model or zone-dialect qualification.
