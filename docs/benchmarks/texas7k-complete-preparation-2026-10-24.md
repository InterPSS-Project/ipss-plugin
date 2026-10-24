# Texas7k complete preparation and flat-start validation

The unchanged supplied case now imports all **2,705 dynamic records**, solves
power flow, holds a ten-second equilibrium, and completes a five-cycle
three-phase bus fault using the Java sparse solver. BSG, GNET and scoped
model-removal preparation files are handled by the normal multi-file loader.

This supersedes the partial-case qualification in the
[earlier startup checkpoint](texas7k-controller-startup-2026-10-24.md).
It establishes the requested case preparation and simulation behavior; it is
not a claim of independent full-network trajectory parity or blanket model-family
verification.

## Preparation receipt

| Input or check | Verified result |
|---|---:|
| Bus records | 6,717 |
| Dynamic records attached | 2,705 |
| Rejected/error dynamic records | 0 |
| BSG entries | 120, fixed active-power response |
| GNET requested buses | 13 |
| Active generators converted by GNET | 5 across four buses |
| Other GNET targets | Nine already had no active generator |
| Scoped model-removal directives | 55, selectors 3 and 6 |
| Disabled attached controllers | 0 |

The five supplied input hashes still match the private source manifest.
Explicit netting creates negative loads from the generator injections; default
missing-model preparation additionally nets the two active units without base
dynamic models before dynamic load conversion. The confirmed constant-current
active and constant-impedance reactive load treatment is retained.

## Torque-record compatibility

Seventeen rejected records contain repeated or decreasing interior power
points; another uses selector 2. Independent operating-point references preserve
the original point order. Endpoint checks precede the interior intervals, and
collapsed intervals are skipped. Sorting the curve would change the equations.

Twenty operating points are each cold-regenerated twice. Selector 2 produces
byte-identical complete fault exports to selector 1 at both tested timesteps,
with two cold runs. The runtime therefore selects the power-error branch for
the independently observed nonzero selector. Startup validation reports the
unusual source values without rewriting them.

Finite numeric parsing, increasing endpoints, the all-zero default curve and
degenerate-curve rejection remain enforced. Malformed numeric fields no longer
silently become defaults. These checks do not qualify arbitrary curve shapes
or undocumented selector values.

## Generic auxiliary-controller hosts

The 13 REAX3BU1 and two REAX4BU1 records now attach to their actual
REGCA1/REECA1 hosts. Shared PLNTBU1 attachment works in either source order.
The generic host initializes absolute P/Q references; the earlier specialized
host's incremental reference API remains available.

Independent legacy and standard-record references confirm the interface.
System-base plant outputs are converted to the owning machine's MVA base
before the auxiliary gains. Cold tests on 50-, 100- and 200-MVA hosts detect and
guard this conversion. Worst recorded-input auxiliary-state differences are
9.88e-8 reactive and 1.09e-6 active. All samples and event-side duplicates remain
in the comparison.

One standard cold export differs in two inactive power-factor reference slots
by 1.36e-19; those slots are retained and explicitly scored. All other columns
in that legacy/standard comparison agree exactly. The original 200-MVA fault
run that exhausted its network iteration budget is retained as rejected
evidence. A separately named run with a larger iteration budget converges
without altering model constants or the fault.

Both auxiliary states and all seven shared plant coordinates expose their
stored derivative observations. The shared plant advances once per global
predictor/corrector stage. This generic-host path requires staged modified
Euler; unsupported single-stage or staggered-history calls fail explicitly.

## Equilibrium and disturbance gates

The first complete-stack run revealed growth from double-roundoff residuals
through the newly active plant loops. Control-error identities now use the
plant model's existing 1e-12 numerical-zero threshold before high-gain control.
A counterexample test confirms that 1e-9 physical inputs still produce a
response. No controller gains, source curves or physical time constants were
adjusted to obtain a flat run.

The permanent gate uses a 1/240 s network step, modified Euler, 0.001 pu
power-flow mismatch tolerance and **1e-4 pu dynamic-network voltage-correction
tolerance**. All buses and machine angle/speed are checked during the run;
9,496 controller coordinates are also compared between initial and final
snapshots.

| Ten-second quantity | Maximum drift |
|---|---:|
| Bus voltage | 2.77556e-14 pu |
| Machine speed | 1.33227e-15 pu |
| Rotor angle | 2.18250e-11 degrees |

Power flow reports a maximum reactive mismatch of approximately 5e-5 pu.
A three-phase fault at bus 111180 is applied at 0.1 s for five 60-Hz cycles;
the run completes beyond 0.5 s with finite bus and device states. This is a
disturbance execution/regression gate, not a wide-area reference-error claim.

## Validation and retained scope

The complete registered reactor passes **4,103 tests**, with 86 existing skips
and zero failures/errors. Module totals including skips are 442 core, 468
three-phase and 3,279 integration tests. All **408** offline checks pass.
The six-case short smokes, strict ALLSYM equilibrium gate and an additional
fifteen topology-selected fault cases pass as well.

Raw references, readbacks, source inventories, test reports, rejected
counterexamples and exact tool provenance are retained in the private
`verification-artifacts/2026-10-24-texas7k-complete` checkpoint. Separate
filter-state perturbation experiments are retained as diagnostics and are not
used to qualify the static torque-curve function.

Complete independent Texas7k disturbance trajectories remain unqualified.
The earlier EXPIC1 algebraic/output event-phase differences also remain open;
the stationary case and finite-fault gates do not resolve them.
