# Latest core/plugin vs RustPower (2026-11-05)

The maintained public load-flow API, using cached polar NR and Java KLU,
was compared with RustPower 0.5.2 on all five established cases. **Four cases
meet the <=1.2 pooled-mean time ratio; IEEE118 remains above it at 1.329.**
The original all-five target is therefore not met.

| Case | RustPower mean ms | InterPSS cached NR mean ms | InterPSS / RustPower | <=1.2 |
| --- | ---: | ---: | ---: | --- |
| IEEE39 | 0.01344 | 0.01458 | 1.085 | Pass |
| IEEE118 | 0.02886 | 0.03837 | 1.329 | Fail |
| PEGASE9241 | 13.06634 | 14.06534 | 1.076 | Pass |
| 25K | 41.70488 | 46.65824 | 1.119 | Pass |
| 70K | 227.37426 | 207.40985 | 0.912 | Pass |

Ratios below one mean InterPSS was faster. These are warmed repeated-solve
measurements. They should not be compared directly with one-warmup/one-solve
startup measurements. In this batch, 25K takes five RustPower iterations and
six InterPSS iterations; iteration counts agree on the other cases.

## Sources and dependency alignment

- Core **1.4.9**, commit `e8eb86c8e7f4cc74e0b9a007422112f483854165`.
- Plugin **1.4.11**, measurement source head `95e038efe2b919676c2666b7f088ecf80c1508bb`.
- The plugin POM now sets `ipss.core.version` to **1.4.9**.

The source-reactor build compiled core and plugin together. Its test report
confirms the freshly built core directory is on the classpath, with no installed
core jar substituted. The build used the core 1.4.9 dependency override; the
tracked plugin POM was then aligned to that same effective value and Maven
validation passed. The plugin build began at `2a926edcca7233e420fbb690c821c084c323477e`;
the later measurement head adds a test and documentation. Production and
selected test source fingerprints were verified before timing; production
sources were verified again after timing. The retained POM patch identifies
the working-tree dependency change. Unrelated model-test edits were preserved.

## Protocol and qualification

Three fresh processes per implementation and case use alternating runtime order
on logical CPU 2, with verified affinity mask 4. JVM heaps are bounded at 2 GiB.
IEEE39 and IEEE118 use 50,000 total warmups and 10,000 measured solves per
process. PEGASE9241, 25K and 70K use 50 total warmups and 100 measured solves.
Java's initialization solve counts within the warmup total. Input preparation,
runner compilation and receipt hashing occur outside the solve timers.

The latest source build passes **689 focused checks**, including all **685
previously required NR checks**. A full regression run was not repeated for
this benchmark request. All **121,800 measured samples** and all **30
case/runtime/process groups** pass the result audit. Every repetition is present;
accuracy, actual CACHED selection, owned completion, exact initial-factor reuse,
actual factor work, source/runtime/input hashes and affinity checks pass.

The maximum independent InterPSS mismatch is **5.32034399e-08 pu**;
the maximum complex voltage error is **5.49587076e-09 pu**. These satisfy
the 1e-6 mismatch and 1e-7 voltage agreement gates. Both cohorts retain all
108 production classpath fingerprints and their per-file evidence hashes.

Large cases retain the previously qualified fixed-control nodal snapshots:
25K uses compensated flat start and 70K uses saved-state restart with required
PV/reference setpoints. Both runtimes use matching inputs/starts, tolerance
1e-6 and a 20-iteration limit. This qualifies nodal solves; controllable-device
and branch-flow model comparisons are outside this benchmark's scope.

The prior accepted checkpoint and this batch were measured separately.
Differences between their ratios do not establish a causal speedup or regression
from code changes. No timing batch was repeated to seek a passing ratio.
The first source build was superseded before timing when core's version changed;
its evidence remains retained.

The [machine-readable checkpoint](checkpoints/rustpower-latest-core-plugin-2026-11-05.json)
records settings, all case/per-fork summaries and evidence hashes. Raw evidence
is retained in `ipss.test.plugin.core/target/nr-rustpower-latest-results-20261105/`;
build/source qualification is in
`ipss.test.plugin.core/target/nr-rustpower-latest-20261105/`.
