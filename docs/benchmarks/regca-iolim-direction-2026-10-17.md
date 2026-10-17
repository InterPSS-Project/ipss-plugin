# REGCA1 native Iolim current-coordinate correction

## Fixed model equation

Baseline: plugin `03d158833` (1.4.6), locally rebuilt core 1.4.3 at
`e886019ce`. No core source or dependency version changes in this slice.

Native PSS/E REGCA1 specifies negative Iolim in the opposite reactive-current
coordinate from the positive-generated-Q current stored and injected by this
InterPSS model. The correct generated-current ceiling is **-Iolim**, not a
generated-current floor at Iolim. The conversion is:

```text
Iq_internal >= Iolim
Iq_generated = -Iq_internal
Iq_generated <= -Iolim
```

The production output changes from `max(Iolim, generatedIq)` to
`min(-Iolim, generatedIq)`, where `generatedIq` includes the existing algebraic
high-voltage correction. No dynamic lag state, rate boundary, gain, integration
method, initial state or case input is changed. The shared function feeds both
the Norton injection and observation/power-feedback paths. It remains pure on
repeated reads. The limiter also applies below Volim, as the native probe proves.

This does **not** implement the still-missing iterative high-voltage network
interface. Its gain/deceleration/correction lifecycle and joint active-limit
behavior remain separate work. The native fault used here stays below 1.053 pu
after the correction, so it isolates Iolim without high-voltage activation.

## Sources and exact input contract

The native source is PSS/E 36.7 Model Library, printed page 797, with one ICON
and fourteen CON values; Iolim is CON J+8. The parser's fifteen-value record
and ordering are unchanged. The preceding
[network-quality audit](regca-network-run-quality-2026-10-17.md) documents the
two opposite-direction discriminators and original source fingerprints.

Native fault with Iolim=-0.25: during 165 low-voltage samples, the lag state
exceeds +0.3 pu while actual generated current `Q/V` is +0.25 pu, with zero
exported high-voltage correction. Native capacitive-step runs with Iolim=-0.25
and -1.3 are instead byte-identical while absorbing more than 0.25 pu. Together
these distinguish the positive generated-current ceiling from the old negative
floor. Actual native CON readbacks confirm the requested values.

[PowerWorld REGC_A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
does not use its Qmin/Khv fields; it cannot serve as an active-native-Iolim
oracle. The WECC appendix's differently labeled reactive-power floor must not
be silently substituted for the native current limit. No new vendor dialect or
auto-correction option is added to the PSS/E reader.

The three-bus lossless network is the existing public
`regca_zero_q_lossless.raw`, SHA-256
`73f2f9990ac9536c3c8f672028c73d8e18c7ea6433de666f776f5bb9f788ae83`.
New public `regca_iolim_native.dyr` is the exact native probe DYR with only
CRLF-to-LF normalization, SHA-256
`aec15db2465f7d70f148636af4f8523cc0fd13b496f0260975d26412bb8848ee`.
The original native DYR hash is
`628e284a1d74d506fbc7ecaa2a34d3018e24151dadd490b8445bfeda831b8d7b`.
It has REGCA1, REECA1, REPCA1 and core GENCLS, not an alternate machine engine.
The event is a Bus1 j0.01-pu three-phase fault at 0.05 s, cleared at 0.10 s,
on 100 MVA and 60 Hz, with zero initial Q and P, through 0.5 s.

Native DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Exact probe script SHA-256:
`1764cf1973f34d8c00f93797eb0432fd26fd872f5aecebdd50fb1cfde1d57ee8`.
Both step sizes use a 200-iteration network budget. Each reference has two
identical cold CSV exports, good initialization and clean recognized run-quality
diagnostics. Raw artifacts remain immutable in the ignored private archive
`2026-10-17-regca-iolim`, alongside scripts, staged inputs, logs and manifests.
The installed native 50-bus license still cannot benchmark complete Texas2k.

## Failing-before-fix evidence and focused tests

Against the old implementation, the converter test run has 16 tests and two
failures: requesting -0.8 pu incorrectly produces -0.25, while a positive
1.0-pu predictor state is emitted without the required +0.25 ceiling.
The corrected run passes all sixteen converter tests.

The three new converter tests check positive/boundary/negative commands;
50/100/200-MVA base conversion; complex-voltage Norton injection; unchanged
predictor/corrector storage; reinitialization; idempotent reads; and inactive
current/voltage derivatives. The old primitive test that asserted a negative
generated-current floor is corrected using the independent native evidence.
All existing trajectory bounds are preserved.

An isolated build of the committed pre-fix converter reproduces the full-solver
discrepancy without editing the production build. Its Git blob matches the
committed source exactly: `44bc7e6635232fa9daa18087738f84ee02172048`.
The old and new runs use the same loader, network, controllers, inputs and
observation path; only the converter implementation is switched.

`RegcaCurrentLimitNativeTest` is registered in CorePluginTestSuite. It checks
23 native checkpoints per step, with all three converter states, terminal V
and Q, plus the independent exact +0.25 current limit during the fault. The
existing zero-Q recovery, artifact-quality/integrity and ANDES Type-3 tests also
pass. Total focused result: **44 tests, no failures/errors/skips**.

## Complete nominal-grid diagnostic comparisons

All comparable InterPSS endpoints, including repeated event-pre frames, are
scored in the private full exports; public regression retains the selected
checkpoints. Native instantaneous event-post rows and negative initialization
history are retained but unmatched. This is not exact all-event-side acceptance.

| Channel | Old max error, 0.25 ms | Fixed max error, 0.25 ms | Fixed max error, 0.125 ms |
|---|---:|---:|---:|
| Ip lag | 0 | 0 | 0 |
| Iq lag | 0.57328484 | 0.01296642 | 0.00651855 |
| Filtered V | 0.12118977 | 0.00306359 | 0.00155113 |
| Terminal V | 0.16705209 | 0.00078894 | 0.00039606 |
| Q | 1.01193729 | 0.00332666 | 0.00167207 |

Errors are pu. Fixed Iq/filter maxima occur at the first integrated endpoint
after clearing (0.10025 / 0.100125 s). Voltage maxima occur at 0.26975 / 0.27075 s;
Q maxima at 0.2625 / 0.261625 s. The roughly halving errors are consistent with
finite-step/event staging; they do not prove every coupled-controller or
high-voltage boundary equation. No gain, limit or signal is fitted to improve
the match.

New checkpoint bounds (Ip/Iq/filter/V/Q) are respectively
`0/.014/.0033/.00085/.0036` at 0.25 ms and
`0/.007/.0017/.00043/.0018` at 0.125 ms. These are modestly above observed
full-profile maxima; they are new active-limit profile gates, not widened
existing tests. The exact active current itself is checked within 1e-10 pu.

| Step | Native CSV SHA-256 | Native rows | Compared InterPSS rows |
|---|---|---:|---:|
| 0.25 ms | `80e092f8bc0c3a523d1a0d301ca6b9067b1713ac128918f604683866641cd13a` | 2,005 | 2,003 |
| 0.125 ms | `a0171d86330529e48d406363ab747754d20ff2c61b06c92f2fe209b7d599c96d` | 4,006 | 4,003 |

Native time is single precision. Nominal tick mapping has maximum drift
9.36 / 13.44 microseconds, bounded by h/8 for the scored samples. At 0.125 ms,
native run-to-time emits one extra sample at 0.500111579895 s; it is preserved
but not substituted for the requested nominal 0.5-s checkpoint (native time
0.499986559153 s). Its run-quality completion allowance is explicitly one
step, while the 0.25-ms allowance remains 1e-5 s. These are clock/end-time
allowances, not relaxed trajectory error thresholds.

## Full regression and system impact

The complete registered reactor exits zero: **2,727 tests, 2,638 passed,
89 skipped, zero failures/errors**. Only fresh Surefire reports written after
the run started are counted; the unrelated untracked WECC240/JAX test is excluded.

```text
mvn -q test -Pextended-tests --fail-at-end -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| ipss.plugin.core | 129 | 4 | 0 |
| ipss.plugin.3phase | 468 | 28 | 0 |
| ipss.test.plugin.core | 2,130 | 57 | 0 |

All six strict one-second flat cases retain maximum voltage drift
3.12838644e-12 pu and speed drift 5.66213743e-14 pu. All six flat/fault/trip
smokes and all fifteen topology-fault execution gates pass. Source hashes,
timesteps and durations match the preceding committed baseline. Nine complete
fault CSVs are byte-identical; six change:

| Case/site | Old final V | New final V | Old max monitored speed deviation | New deviation |
|---|---:|---:|---:|---:|
| 4 conventional | 1.11260975 | 1.11261113 | 0.02821362 | 0.02821362 |
| 4 renewable | 1.16099695 | 1.16099814 | 0.02820417 | 0.02820418 |
| 5 conventional | 1.17931024 | 1.18123752 | 0.03173718 | 0.03173811 |
| 5 renewable | 1.14198184 | 1.13529436 | 0.03097927 | 0.03091726 |
| 5 weak grid | 1.08198586 | 1.12155605 | 0.03157625 | 0.03156691 |
| 6 GFM terminal | 1.68212314 | 1.68170750 | 0.02674571 | 0.02672717 |

Values are pu at physical 0.5 s; speed maxima cover the three monitored
conventional machines per event, not the fleet. Not every response improves:
Case 5 weak-grid voltage increases, and the severe Case 6 GFM overvoltage is
almost unchanged. Broad execution success is not independent physical acceptance.
This native-model correction therefore does not close the Texas2k milestone.

The full log, enabled settings, fresh reports, fifteen raw traces and matched-input
comparison are archived under `2026-10-17-regca-iolim/completed-regression`.
Source opt-ins are restored to false. Full regression log SHA-256:
`44cfdd988cef0207e83b9b9b2ead823d265a9d77aa9a99b69d12d7c44b3bc5d6`.

### Current-equation system timestep study

The two historically severe sites are rerun on this corrected production code
at h/2 and h/4, where h=1/240 s. Both scoped eight-test runs pass without skips,
adding four fault executions. They use fresh tagged outputs, the same original
input hashes/duration, and physical endpoint-v2 records. All original raw traces
and repeated-time event-pre samples are preserved. The comparison uses exact
common physical times **and matching sample kinds**, without interpolation.

| Site | Final V, h | Final V, h/2 | Final V, h/4 | Peak V, h/4 |
|---|---:|---:|---:|---:|
| Case 5 weak grid, Bus1011 | 1.12155605 | 1.07926025 | 1.09508761 | 1.37308409 |
| Case 6 GFM terminal, Bus1016 | 1.68170750 | 1.68256462 | 1.68486889 | 1.81364397 |

On the same h-grid, Case 5 maximum voltage differences grow from 0.18121 pu
(h versus h/2) to 0.25653 pu (h/2 versus h/4). Its maximum difference among
the three monitored machine speeds grows from 0.003180 to 0.016397 pu. Case 6
voltage differences decrease from 0.03743 to 0.01918 pu, but speed differences
grow from 0.001421 to 0.002349 pu and the severe voltage persists. These runs
therefore do **not** establish system timestep convergence or physical acceptance.
The GFM internal-voltage command remains at its 1.2-pu upper limit at final time
for all three steps; that localization alone does not prove the controller is wrong.

The four reduced-step traces, separate enabled configurations, logs, test reports,
hash-checked summaries and full all-monitored-speed comparison are archived
beside the baseline run. The additional tests do not change the full-reactor
count above. All source diagnostic settings are restored to defaults afterwards.

### Newly identified core convergence-reporting gap

Read-only inspection of the tested core `e886019ce` finds that
`DStabSolverImpl.networkSolutionStep()` returns false when its voltage iterations
exhaust the budget without convergence. However,
`AbstractDStabSolver.nextStep()` ignores that boolean at all three modified-Euler
network solves. The caller can therefore advance despite this failure, without
the exception that would make the outer step return false. Passing execution
assertions do not prove converged network solutions. This is a separate core
bug, not proof that a particular saved timestep failed; per-stage runtime
instrumentation is still needed to identify affected steps and buses.

The direct Iolim coordinate/Norton checks remain valid, and the reported native
references have independent log-quality checks. The InterPSS trajectory and
system evidence above must nevertheless retain this explicit convergence-quality
limitation. Propagate solver failures, expose iteration/residual diagnostics,
and rerun current case gates before treating them as physical acceptance. This
core reliability issue takes priority over interpreting the severe responses as
model or parameter errors. It is not silently patched in this model-only slice.

Remaining: iterative high-voltage management/deceleration and its lifecycle,
joint active-HV/current-limit profiles, exact instantaneous event-post comparisons,
historical reference-log audits, native REGFMA1 twenty-value parsing, and matched
complete-system acceptance. A separate deliberately forced-state native diagnostic
is retained privately for the next HV-interface investigation; it is not a
physical fault benchmark or evidence that the current network interface is fixed.

Follow-up: core `12c231df5` now propagates stage failures and exposes permanent
last-solve diagnostics. The [convergence-enforced report](network-convergence-propagation-2026-10-17.md)
supersedes this report's system-execution baseline, not its native Iolim
coordinate evidence. All fifteen production-step faults now execute with the
convergence criterion enforced; synthetic deep-fault recovery and complete
independent/model acceptance remain explicitly open.
