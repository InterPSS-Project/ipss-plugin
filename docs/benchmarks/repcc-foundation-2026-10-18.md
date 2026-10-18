# REPCC native contract and equilibrium foundation

## Status

This slice implements typed REPCC parameters and algebraic equilibrium
initialization. It does **not** implement the dynamic kernel, network attachment,
DYR import handler or mechanically switched shunt operations. Runtime coverage
remains 25/27; REPCCU's family and HPLNTD still lack loadable implementations.
This report is a development checkpoint, not full model verification.

## Exact source boundary

The installed reference Model Library section 27.4, printed pages 913-925
(PDF pages 925-937), specifies the standard-native name `REPCC`: 35 mixed-type
ICONs, 57 CONs, thirteen states and 36 variables. The standard DYR record has
92 parameters, not a guessed user-model wrapper. Twelve character ICONs are
quoted: two circuit identifiers and ten shunt identifiers. The first circuit is ICON 3, the second ICON 12,
and shunt identifiers occupy ICONs 14,16,...,32 (zero-based).

The approved source list maps REPC_C to `REPCCU`. A separate native alias probe
did not attach that name in this installation. Preserve the source-list mapping,
but do not infer an alias or its allocation from the installed REPCC contract.

Source PDF SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Model library binary SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The main controller diagram was rendered and visually reviewed.

[PowerWorld REPC_C](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_C.htm)
is a separate modeling reference. Its model-base and measured-frequency rate
parameters are not extra native CONs. Keep simulator-specific validation and
time-constant corrections separate rather than silently combining dialects.

## Implemented foundation

`RepccData` preserves every CON in source order and typed branch/shunt
configuration. Ordered shunt slots and arrays are immutable. Signed first-bus
values retain receiving-end measurement intent. Invalid finite/time/bounds
inputs are rejected without rewriting them to fit a trace. Invalid shunt
threshold ordering is retained and exposed as a disabled switching condition,
not automatically reordered.

The typed helpers encode these documented distinctions:

- Feedforward disabled selects Pmin/Pmax for the active PI; enabled selects
  PImin/PImax.
- Power-factor control falls back to reactive-power control below, but not at,
  one percent absolute active power on machine base.
- Reference slew filters use twice the actual integration timestep.
- Electrical feedback and feedforward are separate flags, not one option.

`RepccInitialization` consumes resolved machine-base branch P/Q and current,
regulated complex voltage, and initial downstream active/reactive commands.
It computes voltage-compensation/droop, Q/PF references, active PI bias and all
thirteen equilibrium coordinates. Unused measurement storage stays zero.
Its named-state map and state arrays are immutable snapshots. Power-factor
initialization preserves reactive sign and uses absolute active power, including
charging operation. This foundation explicitly rejects proportional-only,
enabled-shunt, disabled-loop and PF-with-disabled-feedback initialization until
those paths have independent evidence. It rejects an infeasible equilibrium
outside configured reference, output, PI or PF bounds rather than clamping
the initial operating point or changing gains to make it stationary.

The initialization class does not resolve a network, inject current, advance a
state or claim to execute switching. Those remain implementation work.

## Cold reference evidence

Eight feasible SMIB profiles, each cold-regenerated twice at 0.5 ms, retain all
59 channels, 605 samples, exact inputs, raw output, logs and hashes. Every run
passes the native initialization/convergence check; paired inputs, scripts and
CSV outputs are byte-identical. The profiles cover:

- Line-drop-compensated voltage control.
- Reactive-power control.
- Feasible constant power-factor control.
- Nonzero lead time and droop compensation in separate profiles.
- Feedforward disabled and electrical feedback disabled separately.
- Missing branch with generator-power fallback.

The finite flat trajectories are reference health checks only. InterPSS
comparisons in this slice cover equilibrium and input mapping, not time-domain
trajectory reproduction. Negative-time history and both duplicate endpoint rows
are preserved, counted and checked for monotonic time ordering.

An initial PF profile with a positive-Q minimum PF of 0.9 was infeasible at its
approximately 0.89264 operating point. Keep its suspect-initialization log and
raw output as rejected evidence. A separately named profile uses a feasible
0.8 bound; it is not a fitted gain or replacement of the rejected artifact.
The source-name alias attempt also remains a separate rejected diagnostic.

The missing-branch discriminator also distinguishes internal feedback from plot
storage: the controller uses generator P/Q internally, while the unresolved
line-flow plot variables remain zero. The comparison derives the fallback input
from physical generator power, not from those inactive variables. This finding
corrected the test harness, not the published production equations.
Regulated voltage remains the independently selected remote-bus voltage; a
missing power-measurement branch does not force terminal-voltage regulation.
Do not reuse the hybrid plant controller's different fallback rule unchanged.

## Remaining work

- Implement and independently replay all thirteen differential states, each
  selector, limiter, reference ramp and zero-time bypass.
- Establish native proportional-only initialization, low-power PF fallback,
  initial-limit behavior, blocked-voltage freeze and recovery timer semantics.
- Implement shunt measurement, ordered dispatch, breaker delays, capacitor
  discharge/re-arm timers and network admittance updates.
- Add exact standard-native parsing, deferred non-overwriting controller
  attachment, measurement/base conversions and downstream Qcmd/Vcmd routing.
- Establish legacy source-name wrapper evidence separately.
- Compare full-solver flat, three-cycle fault, reference changes and lifecycle
  events at matched and reduced steps, including instantaneous event sides.

No full Texas2k dynamics are run or required for this scoped work. Its model
inventory remains in scope.

## Acceptance gates and source audit

Final gates pass with zero failures/errors: 50 focused checks, 325 plugin-core
tests (six optional skips), and 1,377 registered SMIB/model/solver checks (one
existing diagnostic skip). The focused gate includes `RepccDataTest` (seven
tests), `RepccNativeContractConformanceTest` (four tests), adjacent hybrid-plant
kernel/import tests, catalog consistency and the evidence inventory. The last
gate selects `DynamicModelSmibTestSuite` plus the supplemental catalog,
staggered solver, static-load voltage-curve and runtime-plan tests.

The audit matches 22 owned Java/resource files to the tested snapshot and
2,074 baseline Java/POM files to Git. It verifies all sixteen accepted native
runs, eight byte-identical pairs, both rejected diagnostics and the immutable
1,021-file, 1,079-file and 1,085-file earlier hybrid-plant archives. The largest
observed equilibrium-state residual is 4.37e-8 pu in compensated voltage;
individual state bounds retain declared-double versus native-float precision.
The private evidence archive is `2026-10-18-repcc-foundation`.

The source baseline is plugin `ec3581c50`, excluding unrelated uncommitted
workspace changes. Tests use the last accepted core jar, isolated from the
shared local dependency repository. Its SHA-256 is
`e302580561dfd6a4b6c203fe910bdd082aa1ee212f67e20334a36998781ba187`.
Every test report's classpath is checked for that isolated dependency path.
No source POM or production dependency version is changed for this isolation.
