# Typed hybrid-controller runtime composition

## Scope and acceptance boundary

`HplntdCoordinator.attach` composes one bus-owned HPLNTDU kernel with multiple
REAXDU lag ports on existing REGCA1/REECA1 devices. It preflights the entire
group before attaching any port, sorts device identities, gathers initialization
from every unit, and advances the plant and complete lag vector exactly once
per predictor/corrector stage. Later unit callbacks consume the published
commands without integrating the shared group again.

This is a **bounded typed runtime**, not main-importer support or exhaustive
trajectory verification. No catalog entry or native-name alias is added.
Approved-family loadable coverage remains **26/27**. The exact wrapper decoder
and immutable binding plan from the preceding slice are still separate from
the main dynamic importer.

Supported hosts currently require active PV generators with positive MVA,
REGCA1/REECA1, staged modified Euler, reactive-power reference input, default
relative links, nonnegative sharing, enabled positive-integral plant loops,
a valid monitored branch, and disabled automatic shunt switching. Conflicting
plant/wind/weak-grid ownership and two-step converter integration reject early.
The implementation does not replace an existing controller to make a case load.

## Runtime invariants

- Stage-zero samples live participation and rating weights once; both stages
  use that membership snapshot. Duplicate, incomplete, mismatched-step or
  mid-stage membership callbacks fail explicitly.
- Zero-time observations never advance the plant or lag states.
- Unit trips reweight live participation without changing initial-reference
  coordinates. Ignored unit commands hold while the unused lag states continue
  from their held inputs. Rejoining units use the live group again.
- Whole-group reinitialization gathers new unit seeds and resets shared clock
  and integration history. Partial initialization cannot advance.
- Named-state observation exposes thirteen plant and two states per unit in
  immutable maps. Converter-wide monitoring propagation remains follow-up work.

## Small-testbed evidence

The deterministic four-bus case has two renewable units rated 50 and 100 MVA
and a synchronous infinite-bus equivalent. Its topology is reconstructed from
the committed RAW fixture, not an external working directory. The existing
frozen native command profiles supply exact controller data and independent
reference channels. Source contracts and provenance are unchanged from the
[kernel evidence](hplntd-kernel-2026-10-18.md).

Eight new tests cover flat equilibrium at 0.5/0.25 ms, once-per-stage counts,
reversed bus/binding order, duplicate and incomplete callbacks, ownership,
trip/rejoin command behavior, reinitialization, native command comparison and
a three-cycle fault at Bus 2. The fault uses j0.1 pu impedance from 0.05 through
0.10 s; it proves finite coupled response and bounded timestep sensitivity,
**not independent fault-trajectory acceptance**.

The command comparison retains every reference row, including negative-time
history and duplicate event samples. All four bus voltages, both units' P/Q,
thirteen plant states and four downstream states have separate declared bounds.
Two generated paired traces contain 405 and 805 data rows respectively.

Observed maximum voltage error is 3.02e-5 pu at 0.5 ms and 1.46e-5 pu at
0.25 ms. Maximum unit active-power error is 4.50e-4 and 2.25e-4 pu on system
base. Downstream-state errors approximately halve with timestep.

The two reference-slew states are a deliberate integration-method exception:
their time constants are 2h, so reducing h does not reduce h/T. Modified Euler
and the reference tool's STORE rule therefore retain different short event
transients (maximum active-reference-state error about 0.00553 pu). This is
not hidden by dropping rows or changing the default integrator. Every coupled
step also checks the exact independent rate-limited Heun recurrence; the prior
kernel suite separately checks the reference tool's scalar STORE recurrence.
These bounded cross-method residuals are not an exact matched-method claim.

## Remaining work

Final gates against the unchanged frozen companion jar: **52 focused hybrid,
379 plugin-core unit, and 1,480 selected SMIB/model/solver tests**, zero failures
or errors. Six existing optional unit skips and one existing diagnostic skip
remain. The wording guard checks 218 documents/new Java files without violations;
all six guard regression tests pass. Source snapshots, test reports, diagnostic
runs and paired traces are retained under private verification artifact
`2026-10-18-hplntd-typed-runtime`, with a hash inventory. Prior sealed evidence
is checked for immutability before sealing this slice.
The final Java baseline includes concurrent recovery commit `81610d44f`; its
three added controller regressions are included in the final selected gate.

- Connect exact bus/unit wrappers and the ownership plan to the main importer,
  respecting removed-generator records and preserving import-order independence.
- Propagate group states through standard device monitoring.
- Independently verify coupled fault trajectories, full lifecycle handling and
  supported alternate hosts before extending acceptance.
- Implement disabled/zero-integral control modes, ignored/branchless groups,
  arbitrary valid relative-variable mappings, initially inactive units, all-off
  restart and automatic shunt switching without silently approximating them.
- Retain the explicit-base pause counterexample; this typed composition does not
  reproduce that reference-tool allocator reset.

No full Texas2k dynamic simulation is part of this slice.
