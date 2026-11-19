# Terminal evaluation contracts and IEEE118 diagnostic

Core test commit `65039c29a83a2ea3d3552bcd990391e3dba341c5` adds 11 independent endpoint contracts. No production
optimization from this experiment is retained. The accepted factor-cache runtime
and its external benchmark status remain unchanged: four cases meet <=1.2 and
IEEE118 remains at 1.309. The full consolidation goal is still open.

## Retained coverage

Eight cases span polar/rectangular coordinates, full/reduced matrices and Java
KLU/CSJ publication. They combine internal endpoints, repeated external objects
and distinct external objects that share a matrix column but have different
voltages and equation masks. Current physical powers are checked independently
with complex network-current arithmetic. Preserved matrix entries are compared
against the frozen assembler after initial and mutated internal/external voltages.
The known full-rectangular PV-row correction remains covered by the separate
independent derivative tests rather than the frozen incorrect entries.

Two additional cases reuse the same ordinary plan while changing internal
voltage coordinates and independently verify current physical power. One custom
endpoint case checks repeated magnitude/angle getter order and the separately
captured values for each endpoint occurrence. An endpoint lookup must preserve
these calls without an explicit stronger model contract. Existing tests continue
to require AUTO fallback and strict CACHED rejection for opaque external ownership;
the new numerical external cases exercise fresh preparation.

The first candidate-focused attempt found a new fixture with missing network
ownership before its Complex voltage setter. The fixture was corrected; the
failed attempt is retained. The corrected candidate gate passed all 588 checks.
No production numerical correction was needed for that fixture.

## Measured candidate and rejection

A diagnostic JFR of 240,000 warmed IEEE118 public solves records 482
main-thread execution samples and locates work in terminal-voltage lookup,
factor/refactor operations, residuals and model admission. The diagnostic includes
voltage reset and JFR overhead; it is separate from acceptance timing. Operating-
system performance-counter warnings are retained, not used as attribution evidence.

The candidate moved external-voltage resolution into preparation, with direct
indices in the common power/Jacobian loops and no gather copy for internal
endpoints. It preserved repeated external getter calls and passed numerical tests.
The matched batch uses the same compiled driver, input, numerical settings and
CPU 2 affinity mask 4, in before/after/after/before order. IEEE118 uses 50,000
total warmups and 10,000 measured solves per process; the larger cases use 50/100.
The Java initialization solve counts within warmup. Builds and profiles do not
overlap this timing batch. All 40,800 measured samples remain.

| Case | Accepted mean ms | Candidate mean ms | Accepted median ms | Candidate median ms |
| --- | ---: | ---: | ---: | ---: |
| case118 | 0.03859469 | 0.04097971 | 0.03700000 | 0.03740000 |
| case9241pegase | 14.23751100 | 14.11415300 | 14.00590000 | 13.70345000 |
| activsg25k | 46.49677400 | 46.52471750 | 45.52930000 | 45.48425000 |

IEEE118's candidate mean is 6.18% higher and median about 1.08% higher. Its
16.4449 ms maximum remains in the mean. The experiment does not identify the
cause of that outlier. PEGASE9241's mean decreases 0.87%; 25k's mean increases
0.06%. These measurements do not establish a useful general speedup or close
the IEEE118 gap. Final voltages are byte-identical; independent residual/voltage,
cached execution, exact factor reuse, owned completion and runtime/affinity checks
pass. The production candidate was reverted, with both versions and every sample
preserved. It is not presented as an accepted optimization.

## Full regression and unchanged production runtime

Run `nr-terminal-contract-full-20261031` executes **8,001 identities: 7,894 pass,
91 skip, 11 fail and five error**. All **588 required checks pass**, including
the 11 additions. Against the 7,990-identity factor-cache baseline, no identity
is missing or newly failing/skipped, no baseline failure diagnostic changes,
and there are no fork dumps or Java source changes after capture. The same 16
existing failures/errors remain; failure-ignore Maven status is not an all-green
test certificate.

All 108 production classpath entries match the accepted external benchmark
runtime byte for byte after restoration. The new contract tests do not change
that executable. The [accepted external results](nr-exact-factor-reuse-2026-10-31.md)
therefore remain the performance evidence; no unchanged external batch is repeated
to seek a pass. They are separate from the rejected candidate measurements above.

## Open contracts

Structural reuse, coefficient reuse and delayed voltage publication still need
separate explicit capability contracts. This experiment does not close MODE-06
or the complete adapter-resolution item PLAN-12. Review also found that same-size
CSC pattern mutation tests do not yet construct deliberately colliding hashes;
the explicit adversarial equal-hash requirement remains open in TC-09. Complete
interaction coverage, generic/frozen comparisons and final performance/release
qualification also remain open.

Evidence is retained in `ipss.test.plugin.core/target/nr-capability-contracts-20261031/`,
the corresponding bounded logs and source snapshots. The
[machine-readable checkpoint](checkpoints/nr-terminal-evaluation-contracts-2026-10-31.json)
records the full audit, rejected measurements, source/runtime evidence and hashes.
