# RustPower and maintained prepared NR: 25k and 70k

RustPower 0.5.2 with KLU successfully solves both complete large-case nodal systems through its numerical interface. The maintained prepared InterPSS NR also converges. Both cases meet the pooled-mean runtime-ratio criterion of InterPSS / RustPower <= 1.2 in this batch. These are fixed-control numerical benchmarks.

| Case | Start | RustPower mean ms | Prepared NR mean ms | InterPSS / RustPower | Target |
| --- | --- | ---: | ---: | ---: | --- |
| ACTIVSg25k (Texas25k) | Compensated flat | 44.467043 | 48.262114 | 1.085 | Pass |
| ACTIVSg70k | Saved-state restart | 233.958809 | 245.064010 | 1.047 | Pass |

All three per-fork ratios also pass for both cases. The earlier IEEE39/IEEE118/PEGASE9241 cohort was not rerun here: its IEEE118 and PEGASE9241 failures remain. Passing this added cohort does not establish a five-case overall pass.

The [one-warmup follow-up](rustpower-large-single-warmup-2026-10-30.md) misses the target, with ratios 2.047 and 1.668. The passing results above describe the warmed protocol documented here; the follow-up uses exactly one warmup and one measured solve in a fresh process for each case/runtime.

## Support and convergence scope

RustPower accepts an externally prepared sparse Ybus, Sbus, starting voltage and PV/PQ/reference indexing. Its [official interface description](https://github.com/chengts95/rustpower#pandapower-integration-scope) distinguishes numerical solving from importing a full device/control model. Actual successful solves establish support for these two numerical systems; they do not establish complete input-format or control-model coverage.

ACTIVSg25k converges from compensated flat start. RustPower reports five iterations and InterPSS reports six; the independent full-step reference takes five updates to reach the benchmark tolerance and six to reach 1e-8 pu. The table contains complete-solve timings with each maintained implementation's iteration policy. It is not an equal-iteration kernel comparison.

ACTIVSg70k fails the RustPower flat-start solve after the 20-iteration limit. The independent full-step reference also diverges from flat start. Its supplied saved voltage state, with the current PV/reference magnitudes enforced and reference angle rotated to zero, gives a reproducible successful restart: both timed implementations report six iterations. This is the explicitly selected 70k profile; it does not qualify flat-start robustness. A perturbed-start exploratory reference failure is also retained. No converged output from either timed solver is used as its next initial state.

## Exact nodal representation

| Input | Physical buses | Three-winding virtual buses | Exported buses | Active AC branches | PV buses | Reference buses |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ACTIVSg25k | 24,250 | 750 | 25,000 | 32,229 | 478 | 1 |
| ACTIVSg70k | 67,900 | 2,100 | 70,000 | 88,207 | 4,470 | 1 |

The original inputs are initialized through the maintained direct parser with adjustment controls disabled and nominal-voltage line-to-transformer correction enabled. Every bus is retained. The exporter checks constant-power load equations and rejects active voltage-dependent current or impedance terms. Some 70k buses are ZIP-labelled aggregates, but their actual current and impedance coefficients are zero.

The canonical MATPOWER snapshot retains each branch's two mutual admittances using equivalent series impedance and phase shift at unit tap magnitude. The difference between the original self-admittances and these series terms becomes a fixed bus shunt. Net specified injections are represented as aggregated negative loads, with zero-power PV/reference generator records for voltage masks and setpoints. Original bus identities and base voltages are retained in a separate mapping. The canonical model is for nodal voltages and residuals; individual generator allocation, physical branch-flow bookkeeping and control workloads require their original device representation.

Both snapshots are re-imported through the maintained MATPOWER parser and re-exported before timing. Maximum Ybus roundtrip errors are 7.2902e-12 (25k) and 1.4559e-11 (70k); maximum Sbus error is 8.8818e-16 pu. Equation masks and controlled voltage setpoints match. Qualified input hashes are checked before the matched harness stages the files.

## Timed boundary and accuracy

Three fresh processes per runtime alternate order on verified logical CPU 2, mask 0x4. Each case has 50 warmups and 100 measured solves per process: 1,200 measured solves total. All samples and outliers are retained. Both use 1e-6 pu tolerance and a 20-iteration limit. Adjustment controls, globalization, variable-update limits and low-voltage load adjustment are disabled.

RustPower timing includes voltage reset, solve and calculated-power retrieval. InterPSS timing includes voltage reset, maintained public loadflow(false), voltage publication and canonical-model generator allocation. Import, plan/context setup, compilation, validation and hashing are outside the timers. Java uses pure Java KLU with a 2 GiB heap bound.

An independent SciPy sparse polar Newton implementation solves the original exported Ybus/Sbus to 1e-8 pu. Final timed voltages are checked against those original equations and that reference. Maximum InterPSS residual is 3.02793e-8 pu, and maximum complex-voltage error is 5.49590e-9 pu. RustPower maxima are 1.02732e-8 pu and 9.94230e-10 pu. Every measured call converges and reports a positive iteration count. All six InterPSS case/process diagnostics show one plan build, positive prepared residual/workspace counters and retained symbolic analysis.

## Variability

| Case / runtime | Median ms | Min ms | P95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| activsg25k / rustpower-klu | 42.180000 | 38.132900 | 59.926480 | 157.623400 |
| activsg25k / prepared | 47.538950 | 44.649300 | 53.509690 | 74.273400 |
| activsg70k / rustpower-klu | 227.882550 | 213.686800 | 274.697640 | 413.222800 |
| activsg70k / prepared | 234.378500 | 222.301200 | 294.766965 | 427.064800 |

| Case | Fork 0 ratio | Fork 1 ratio | Fork 2 ratio |
| --- | ---: | ---: | ---: |
| activsg25k | 0.960 | 1.151 | 1.174 |
| activsg70k | 1.073 | 0.988 | 1.081 |

## Reproduction and validation

New tooling is tools/benchmarks/LargePowerflowCaseExporter.java and tools/benchmark_large_cases.py. Compile the exporter against the verified source-runtime classpath, then run its raw mode for each input into a new output directory. Prepare the numerical snapshots with:

```powershell
$benchmarkPython = "target/rustpower-benchmark-20261030/venv/Scripts/python.exe"
& $benchmarkPython tools/benchmark_large_cases.py prepare --source <exported-25k-directory> --output <new-input-directory> --name activsg25k
& $benchmarkPython tools/benchmark_large_cases.py prepare --source <exported-70k-directory> --output <same-input-directory> --name activsg70k --start saved
```

Re-import each generated .m using the exporter's matpower mode, then run benchmark_large_cases.py verify with --source pointing to the original export and --output pointing to that roundtrip export. Successful verification writes the input qualification receipt required by the matched runner. The executed timing command was:

```powershell
python tools/run_bounded.py --log ipss.test.plugin.core/target/rustpower-large-matched-20261030.log -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --output ipss.test.plugin.core/target/rustpower-large-cases-20261030/comparison --test-report .verification-build/prepared-nr-rustpower-recheck-20261030/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.aclf.PreparedNewtonLoadflowTest.xml --cases activsg25k activsg70k --case-directory ipss.test.plugin.core/target/rustpower-large-cases-20261030/inputs --cpu 2 --forks 3 --large-warmup 50 --large-repeats 100
```

Use new output/log locations for subsequent runs. The original three-case defaults remain available; the large cases run as a separate cohort. The legacy Java comparison is guarded against using an incompatible large-case start profile.

Five focused Python tests pass: independent finite-difference Jacobian validation, tap/phase/shunt/injection normalization, known-solution Newton convergence, rejection of unsupported two-port asymmetry and rejection of an unmatched legacy CLI profile. Both full large-case roundtrips and the 1,200-sample audit pass. The actual runtime reuses the preceding source reactor gate (174 passed, one existing skip); every snapshotted numerical production source matches that verified build. Production algorithms were unchanged in this task.

## Evidence

- Machine-readable checkpoint: [rustpower-large-cases-2026-10-30.json](checkpoints/rustpower-large-cases-2026-10-30.json).
- Local evidence root: ipss.test.plugin.core/target/rustpower-large-cases-20261030/.
- comparison/ contains all timing CSVs, final voltages, diagnostics, accuracy checks, qualified input hashes, runtime fingerprints, executed source snapshots and the successful target receipt.
- result-audit.json verifies all 1,200 repeat identities, affinity observations, file hashes, prepared-path coverage, source-build matching and accuracy limits.
- Reference, roundtrip, smoke and failed exploratory logs are retained alongside the full timing log. The bounded full run completed with child exit code 0.
- Original input SHA-256: 25k 96db16ae4d5f05b1dd9f894d775399b51a0e76279b8a941a113ab9e10df409f3; 70k b4aecf7a16273ebb401155d70d1cdcb1838a7af364029ed8448e8e0d3754480b.
- Numerical source/runtime provenance: core 5309d648648378f7bec22226fe5ef6c8d80c52be; plugin 9cbd57e10fce7e9c31a4796a78b0b04e613ef0a0 plus recorded working-tree sources. Exact executed class and dependency fingerprints remain in comparison/environment.json.
