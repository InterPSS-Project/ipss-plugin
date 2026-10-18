# REGCA1 exactly-zero initial-Q recovery selector

**Reference-quality correction:** subsequent complete-log review found 95
native network non-convergence messages in the baseline used below. The raw
artifact and these historical numbers are preserved, but are not accepted
transient evidence. A clean twice-cold 200/400-iteration reference now supplies
the public final-recovery checkpoints, without changing their bounds or the
selector implementation. See the [network run-quality audit](regca-network-run-quality-2026-10-17.md)
for corrected values, full-profile errors and the permanent rejection gate.

## Fixed equation and precise scope

The native REGCA1 recovery-rate selector uses the sign of initialized generator
Q. Positive Q selects the upper rate boundary, negative Q selects the lower,
and exactly zero selects neither. InterPSS previously used `else` for the lower
boundary, incorrectly including exactly zero. The fix is a strict `< 0` test.

```text
dIq = (-Iqcmd - IqState) / Tg
if Q0 > 0: dIq = min(dIq, Iqrmax)
if Q0 < 0: dIq = max(dIq, Iqrmin)
```

Signed zero selects neither bound. Small genuinely nonzero Q retains its
directional bound; there is no epsilon or initialization adjustment. A zero
rate remains an active zero rate when its direction is selected. This is not
PowerWorld's different convention of disabling zero-valued recovery limits.

The source contract is reference simulator Model Library, printed page 798, and the
two cold native lossless-case probes described in the
[preceding instrumentation report](texas2k-endpoint-alignment-2026-10-17.md).
Model Library PDF SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The native model DLL SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The tested baseline is plugin `2a0327cbf` (1.4.6), using locally rebuilt core
1.4.3 at sibling core commit `e886019ce`. No core solver is edited here.

## Failing-before-fix and focused checks

The updated exact-zero converter test and new signed-zero/tiny-nonzero test
both fail against the old selector: 13 tests, two failures. After the fix all
thirteen pass. The new full-solver recovery test independently fails against
the old selector because it latches the large fault current instead of
returning downward. It passes with the strict-sign correction.

The registered `RegcaZeroQRecoveryTest` uses the exact lossless network
exported from the native saved case. The public
`regca_zero_q_lossless.raw` differs from the original export only in CRLF-to-LF
normalization. Its normalized SHA-256 is
`73f2f9990ac9536c3c8f672028c73d8e18c7ea6433de666f776f5bb9f788ae83`;
the original export's raw SHA-256 is
`797f6691c18e1dec8063852c9a578c12152efc9e0cfb16be3309da17c552c957`.
It reuses the existing public REGCA1/REECA1/REPCA1 DYR, raw SHA-256
`55f82ae8b91c197e885f839494330ed0899e8a73bbe8ff9ed62694e81d53ca5d`.

The fixture has exact zero initial Q, zero real power, two lossless 0.1-pu
reactance branches and an existing core GENCLS infinite-grid approximation.
The event is a Bus1 three-phase j0.01-pu fault at 0.05 s, cleared at 0.10 s;
the step is 0.25 ms and the final physical time is 0.5 s. No initial state,
gain or limit is fitted to an export. The synchronous recorder preserves
physical/raw timestamps and event-pre samples.

The native raw CSV is identical across both cold runs:
`24f709656b6885be8aa2b9e91ef1a299197626e445995a75c45f7ab3e5536385`.
The test scores five final-recovery channels, not the complete transient:

| Channel | Native final | InterPSS final | Absolute difference |
|---|---:|---:|---:|
| Ip state | 0 | 0 | 0 |
| Iq state | -0.00691122236 | -0.00686452307 | 0.0000466993 |
| Filtered V | 0.99753773212 | 0.99757665338 | 0.0000389213 |
| Terminal V | 0.99852406979 | 0.99855575882 | 0.0000316890 |
| Q (pu) | -0.00690102205 | -0.00685460904 | 0.0000464130 |

Ip is checked exactly; the four new final-point bounds are 1e-4 pu. Existing
trajectory tolerances are unchanged. The native last timestamp is
0.500009357929 s because its clock is single precision; this is explicitly a
final-recovery checkpoint, not exact all-sample/event-side acceptance.

The focused converter, recovery, synchronous-recorder, aggregate-controller
and ANDES Type-3 regression run passes all 34 tests without skips:

```text
mvn -q -pl ipss.test.plugin.core -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PsseRegca1ConverterTest,RegcaZeroQRecoveryTest,EndpointAlignedStateMonitorTest,RenewableAggregateQvModeTest,Type3WindAndesTrajectoryTest test
```

## New repeatable PowerWorld artifact

The identical RAW/DYR and event were run twice in PowerWorld 24.2026.9.3,
without auto-correction or model overrides. Both benchmark runs complete.
Their full eight-channel exports match numerically at every sample using
`PowerWorldCsvRepeatability`: 2,003 samples and two repeated-time samples.
Canonical CSV SHA-256 (CRLF/CR to LF, retaining final newline):
`f91d1c94d384a2cf2b26fc1418927e19459497da037090d9dd75bb31a213a3b8`.
Raw CSV SHA-256, also identical for both runs:
`0c651a450698c858787c9207944eccda150617499fc9c6f0ff149da39b603d10`.

The benchmark preserves all three nearby voltages, generator P/Q and all three
converter-state exports. PowerWorld labels its first two converter states Eq
and Ip; these are not blindly matched by native state index. The exact runner,
specification, staged inputs, AUX, logs, manifests and raw results are archived.
Executable SHA-256:
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`;
runner SHA-256:
`0d6abbe93113afcc7113957d30278dda69e4392973a5da3aab611f27b4c9f61e`.

A separate read-only `TSSavePTI` audit exports all four expected records with
identical numeric fields: REGCA1 (15), REECA1 (51), REPCA1 (34), GENCLS (2).
The import export raw SHA-256 is
`d77b37bcfd562add351b038fae0e87bd304a9580ddd2176957112a8326260a42`.
That separate audit stalls after this export and times out; its later
post-initialization export is not claimed as completed. This does not change
the status of the two original completed benchmark runs. Its partial data and
failure logs are preserved separately, not promoted to a complete artifact.

## Remaining high-voltage boundary discrepancy

The complete native comparison still has maximum errors of approximately
0.0257623 pu in Iq, 0.0100337 pu in filtered voltage, 0.0183000 pu in terminal
voltage and 0.1280651 pu in Q. These are reported, not hidden under new broad
trajectory thresholds. Final recovery agreement does not prove the full model.

| Tool | Post-fault voltage peak | Final voltage |
|---|---:|---:|
| Native reference simulator | 1.20973337 | 0.99852407 |
| PowerWorld | 1.20000005 | 0.99857450 |
| InterPSS, selector fixed | 1.21955198 | 0.99855576 |

The native peak is a short early transient near 0.1005 s; do not describe its
complete trajectory as an exact 1.2-pu clamp. PowerWorld's
[REGC_A documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
explicitly places VLim in the algebraic network boundary. Its completed export
independently demonstrates that behavior on this profile. InterPSS currently
uses a finite-gain high-voltage current correction. This remains a separate
network-interface investigation, not a reason to fit Khv or mutate the dynamic
Iq state during output reads.

The exported native high-voltage VAR also needs a coordinate audit. On this
100-MVA case, `Q - V*Iq + VAR` is much smaller than
`Q - V*(Iq - VAR)` over its 105 active samples (maximum residuals 2.67e-5 and
0.02545 pu respectively). This observed identity is localization evidence, not
an inferred replacement model equation or a gain multiplier.

Negative-time native initialization samples and instantaneous native event-post
samples remain in raw files; this InterPSS observation path lacks the latter.
The diagnostic comparison uses declared nominal-step alignment and reports
that limitation. Whole-profile independent acceptance remains open.

## Full regression and remaining work

Full registered regression exits zero with the six strict flat cases and
fifteen topology faults enabled: 2,712 tests, 2,623 passed, 89 skipped,
zero failures/errors. Counts use only fresh Surefire reports written after
the run began; the unrelated untracked WECC240/JAX test is excluded.

```text
mvn -q test -Pextended-tests --fail-at-end -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| ipss.plugin.core | 129 | 4 | 0 |
| ipss.plugin.3phase | 468 | 28 | 0 |
| ipss.test.plugin.core | 2,115 | 57 | 0 |

All six strict flat runs retain maximum voltage drift 3.13e-12 pu and speed
drift 5.67e-14 pu. All fifteen fault execution gates pass. All fifteen fault
trajectories change with this production fix; input hashes, timestep and
duration match the preceding endpoint-aligned archive exactly.

Selected changes at physical 0.5 s and maximum speed deviation among the three
monitored conventional machines per event are:

| Event | Old final V | New final V | Old max speed deviation | New max speed deviation |
|---|---:|---:|---:|---:|
| Case 3 weak grid, Bus1011 | 1.361645 | 0.984561 | 0.016126 | 0.017657 |
| Case 5 weak grid, Bus1011 | 1.519312 | 1.081986 | 0.039290 | 0.031576 |
| Case 5 conventional, Bus1051 | 1.330595 | 1.179310 | 0.047700 | 0.031737 |
| Case 6 GFM terminal, Bus1016 | 1.699419 | 1.682123 | 0.034741 | 0.026746 |

Values are pu. Most selected responses improve, but not every speed deviation
decreases. The worst monitored-machine deviation across the fifteen events
drops from 0.0477002 to 0.0317372 pu. These are not fleet-wide maxima or
independent full-system acceptance. In particular, the severe GFM-terminal
overvoltage remains unresolved. No physical parameter is changed to obtain
these improvements.

The enabled configuration, fresh XML, complete log and all fifteen new traces
are archived and copy hashes checked. Native and PowerWorld input/output
manifests and exact-case newline normalization also pass the archive audit.
Private opt-ins are restored to false after validation. Full log raw SHA-256:
`45866961ad3dfac35d8eadd752ad1d9d737d0ac9c98379dc80aa4f27424c7526`.
Existing unrelated caught diagnostic exceptions in the full reactor log are
not represented as an exception-free runtime claim.

- [x] Correct the strict zero/nonzero selector without an epsilon.
- [x] Observe two focused failures and one actual-solver failure before fixing.
- [x] Pass 34 focused tests and register the public lossless recovery fixture.
- [x] Match the five native final-recovery checkpoints within declared bounds.
- [x] Cold-repeat the same profile in PowerWorld and verify every paired sample.
- [x] Check all imported model records numerically; retain the separate failed
  post-initialization export audit without claiming it completed.
- [x] Complete full registered regression, all six strict flat cases and fifteen
  fault events, then archive and commit this scoped model fix.
- [ ] Implement and independently verify the high-voltage algebraic boundary;
  do not redefine all-sample acceptance around the final checkpoint.
- [ ] Complete exact event-side observations and the current REGFMA1 schema.
- [ ] Resolve full-system faults and obtain independent Texas2k acceptance.

Private evidence is preserved outside build directories under
`2026-10-17-regca-zero-selector`. No existing reference export is replaced.
