# Case 6 prefault localization and complete physical observations

## Observation changes

The optional full-network recorder now binds its channel inventory after dynamic
initialization. Initialization can deactivate generators that have no dynamic
model and convert their injections into equivalent loads. Binding before that
conversion incorrectly rejected these otherwise runnable diagnostic cases.

The recorder also supports terminal active and reactive power for REGFMA1.
It evaluates current from stored source voltage, angle, impedance and current
limit without calling output methods that change electrical caches or limiter
flags. The existing bus and generator disconnection rules remain in place.
These changes affect diagnostic observations, not the integration equations.

Case 6 records 5,268 physical channels: voltage magnitude/angle at 2,000 buses,
terminal P/Q for 408 initialized generators, and four additional observations
for each of 113 synchronous machines. The 295 converters include all five
original grid-forming units. The 603 samples retain both cold-history points
and every 0.5-ms endpoint through 0.3 seconds.

Enabling the recorder leaves the original 40 selected physical channels exactly
unchanged. Refreshing to the complete committed production snapshot `d25edba56`
also reproduces all 5,268 physical channels and 603 samples exactly. State
observations have identical parsed values; their raw JSON member ordering
differs. Both the initial raw-order rejection and the numerical equality check
are retained. The final control and source hashes are in the private manifest.

## Wider supplied-reference comparison

The six supplied Case 6 disturbances share a common prefault history. Combining
only this common interval gives 191 distinct physical observations, with all
49 overlapping physical channels exactly equal. Each channel retains its
originating event and that event's full ten-second reference range. Responses
after fault application or a generator trip are not combined.

A preliminary audit of all auxiliary channels found four nonidentical unused
load-reference overlaps. That wider union is retained as a rejected control;
it is not used for the physical comparison. Controller replays separately
check agreement for every selected input, including field-current and
stabilizer-output channels.

| Observation | Largest absolute error through 0.3 s | Location/time |
|---|---:|---|
| Bus voltage | 0.0669425 pu | Bus 5083, 0.266 s |
| Rotor angle | 0.631768 degrees | Bus 5297, unit 1, 0.300 s |
| Terminal active power | 89.3161 MW | Bus 5302, unit 1, 0.300 s |
| Terminal reactive power | 118.234 Mvar | Bus 5302, unit 1, 0.266 s |
| Field voltage | 3.82487 pu | Bus 7170, unit 1, 0.300 s |

Bus 5083 starts within 1.14241e-6 pu of its supplied voltage. Bus 5297 starts
within 1.21429e-5 degrees of its supplied rotor angle. The later mismatch
therefore requires investigation of the evolving dynamics and their inputs;
a large initial power-flow voltage error does not explain these observations.

## Convergence control

One control changes only network convergence from 1e-4 to 1e-6. This comparison
uses the original five bus-voltage and five rotor channels, not the wider
191-channel inventory. Both runs end at 0.3 s and use the original 0.5-ms step.

| Network tolerance | Total iterations / 602 calls | Worst voltage NRMAE | Worst rotor absolute error |
|---|---:|---:|---:|
| 1e-4 | 7,556 | 0.0162466 | 0.538251 degrees |
| 1e-6 | 24,306 | 0.0141063 | 0.509595 degrees |

The tighter tolerance uses 3.22 times as many network iterations and leaves
all five voltage and all five rotor channels outside their prefault limits.
It is not adopted as a correction. Finite network iterations can also change
converter algebraic memory, so this is a numerical control rather than a proof
that the tighter result is the correct reference.

The separate Case 5 prefix also fails: worst voltage NRMAE is 0.00826114 and
worst rotor absolute error is 0.0377484 degrees through 0.3 s. These prefixes
do not establish complete prefault or ten-second acceptance for either case.

## Physical-input localization

Independent swing-equation replay uses the original inertia, zero damping and
stator resistance, and supplied mechanical/electrical powers for the five
machines in the original Case 6 bus-fault observation set. Starting from zero
speed deviation and zero angle increment, the supplied powers reconstruct
the supplied first-second angle increments within 0.000176318 degrees.
This increment check is diagnostic and does not replace absolute-angle scoring.

For bus 6166, using local electrical power produces about 24.49 degrees of
increment error. Combining local mechanical power with supplied electrical
power reduces this to 0.795318 degrees; combining supplied mechanical power
with local electrical power gives 25.2828 degrees. Electrical-power mismatch
dominates this particular angle divergence. Local-power replay is not exact
for every machine: two local output histories retain small endpoint residuals,
up to 0.007073 degrees, which remain separately recorded.

Exciters for all 23 observed synchronous machines are then replayed from their
supplied terminal voltage, field current, stabilizer output, speed and terminal
power. Original controller parameters are retained. Controller states evolve
independently using production state bindings, including the local ten-substep
ST1 recurrence. The largest current-endpoint field-voltage error over the full
prefault second is **3.94550e-5 pu**. Over the first 0.3 s it is 2.34502e-5 pu,
versus 3.82487 pu in the coupled run. Earlier held-input outputs are retained
as a separate diagnostic rather than substituted for the matching endpoint.

The same 23 stabilizers are replayed with their supplied speed and electrical
power. Their largest output error through one second is **7.81304e-6 pu**;
through 0.3 s it is 1.43509e-6 pu. No controller state, trajectory-dependent
gain, time shift or fitted offset is injected.

These results narrow the investigation toward machine/network inputs and
coupling. They do not prove that every controller in the complete fleet is
correct, nor establish a corrected coupled trajectory.

The original archive also supplies machine and controller state histories.
For all 23 observed round-rotor machines, the four cold electrical states agree
with the local initialized states within **2.69908e-5 pu**. All 42 repeated
machine-state channels agree exactly across their common prefault intervals.
The native model's published six-slot order is retained with the source hash.

Combining those native internal states with supplied terminal P/Q and voltage
magnitude permits an independent diagnostic reconstruction of terminal voltage
phase. The stator relation reproduces the measured voltage magnitude within
**2.46822e-7 pu** through one second. Comparing the inferred phase with the
local solution gives a **4.17891-degree** difference at bus 5297 by 0.3 s,
versus 0.000184134 degrees at zero. This is an inferred physical boundary,
not a newly supplied direct bus-angle channel or an additional acceptance gate.
It provides a concrete boundary for the next machine-equation replay.

## Validation and remaining work

All 84 focused observation, state-access, model and integration tests pass
against the complete committed production snapshot `d25edba56`, with no failures
or skips. A preceding run against the prior controller snapshot also passes
84 tests. New guards cover delayed inventory binding, conversion of an
unmodeled generator, grid-forming current limiting, unchanged stored states
and limiter flags, repeated event timestamps and disconnection reporting.
The observation test class was already registered in the core suite; a fresh
execution of that entire suite is not claimed here.

Two source units in Cases 3–6 have no generator/converter model in the supplied
DYR files: bus 5045/unit 1 and bus 7099/unit 2. Their handling in the original
reference preparation still needs clarification. The existing local conversion
behavior is preserved; no inferred replacement or shutdown is applied.

Inputs, raw outputs, source snapshots, replay programs, comparisons, rejected
controls and hashes are retained in the private evidence set
`2026-10-19-case6-prefault-localization-and-observations`. The numerical runtime
used for these diagnostic controls is not evidence for the runtime target.

All six cases and all 36 disturbances remain required. Voltage/frequency NRMAE
limits remain 0.001 prefault and 0.005 from fault application, normalized by the
full ten-second reference range. Rotor limits remain 0.02 degrees maximum
prefault and 0.2 degrees ten-second RMSE. Direct bus-frequency agreement,
complete coupled accuracy and the 20-second runtime target remain unproved.
