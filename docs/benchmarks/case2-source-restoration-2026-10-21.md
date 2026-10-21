# Case 2 source restoration

The fast main-source configuration has been restored on the saved performance
branches in core and plugin. A build from 1,834 core sources and 882 plugin
sources reproduces the retained Case 2 recording byte for byte, without frozen
main classes or engine/plugin JAR fallbacks on its runtime classpath.

After 32 warmups, seven complete-case measurements were:
7.615716, 7.524497, 7.770296, 8.052078, 7.578641, 7.619469 and 7.712807 seconds.
The median is **7.619469 seconds**. Six of seven runs were below eight seconds;
the maximum was 8.052078 seconds. These include fresh case preparation,
initialization and full recording, on one verified logical processor, at `1e-5`
voltage-correction tolerance and a `1/240` second step, for ten simulated seconds.

Two preliminary complete audit runs and all timing repetitions reproduce the
canonical full-recording hash:
`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`.
The 2,405 samples and 4,810 channels retain event-side duplicates. This establishes
Case 2 observation equivalence, not independent-reference accuracy or internal
state equivalence for the reconstructed source build.

## What was recovered

The reconstruction combines the historical core base, retained direct-controller
and network source variants, and the current machine frame/endpoint preparation
implementation. It restores the fused network voltage-convergence path, cached
iteration participants, ordinary solver workspace reuse and direct ST1/ST4/PSS2A
evaluation. The benchmark retains ordinary current transfer and disables cached
current-array traversal, matching the selected fast executable.

The first source attempt selected two older load-flow files and produced a
different trajectory. Comparing method instructions located the mismatch;
restoring the later load-flow sources recovered exact recordings. That failed
attempt is retained and excluded from qualification.

Clean compilation also found an unused buffer path referring to a two-argument
sparse-solve overload absent from the archived solver. The overload was recovered
from the historical base; it validates dimensions, factors when necessary, then
delegates to the existing one-argument method. Three unused compiled-controller
helper classes from the newer implementation could not compile against the older
controller APIs and are absent from the recovered configuration.

## Validation and remaining work

The main modules and benchmark compile from source. Sixteen focused machine-frame
and sparse-solver tests pass. An independent extraction and clean build of the
source-only archive produced 3,868 class files byte-identical to the timed build.
The restored working sources were also checked against that source set; four
files with whitespace-only cleanup were recompiled and their classes matched.
**The current full core test sources do not yet
compile against this rollback**, because newer tests depend on the removed
compiled-stage and indexed-voltage APIs. An initial test compile also lacked
Mockito; the repeat added the declared Mockito dependencies and still exposed
the API incompatibilities. No tests were deleted or weakened to report a pass.

The branch is therefore a source checkpoint awaiting regression qualification,
not a production-ready release. Required follow-up includes reconciling the
newer API/test contracts, running applicable system gates and internal-state
comparisons, and checking repeatability of the eight-second limit. Independent
reference accuracy remains unresolved. The active performance goal is not
declared complete.

## Rebuild and recovery

```powershell
python tools/run_bounded.py --log target/recovered-source-build.log -- python tools/build_case2_source_checkpoint.py --output target/recovered-source-build
```

Use a new output name on each build. The source-only archive contains the entire
recovered main-source configuration and benchmark sources; the builder verifies
its hashes, verifies external Maven dependencies and compiles all three source
groups. It does not load frozen engine/plugin classes. Build results are written
under the requested target directory. The archived benchmark uses 32 warmups and
seven measurements; the build command itself does not run the simulation.

Both pre-restoration stashes and backup branches remain intact. See the
[recovery record](checkpoints/case2-pre-fast-selection-2026-10-21.json) and
[source checkpoint](checkpoints/case2-recovered-source-2026-10-21.json) for exact
identities, source counts, raw timing evidence and archive digests.
