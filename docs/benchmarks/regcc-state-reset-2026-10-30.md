# REGCC active PI state reset investigation

The evidence points to a **wrong-state/history reference in the active-current
PI clipping branch** of the reference implementation. This is an inference from
controlled tests, not a confirmed private source-code diagnosis. No reviewed
diagram or public implementation specifies copying the reactive PI history into
the active PI state. The InterPSS runtime is unchanged by this investigation.

This follows the [local-integration and PI-limit audit](regcc-substeps-2026-10-30.md).

## What the diagrams and references specify

The installed exact-model diagram labels separate reactive and active PI
coordinates, `s0` and `s1`. Their errors come from their respective command and
measured-current components. The source-voltage calculation and PLL couple the
axes through physical feedback. No reset connection joins the two PI histories.

The [EPRI current-limit memorandum, hosted by WECC](https://www.wecc.org/sites/default/files/documents/meeting/2024/Memo%20on%20EPRI%27s%20method%20for%20implementation%20of%20current%20limit%20logic%20in%20REGC_B_C.pdf)
limits network current according to P/Q priority, recalculates equivalent source
voltage and iterates the network solution. It distinguishes those algebraic
voltage variables from differential lag states. Its algorithm does not copy
one current PI integral into the other.

The pinned public [Dynawo REGCc implementation](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/dynawo/sources/Models/Modelica/Dynawo/Electrical/Controls/WECC/REGC/REGCc.mo)
wires separate PI instances for the two current errors. Its
[anti-windup block](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/dynawo/sources/Models/Modelica/Dynawo/NonElectrical/Blocks/Continuous/BaseClasses/BasePIAntiWindup.mo)
feeds each instance's limited-minus-unlimited output back to that same
integrator. That dialect differs from the installed model's measured behavior;
it supplies architectural evidence, not a substitute trajectory oracle.

The installed release notes mention documentation-check and drive-train
initialization corrections for this model. They do not describe this particular
clipping/reset behavior. Exact document/library provenance stays in the private
evidence manifest. A second executable release was unavailable; the older local
folders contain libraries rather than a complete independently runnable engine.

## Isolating the clipping branch

Thirty-eight new discriminator runs form nineteen cold pairs. Each retains all
nine states, ten variables, terminal P/Q and voltage/angle at all three buses.
Paired inputs and complete CSV hashes agree. All advanced direct state-API
readbacks agree with the corresponding recorded nine-state vectors. This rules
out CSV conversion or swapped output-channel labels as the explanation.

The state-space interventions below occur at 0.05 s without a fault or upstream
electrical controller. `Imax=0.26`. State and associated integration memory are
set consistently unless the row explicitly identifies a memory discriminator.
The first advanced endpoint is at approximately 0.0505 s.

| Entered reactive integral | Entered active integral | Active integral after first step | Observation |
|---:|---:|---:|---|
| 0.01 | +0.39 | 0.01 | Upper overshoot copies reactive history |
| 0.13 | +0.39 | 0.13 | Reset follows the independently selected reactive value |
| 0.13 | -0.39 | 0.13 | Lower overshoot has the same cross-coordinate result |
| 0.13 | +0.26 | +0.26 | Exactly at the limit, no reset with zero initial active error |
| 0.13 | +0.259 | +0.259 | Inside the limit, no reset |

The exact-boundary and inside controls also pass without an intervening zero-time
run. An empty zero-time call leaves the injected state unchanged; clipping and
the reset occur when integration advances. It is therefore important to
distinguish **requiring clipping** from simply being numerically on a bound.

Two integration-memory controls narrow the mechanism further:

- With active state 0.26 and active memory deliberately set to 0.11, the next
  active state is 0.11, as expected when this artificial intervention selects a
  different integration history.
- With active state 0.39, active memory 0.11 and reactive history 0.13, the next
  active state is 0.13. The clipping branch selects the reactive history instead
  of retaining the deliberately distinct active history.

Both P and Q priority reproduce the overshoot reset. Reordering the dynamic
records leaves the control recording identical, but also leaves the allocated
state indices unchanged. That test does not establish behavior at a different
global state offset or in a multiple-converter case.

The diagnostic state/memory edits are not physical disturbances. The natural
fault controls below establish that this behavior also occurs without such edits.

## Natural faults, with no state or memory intervention

Both profiles use fixed current commands, the ordinary 0.5 ms reference grid and
a three-cycle fault from 0.05 to 0.10 s. Only the initial reactive-power operating
point differs. No run boundary or scheduled event occurs at the reset time.

| Initial generator Q | Reset time (s) | Active integral before | Reactive integral before | Active integral after |
|---:|---:|---:|---:|---:|
| -5 Mvar | 0.066500 | 0.260000 | -0.07500478 | -0.07500478 |
| +8 Mvar | 0.070500 | 0.260000 | +0.06071900 | +0.06071900 |

The equality is with the **preceding** reactive value, not the concurrently
updated reactive state. For example, the negative-Q run's reactive state is
-0.07572077 after the step, while the active state is -0.07500478.

The observed active-state jumps are 0.335005 and 0.199281 pu. Given the recorded
active command, current magnitude limit, `Kii=5` and 0.5 ms step, an ordinary
integral update is bounded by approximately 0.001253 and 0.001241 pu. The jumps
are respectively 267 and 161 times those bounds. The PLL angle changes only
0.000144 and -0.00000152 rad; these are not quarter-turn frame transformations.

## Conclusion and implementation decision

The controls localize the anomalous assignment to handling active-integral
clipping/history. They do not reveal whether the private implementation reads
the wrong state element, the wrong integration-memory element or a shared
temporary variable. A wrong index or copied saturation branch is the likely
explanation; vendor confirmation is needed to identify the exact internal cause.

InterPSS should retain separate PI histories and the qualified bounds. This
investigation adds no compatibility rule that copies one axis into the other.
The affected trajectory remains an explicit reference discrepancy, rather than
a complete model acceptance or a reason to change unrelated model equations.

## Validation and retained evidence

`tools/check_regcc_cross_axis_artifacts.py` audits all nineteen pairs, the direct
state readbacks, natural reset identities, derivative bounds and priority/memory
controls. Two additional standard fault runs preserve the older complete CSV
byte for byte, confirming the new optional probe arguments do not change its
default trajectory. That gives forty new native runs in total.

No production Java or model equations changed; the full reactor was not rerun
for this diagnostic-only slice. The preceding implementation checkpoint records
its forty focused checks and full 4,282-test run with four existing failures.

The [evidence checkpoint](checkpoints/regcc-state-reset-2026-10-30.json) retains
all raw output, inputs, exact per-run probes, direct API snapshots, source
documents, audit scripts and hashes. The suggested vendor reproducer is the
no-edit negative-Q fault: inspect converter state `K+1` at 0.0660/0.0665 s and
compare it with state `K` at the preceding sample.
