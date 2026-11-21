# Prepared polar NR versus RustPower: maintained-path recheck

The latest maintained prepared NR does not meet the 1.2 runtime-ratio target
across all three cases. IEEE39 passes the pooled-mean criterion; IEEE118 and
PEGASE9241 fail. This is a completed, accuracy-valid benchmark with a failed
performance criterion.

| Case | RustPower mean ms | Prepared NR mean ms | InterPSS / RustPower | <= 1.2 |
| --- | ---: | ---: | ---: | --- |
| IEEE39 | 0.012967 | 0.015381 | 1.186 | Pass |
| IEEE118 | 0.031666 | 0.041762 | 1.319 | Fail |
| PEGASE9241 | 13.380249 | 16.885001 | 1.262 | Fail |

The criterion is the pooled arithmetic mean InterPSS runtime divided by the
same-machine RustPower KLU runtime, at or below 1.2 for every case. A smaller
ratio is better. All measured samples are retained; no outliers were removed.
IEEE118 needs about 9.0% further time reduction and PEGASE9241 about 4.9% at the
measured RustPower means. No optimization or repeated unchanged qualification
batch was used to seek a passing result.

## Executed implementation and protocol

The benchmark invokes the maintained `LoadflowAlgorithm.loadflow(false)` NR
workflow. The driver asserts prepared residual/Jacobian/workspace execution,
one preparation per case/process and retained symbolic analysis. It does not
invoke the retired whole-solve experiment.

RustPower 0.5.2 with its KLU feature is pinned, matching the version in the
[published performance table](https://github.com/chengts95/rustpower#performance-comparison).
The comparison uses locally measured RustPower timings rather than the
published absolute times from different machines. Other package versions are
pandapower 3.5.4, NumPy 2.4.6, SciPy 1.18.1 and Numba 0.68.1.

Both runtimes execute on verified logical CPU 2, affinity mask 0x4. Three fresh
processes per runtime alternate order. Each small case has 50,000 warmups and
10,000 measured solves per process; PEGASE9241 has 50 warmups and 100 measured
solves. There are 120,600 measured solves in total. Every solve starts from the
compensated flat start, uses 1e-6 pu tolerance with a 20-iteration limit, and
converges in 4/4/6 iterations.

RustPower timing includes voltage reset, solve and calculated-power retrieval.
InterPSS timing includes voltage reset, public load flow, voltage publication
and generator allocation. Setup/import, compilation, validation and integrity
hashing are outside the timers. Adjustment controls, globalization,
variable-update limits and low-voltage load adjustment are disabled. This
qualifies the ordinary numerical power-flow workload rather than a controlled
network or dynamic simulation workload.

## Accuracy and path coverage

The focused source build passes 174 tests, with one existing skipped case and
no failures/errors. The required prepared API, Jacobian, shared live-device and
fixed-pattern LU classes all execute without skips.

Independent original-Ybus/Sbus checks give a maximum InterPSS equation residual
of 5.3203e-8 pu and maximum complex-voltage error of 4.5259e-9 pu. The arrays and
original Ybus match the preceding maintained benchmark exactly. Every measured
repeat index is present, and the benchmark verifies positive iteration counts.
All nine case/process diagnostics report one preparation, positive prepared
residual/workspace counts and retained symbolic analysis.

## Variability and historical interpretation

| Case | Fork 0 ratio | Fork 1 ratio | Fork 2 ratio |
| --- | ---: | ---: | ---: |
| IEEE39 | 1.219 | 1.131 | 1.216 |
| IEEE118 | 1.273 | 1.307 | 1.386 |
| PEGASE9241 | 1.199 | 1.248 | 1.339 |

IEEE39 passes the specified pooled-mean rule but misses 1.2 in two individual
forks; its pass is not a per-process guarantee. IEEE118 fails in all three
forks. PEGASE9241 passes narrowly in one fork and fails in two. Raw timings,
medians, tails and per-process summaries remain in the checkpoint and evidence.

The previous maintained comparison reported pooled ratios 1.250, 1.348 and
1.214, also missing the overall target. The earlier passing ratios 1.014,
1.178 and 0.945 belonged to the retired whole-solve experiment. Different
timing cohorts do not establish which source change caused a timing difference.

## Provenance and reproduction

Core source commit: `5309d648648378f7bec22226fe5ef6c8d80c52be`.
Plugin source commit: `9cbd57e10fce7e9c31a4796a78b0b04e613ef0a0`.
The source build includes recorded pre-existing working-tree changes. Measured
classpath fingerprints, critical source snapshots, working patches, test
receipts and native RustPower package hashes identify the actual executable.
Critical source and runtime files remain unchanged across the timing batch.
No production benchmark overlay or numerical source change was introduced.

Evidence root: `ipss.test.plugin.core/target/prepared-nr-rustpower-recheck-20261030/`.
The bounded comparison log records a completed child process with exit code 2,
the driver's explicit result for an unmet performance target. Inspect
`comparison/target-verification.json` rather than interpreting that as an
accuracy failure or accepting the outer runner's exit status alone.

```powershell
$nrPerfRun = 'prepared-nr-comparison-YYYYMMDD-HHMMSS'
python tools/run_bounded.py --log "ipss.test.plugin.core/target/$nrPerfRun.log" -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --test-report .verification-build/prepared-nr-rustpower-recheck-20261030/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.aclf.PreparedNewtonLoadflowTest.xml --output "ipss.test.plugin.core/target/$nrPerfRun" --forks 3 --small-warmup 50000 --small-repeats 10000 --large-warmup 50 --large-repeats 100 --cpu 2 --required-ratio 1.2
```

Use a new output/log name and preserve the verified runtime. Rebuilding sources
requires a fresh passing classpath receipt. Full raw timings, source/runtime
hashes, inputs, voltage tables and independent validation are retained.

See the [machine-readable checkpoint](checkpoints/prepared-nr-rustpower-recheck-2026-10-30.json)
and [previous maintained comparison](standard-nr-integration-2026-10-30.md).
