# Common NR lifecycle checkpoint

Core commit `bc81289c5` removes concrete `DefaultNrSolver` dispatch from ordinary
NR engine and algorithm lifecycle handling. `INrSolver` now owns observations
of stable model inputs, retained voltage state, cumulative preparation builds
and actual cached assembly use. Fresh and retained execution continue to use
the same polar and rectangular formulations.

`DefaultNrSolver` keeps virtual dispatch through its former observation getters,
so existing subclass overrides remain visible. Its private context and scope
operations use common lifecycle names. Public prepared scope methods remain
compatibility entry points. Legacy optional residual, correction and allocation
hooks are confined to `LegacyNrResidualAdapter`; ordinary engine code consumes
the common evaluated-residual, correction, allocation and lifecycle contracts.

The lifecycle observations are actual state, rather than declarations that a
model is cacheable. Returning true for retained voltages requires a solver to
own their evaluation/correction state and publish it at its observation and
scope-exit boundaries. Stable-input admission still requires the existing model,
observer, workflow and backend checks. This change does not widen model
eligibility, alter equations or change the numerical factor-reuse policy.
Separate contributor capabilities and the standalone API migration remain open.

## Correctness

Nine new test cases exercise a delegating solver bridge that is not a
`DefaultNrSolver`: both coordinates in all three modes, old observation
overrides, and failures after scope entry or correction followed by recovery.
Positive assertions verify retained voltage use during cached polar solves and
published voltages for rectangular/generic solves. Repeated public solves check
voltages, reported mode, build counts and linear-solve counts.

The full source reactor, `nr-lifecycle-full-20261030`, executes **7,878
identities: 7,771 pass, 91 skip, 11 fail and five error**. All **465 required
focused identities pass**. Relative to `nr-admission-full-20261030`, there are
ten additions, including nine lifecycle cases and one concurrent governor test.
There are no missing identities, new failures, new skips, changed baseline
failure diagnostics or fork dumps. All Java source files still match the frozen
compiled snapshot. The same 16 existing failures/errors remain; the full suite
is not wholly green.
The paired source manifests and patches also identify pre-existing unrelated
working-tree edits included in these compiled snapshots.

## Matched performance observations

The preceding compiled admission-fix runtime and this compiled lifecycle runtime
use the same benchmark-driver binary, Java KLU, logical CPU 2 with verified
affinity mask 4, and `-Xms256m -Xmx2g`. Source/runtime fingerprints, commands and
all samples are retained. No builds or intrusive profiling overlap timing.

Each cold observation is a fresh JVM with exactly one initialization warmup and
one measured solve. For each case, runtime order is before, after, after,
before. The separate warmed protocol uses 50 total warmups, including
initialization, and ten measured solves per runtime. Its runtime order is after,
before. The 25k case keeps its compensated flat start; 70k keeps its qualified
saved-state restart. Numerical settings and public solve boundaries match the
preceding benchmark: voltage reset, public load flow, voltage publication and
generation allocation are timed. Import, preparation, validation and fingerprint
collection are outside the timer; runtime JIT work remains included.

| Case/runtime | One-warmup samples, ms | Warmed mean, ms | Warmed median, ms | Warmed range, ms |
| --- | --- | ---: | ---: | --- |
| 25k before | 145.0644, 138.3339 | 55.12253 | 54.35770 | 52.9951–58.3459 |
| 25k after | 141.2923, 135.8024 | 56.20705 | 54.58780 | 52.6958–69.7593 |
| 70k before | 364.6392, 361.1613 | 251.75071 | 248.53755 | 244.9668–266.3618 |
| 70k after | 375.4490, 364.1485 | 244.03386 | 242.54205 | 239.0409–251.2251 |

The 25k warmed median changes by approximately 0.4%; its mean changes by
approximately 2.0%, with overlapping ranges. The 70k warmed median decreases
approximately 2.4%, while its two one-warmup observations are slightly slower.
These limited samples do not establish a general speedup or a persistent cold
regression. They are recorded as observations rather than a final performance
qualification.

There are 48 measured solves in total: eight cold and 40 warmed. Every measured solve
reports six Newton iterations. Before/after cache counters match within each
protocol, including one preparation and retained symbolic analysis. All final
voltage files are byte-identical across both runtimes and protocols for each
case. Independent original-Ybus checks give a largest residual of
`3.02793e-8` pu; all complex-voltage errors remain below `1e-7` pu. Runtime and
driver fingerprints remain unchanged through the timing batch.

No RustPower runtime is measured in this checkpoint. Final <=1.2 qualification
still requires matched RustPower measurements for IEEE39, IEEE118, PEGASE9241,
25k and 70k after the remaining consolidation work. Historical standalone
numerical API migration, specialized contributors and complete coverage closure
are also unfinished.

## Evidence

Local evidence is under
`ipss.test.plugin.core/target/nr-consolidation-lifecycle-20261030/`:
`full-test-audit.json` retains every test identity, diagnostic and report hash;
`performance/runs.json` retains commands, affinity observations, complete runtime
fingerprints, samples, counts, input/start hashes and accuracy checks. Individual
case folders keep the raw CSVs, mode reports and complete voltage outputs.

The source manifests and working-tree patches use the `nr-lifecycle-before` and
`nr-lifecycle-full` prefixes under
`ipss.test.plugin.core/target/nr-consolidation-20261030/`. Bounded build and
performance logs are retained alongside their status files. The
[machine-readable checkpoint](checkpoints/nr-consolidation-lifecycle-2026-10-30.json)
records this scope and evidence hashes. The [coverage manifest](../nr-consolidation-test-coverage.json)
continues to mark release coverage incomplete.
