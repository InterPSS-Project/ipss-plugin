# Large-case NR benchmark: one warmup and one measured solve

With exactly one warmup and one measured solve per case and runtime, maintained prepared NR misses the InterPSS / RustPower <= 1.2 runtime-ratio target for both cases. All accuracy and prepared-path checks pass. These are single observations, with no timing samples removed or extra large-case trials run.

| Case | Start | RustPower ms | Prepared NR ms | Ratio | <= 1.2 |
| --- | --- | ---: | ---: | ---: | --- |
| activsg25k | Compensated flat | 43.2994 | 88.6434 | 2.047 | Fail |
| activsg70k | Saved-state restart | 224.2313 | 373.9693 | 1.668 | Fail |

The [earlier warmed batch](rustpower-large-cases-2026-10-30.md) passed with ratios 1.085 and 1.047. That batch used 50 loop warmups plus a Java initialization solve and 100 measured solves per case/process, across three forks. This requested run measures performance with minimal warmup. It uses a fresh Python process and JVM for each case, so 70k does not benefit from previously solving 25k in the same JVM. These different protocols do not contradict one another. The timing difference is not attributed to a particular subsystem without profiling.

## Exact solve budget and timed boundary

There is one fresh process per case and runtime, pinned to logical CPU 2 (mask 0x4). Each executes one untimed warmup and one measured solve: four measured large-case solves and eight total large-case solves. The Java driver normally performs an initialization solve before its warmup loop; the new --initial-solve-is-warmup switch counts that required initialization as the only warmup, then executes the single measured loadflow(false). It does not perform an additional untimed solve. Diagnostics record loadflowCalls=2, warmupLoadflowCalls=1, measuredLoadflowCalls=1 and initialSolveCountedAsWarmup=1 for each case. RustPower validation records one warmup and one measured solve.

Each timed solve resets the same qualified initial voltage used in the earlier batch. The 25k start is compensated flat. The 70k start uses supplied saved angles and PQ voltage magnitudes with the required PV/reference setpoints; the earlier 70k flat-start failure remains. RustPower reports five iterations for 25k and six for 70k; prepared NR reports six for each. Both use 1e-6 pu tolerance and max 20 iterations, with the previous fixed-control canonical numerical models unchanged.

RustPower timing covers voltage reset, solve and calculated-power retrieval. InterPSS timing covers voltage reset and maintained public loadflow(false), including voltage publication and canonical-model generator allocation. Import/context or plan preparation, process launch and compilation are outside the measured solve. This is a minimally warmed solve measurement; it excludes full application startup. Java heap remains bounded at 2 GiB, and residual runtime compilation during the measured solve is retained.

## Verification

- Input/model/initial-voltage hashes, Python package versions and every Java runtime fingerprint match the preceding large-case batch.
- All snapshotted production numerical sources match the previously verified source build. This task changes benchmark accounting and reporting only.
- Every measured solve converges; independent maximum InterPSS residual is 3.02793e-8 pu and complex-voltage error is 5.49590e-9 pu. Maximum RustPower residual is 1.02732e-8 pu and voltage error is 9.94230e-10 pu.
- Both prepared diagnostics show one plan build, two workspace/solve scopes and retained symbolic analysis. File-hash, repeat-count and CPU-affinity audits pass.
- The existing five focused benchmark tooling tests pass. The neutral-language check is run before delivery.
- Each benchmark child returns code 2 for a missed performance target. The bounded orchestrator records these results and completes successfully; no solver/accuracy failure is hidden by that status.

## Reproduction

Run separately for activsg25k and activsg70k, each with a new output directory and bounded log:

```powershell
python tools/run_bounded.py --log <new-bounded-log> -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --output <new-case-output> --test-report .verification-build/prepared-nr-rustpower-recheck-20261030/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.aclf.PreparedNewtonLoadflowTest.xml --cases <case> --case-directory ipss.test.plugin.core/target/rustpower-large-cases-20261030/inputs --cpu 2 --forks 1 --small-warmup 1 --small-repeats 1 --large-warmup 1 --large-repeats 1 --initial-solve-is-warmup
```

Evidence: ipss.test.plugin.core/target/rustpower-large-single-warmup-20261030/ contains both case directories, exact commands and return codes in runs.json, source matching in preflight.json and the complete audit in result-audit.json. All raw timing CSVs, final voltages, input hashes, executed sources, runtime fingerprints and CPU observations are retained. The bounded log is ipss.test.plugin.core/target/rustpower-large-single-warmup-20261030.log.

Machine-readable checkpoint: [rustpower-large-single-warmup-2026-10-30.json](checkpoints/rustpower-large-single-warmup-2026-10-30.json).
