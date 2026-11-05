# NR reuse capabilities and sparse-interface qualification

Core commit `213be7a897d54767ea6b158136cedb094b5c5a68` separates structural preparation reuse, unchanged model-
input ownership and delayed voltage publication. Ordinary rectangular NR may
own audited coefficient inputs while publishing every correction. Polar and
rectangular equations, load derivatives, controls, numerical Jacobian/LU policy
and the common Newton engine remain shared. The overall consolidation and
performance requirements remain open.

## Accepted checkpoint and final cleanup

The user accepted the latest measured performance on 2026-10-31 and requested
cleanup and final commits. The measurements below remain the accepted baseline,
including IEEE118 at 1.267 times RustPower's pooled mean. Acceptance does not
change the recorded mathematical <=1.2 result or close the broader consolidation
backlog.

Final cleanup shares repeated device fixtures and voltage assertions, adds bus
IDs to numerical assertion failures, makes imports explicit and ensures the
fixed-operator test closes its owned scope even after an assertion failure.
All 685 focused checks pass again with exactly the same executed identities;
the nine benchmark-tooling checks pass. All 108 production runtime entries match
the measured runtime byte for byte. Production solver source is unchanged, so
the full 8,075-identity run below remains the full-regression evidence; it was
not rerun for test-fixture and documentation cleanup. Benchmark samples and
earlier source variants remain retained.

## Capability contract and compatibility

`NrReuseCapabilities` describes admitted preparation and the currently owned
scope. It is an observation, not a replacement for matrix/model validation or
permission to reuse a numerical Jacobian at a different voltage. Current powers
and voltage-dependent load derivatives still require evaluation. After scope
exit only structural preparation may remain eligible.

| Workflow | Structure | Coefficient-input scope | Unpublished voltages |
| --- | --- | --- | --- |
| Audited ordinary polar constant-power model | Yes | Yes | When publication constraints permit |
| Audited ordinary rectangular model | Yes | Yes | No |
| Audited polar constant-current/impedance model | Yes | Yes | No |
| Observed model, or switched-shunt/SVC/fixed-bank bus | May retain supported structure | No | No |
| Generic mode or unsupported reuse contract | No retained preparation | No | No |

The existing `isCapacitor()` exclusion already prevents owned solve scopes for
switched-shunt and SVC buses; it is preserved. A new device-type check prevents
opaque switched-shunt/SVC implementations from inheriting a built-in retained-
input snapshot contract. Same-value replacement and zero-B insertion are
detected even with notifications suppressed. Known empty buses skip device-list
admission work, and the new zero-B insertion tests protect that shortcut.

AUTO evaluates unsupported providers freshly; strict CACHED names and rejects
the missing contract. Restoring an audited model allows recovery. Initialization
converts the tested ZIP provider into a function-load provider, so its AUTO and
strict-cache cases verify fresh fallback/rejection and generic recovery.

The new three-argument `beginNrExecution()` separates coefficient ownership from
delayed publication. Its default bridge preserves the former boolean delivered
to legacy two-argument hooks. A default-solver subclass must explicitly implement
a newer contract; it does not inherit audited preparation merely through Java
inheritance. Existing virtual observation hooks remain compatible. The fixed-CSC
compatibility adapter declares its own copied-input and private-voltage ownership.
An existing lifecycle test now expects stable rectangular inputs through a bridge
that forwards the new overload, while still requiring public rectangular voltages.
Separate tests retain the old flag for legacy two-argument bridges.

## Full regression and sparse requirement closure

Full run `nr-scope-capabilities-full-20261031` executes **8,075 identities:
7,968 pass, 91 skip, 11 fail and five error**. All **685 required checks pass**.
There are 74 additions, no missing/newly failing/skipped identities, no changed
baseline failure diagnostics, no fork dumps and no Java source drift. The same
16 existing failures/errors remain. Full-tested and measured production
classpaths are identical across all 108 entries. Nine benchmark Python tests pass.

The 71 capability cases cover both coordinates, modes, publication permissions,
constant P/I/Z and fresh ZIP providers, observer coefficient mutations, legacy
hooks, subclass isolation, shunt/SVC replacement, zero-B insertion and recovery.
Three real sparse cases cover two deliberately equal-hash patterns and prepared
dimension growth with fresh RHS. Twenty-three existing sparse cases are newly
mapped as required evidence; they are not counted as newly added tests.

TC-09 is verified by the [sparse-interface closure record](../nr-sparse-interface-closure.json).
It maps each required clause to passing identities and assertions: CSC/row
publication, inspection/RHS, zeros, dimensions, symbol transfers, collisions,
failure recovery and backend/block-only parity. The real 66-equation adversary
uses column-zero rows `[0,1,64]` versus `[0,2,33]`; both have the same row-array
hash and size, but require a new analysis and independently known solutions.
Earlier complex-solver collision tests were already present; the prior terminal
inventory's open collision item concerned ordinary NR's real factor cache.
Global release closure and the other coverage requirements remain open.

The retained failed attempts include initial generic fixture compile errors,
ZIP reuse expectations corrected to the existing fresh-provider contract, and
an incorrect test expectation that known shunt/SVC buses would own input scopes.
The latter expectation was corrected to the preserved capacitor exclusion;
the exclusion was not relaxed to make those cases pass.

## Matched timing and direct external comparison

The final before/after batch uses identical inputs/settings and one compiled
maintained-API driver, in before/after/after/before order, on CPU 2 with verified
affinity mask 4 and a 2 GiB heap. IEEE118 uses 50,000 total warmups and 10,000
measurements per process; PEGASE9241 and 25k use 50/100. Initialization counts
within warmup. All 40,800 samples remain; final voltages are byte-identical and
independent original-Ybus residual/reference voltage checks pass.

| Case | Before mean ms | After mean ms | Mean change |
| --- | ---: | ---: | ---: |
| case118 | 0.03933794 | 0.04053730 | +3.05% |
| case9241pegase | 14.13173900 | 14.15692250 | +0.18% |
| activsg25k | 47.39954150 | 47.86980300 | +0.99% |

These results retain measured overhead rather than claiming a performance
improvement. Two earlier source-variant batches remain separately archived;
their samples are not pooled with the final source. No unchanged batch was
repeated to seek a passing result. Builds/profiles do not overlap timing.

Separate one-warmup/one-measurement 25k samples are
**[108.0531, 107.1037] ms before** and
**[109.2794, 112.5024] ms after**. Historical 86-90 ms
startup recovery remains incomplete. This is separate from warmed qualification.

The direct RustPower comparison uses the full-tested runtime in three fresh
processes per implementation, with the required separate small/large cohorts
and alternating order by fork. Counts/settings match the warmed protocol above,
with 50/100 also for 70k. All **121,800 measured samples** remain. The ratio is
InterPSS/RustPower pooled mean time, with <=1.2 required on every case.

| Case | RustPower ms | InterPSS cached ms | Ratio | <=1.2 |
| --- | ---: | ---: | ---: | --- |
| case39 | 0.01247323 | 0.01454164 | 1.166 | Pass |
| case118 | 0.03005917 | 0.03807447 | 1.267 | Fail |
| case9241pegase | 12.69554633 | 13.90743167 | 1.095 | Pass |
| activsg25k | 40.73158100 | 47.40890367 | 1.164 | Pass |
| activsg70k | 221.13013500 | 210.19527367 | 0.951 | Pass |

The all-five target remains unmet: case118 exceeds 1.2. Changes in ratios from
older batches are not attributed to this implementation. Inputs, fixed-control
canonical nodal models and qualified starts are unchanged: 25k uses compensated
flat start; 70k uses saved-state restart with required PV/reference setpoints.
Both use tolerance 1e-6 and a 20-iteration limit. This compares maintained nodal
solves with their existing iteration policies, not complete controllable-device
models. Accuracy, positive CACHED/exact-factor/owned-completion coverage,
complete repeat identities, actual factorization work, hashes and process
affinity pass the audit. Heap limits and memory-counter scope remain those of
the [accepted exact-factor contract](nr-exact-factor-reuse-2026-10-31.md).

The [checkpoint](checkpoints/nr-scope-capabilities-2026-10-31.json) retains full
summaries and evidence hashes. Local evidence roots are
`ipss.test.plugin.core/target/nr-scope-capabilities-20261031/` and
`ipss.test.plugin.core/target/nr-scope-capabilities-rustpower-20261031/`.
Specialized contributor/model coefficient capabilities, remaining handoff and
interaction coverage, final generic/frozen comparisons, startup recovery and
all-five performance qualification remain follow-up items in the broader plan.
This accepted ordinary-scope checkpoint does not close the broader
MODE-06/CLEAN-06 specialized-contract work.
