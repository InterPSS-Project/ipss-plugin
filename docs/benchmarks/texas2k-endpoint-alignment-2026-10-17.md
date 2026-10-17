# Synchronous fault-state observations and native REGCA1 discriminators

## Corrected observation contract

This slice changes test instrumentation, not production equations, integration
stages or parameters. The ordinary core solver emits newly integrated results
with a step-start timestamp, then increments its simulation clock. The old
Texas2k writer independently sampled controller states after that increment and
joined them by timestamp. It therefore paired different physical endpoints.

The baseline is plugin commit `aaa7739c1`, version 1.4.6, with the locally
rebuilt core 1.4.3 at sibling core commit `e886019ce`. The core solver itself
is not edited in this slice.

`EndpointAlignedStateMonitor` now freezes controller states during the reference
bus's actual output callback, in the same sequence as voltage and machine
monitor records. After a successful step, it identifies the physical endpoint
from the driver clock. It retains the original solver label separately and
rejects missing, noncontiguous, incomplete or unsupported callback sequences.
It neither advances model states nor changes the solver clock.

New files use the distinct `texas2k-topology-fault-matrix-endpoint-v2` prefix,
including tagged diagnostics. Existing historical files are not rewritten.
The first columns are `time_s,solver_time_s,sample_kind`, followed by the
existing named monitor/controller channels. Sample kinds are:

- `INITIAL`: the initialized operating point.
- `STEP_END`: the corrected integrated endpoint at start + step.
- `EVENT_PRE`: the extra pre-event callback at the step's start.

At a 1/240-s step, for example, an event-pre sample at 0.05 s is followed by
an integrated endpoint at 0.0541667 s, not an instantaneous event-post sample
at 0.05 s. Both callbacks may carry the raw solver label 0.05 s. The preceding
regular endpoint can also be a pre-event sample at 0.05 s; that redundancy is
retained and labeled rather than silently removed. The ordinary core does not
emit a separate instantaneous post-event algebraic observation in this path.

The initial custom-state capture is configured after successful initialization,
when named states exist. Subsequent snapshots are callback-synchronous and
defensively copied. A changed state width, nonfinite state or unexpected output
contract fails the diagnostic instead of producing a plausible joined file.

## Focused proof

Six tests in the registered `EndpointAlignedStateMonitorTest` pass. They cover
callback-time capture, immutable storage, distinct event-pre/endpoint samples,
clock/lifecycle/output guards, state-vector validation and a public full-solver
three-phase fault. The full-solver test checks every recorded voltage and
machine-speed value against its synchronous live snapshot with exact equality.
It checks both fault edges and reaches a physical 0.15-s final endpoint.

```text
mvn -q -pl ipss.test.plugin.core -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=EndpointAlignedStateMonitorTest,Texas2kTopologyFaultMatrixTest,RepositoryTestConfigurationTest test
```

The existing eight fault-harness tests also pass; the private simulation is
disabled in this focused run. Full system validation is recorded below.

## Matched-network negative-Q investigation

The previous slice's native negative-Q reference was exported from its actual
saved case using PSS/E 36.7 `writerawversion('33',...)`, then imported into
InterPSS. The exact exported RAW SHA-256 is
`f1ebd3ac3ec4e7bbe4b20ab3790b06f3e7d39d26aa347131efbbda8de8d30452`.
Both tools use the same public REGCA1/REECA1/REPCA1 control data and a Bus1
j0.01-pu three-phase fault from 0.05 to 0.10 s. No gains or initial states are
fitted. The initial native P/Q is 24 MW/-10.97093868 Mvar.

Native baseline exports were already cold-repeated in the preceding slice.
Additional 0.25-ms native runs were cold-repeated into fresh directories, adding
the documented previous-voltage, high-voltage-correction and initial-Q VARs.
Both have 2,005 samples and identical raw CSV SHA-256:
`a82dd1bd4666cc3ea178d9a292b0ac42b654ef2b0def4056867a00e505274209`.

The synchronous InterPSS probe was run at both 0.5 and 0.25 ms. Its existing
finite response is not a new accepted benchmark. The following are diagnostic
common nominal-step comparisons, using the native event-pre occurrence at
fault boundaries:

| Channel | Max error, 0.5 ms | Max error, 0.25 ms |
|---|---:|---:|
| REGCA Ip state (pu) | 0.00502244 | 0.00251147 |
| REGCA Iq state (pu) | 0.01230700 | 0.00608050 |
| REGCA voltage-filter state (pu) | 0.00677574 | 0.00347272 |
| Terminal voltage (pu) | 0.00229819 | 0.00221855 |
| Reactive power (pu) | 0.01679792 | 0.01618837 |

The first three errors approximately halve, but terminal-voltage and Q errors
do not. That separates event integration effects from a remaining boundary or
measurement discrepancy; it does not by itself identify the incorrect model.
The published native REGCA1 diagram (PSS/E 36.7 Model Library, printed page
798) was visually inspected at high resolution. Its Khv boundary equation is
not replaced by a gain inferred from a trace.

The native VAR and machine-Q channels do not yet reconstruct a single accepted
algebraic current boundary. A separately cold-repeated half-Khv discriminator
is retained for the next investigation. An observed VAR ratio is not a reason
to multiply the production gain. PSS/E model-library binary SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

Comparison limitations are explicit. Native timestamps drift by up to 4.65 us
at 0.5 ms and 9.36 us at 0.25 ms; samples are matched on their declared nominal
step grid. The native files include two negative-time initialization samples
and instantaneous event-post samples absent from this InterPSS output path.
Those rows remain in the raw artifacts and are reported as unmatched, not
silently treated as passed. Complete trajectory acceptance still requires an
event-side observation contract and all required channels.

## Confirmed next model gap: exactly zero initial Q

Two native zero-Q discriminators prevent a floating-point sign mistake from
being mistaken for model behavior. Merely setting P/Q to zero in the original
lossy network produced a stored native initial-Q VAR of about -3.37e-18 pu.
It is negative, not exactly zero, and the lower recovery-rate boundary remains
active. That probe cannot prove the exact-zero selector behavior.

A second lossless-network probe has exactly zero stored initial Q and Iq.
It uses unchanged control parameters, including both recovery-rate limits at
zero. It cold-repeats exactly: 2,005 samples, raw CSV SHA-256
`24f709656b6885be8aa2b9e91ef1a299197626e445995a75c45f7ab3e5536385`.
Native Iq rises to 1.06009948 pu during the fault, then falls to -0.00691122 pu
by the last sample; terminal voltage returns to 0.99852407 pu.

The published diagram selects the upward bound for positive initial Q and the
downward bound for negative initial Q. It does not select either bound at
exactly zero. The InterPSS implementation instead currently uses an `else`
branch for all nonpositive Q, and its old exact-zero focused test asserts that
downward return is blocked. That is a concrete boundary error to correct in the
next model slice with failing-before-fix tests and the native probe. Do not add
an epsilon that silently reclassifies genuinely small positive/negative Q.

This exact-zero gap is not claimed as the cause of every severe Texas2k fault:
Bus1016's source units initialize with materially negative Q. Their native
zero-Iqrmin convention remains different from PowerWorld's zero-disables rule.

## Full regression and remaining work

The full registered reactor regression exits zero with all six strict flat
cases and all fifteen topology faults enabled: 2,710 tests, 2,621 passed,
89 skipped and zero failures/errors. Counts come only from fresh Surefire
reports written after this run began. The unrelated untracked WECC240/JAX
test is excluded. The command is:

```text
mvn -q test -Pextended-tests --fail-at-end -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| ipss.plugin.core | 129 | 4 | 0 |
| ipss.plugin.3phase | 468 | 28 | 0 |
| ipss.test.plugin.core | 2,113 | 57 | 0 |

All six strict one-second flat cases retain the preceding maximum voltage
drift of 3.13e-12 pu and speed drift of 5.67e-14 pu. The fault checks remain
broad execution checks, not physical or independent trajectory acceptance.
The fifteen new fault exports pass the observation non-regression
audit: all 40,590 original voltage/angle/machine monitor values (22 channels,
123 samples per case) match the old raw values exactly. Every endpoint export
now ends at physical 0.5 s and retains both event-pre callbacks. Controller
columns intentionally change to their synchronous snapshot; entire-file hashes
are not expected to match the old incorrectly joined format.

The actual enabled test configuration, fresh XML reports, full regression log
and all fifteen corrected exports are archived outside build directories.
Native manifests, their frozen script versions and all 32 declared native
artifact hashes have been checked. Source private opt-ins are restored to
false after validation. Full log raw SHA-256:
`d261edfe09a9bcb58a01311bdd387febae268a40a308567e42c28c23fc00c84f`.
As in the previous slice, existing caught diagnostic exceptions elsewhere in
the reactor log are not represented as an exception-free runtime claim.

- [x] Replace the timestamp join with callback-synchronous immutable snapshots.
- [x] Preserve raw labels and identify physical endpoints/event-pre samples.
- [x] Version output names so historical exports are not replaced.
- [x] Pass all six focused recorder tests, including an actual solver fault.
- [x] Reuse the exact native negative-Q network and run matched/half-step probes.
- [x] Cold-repeat native half-step, half-Khv, nominal-zero and exact-zero probes.
- [x] Finish full regression and compare raw network/machine channels against
  the previous fifteen-event exports to prove observation-only non-regression.
- [ ] Fix the confirmed exact-zero REGCA1 recovery-selector bug in its own slice.
- [ ] Resolve native high-voltage correction and Q measurement differences.
- [ ] Add an instantaneous algebraic event-post observation path if required
  for complete cross-simulator event-side comparison; do not invent such rows.
- [ ] Implement the separately identified current REGFMA1 native schema.
- [ ] Resolve severe full-system faults and obtain independent system acceptance.

Native scripts, inputs, manifests and raw trajectories remain in the ignored
private verification archive `2026-10-17-endpoint-alignment`. No existing
reference checkpoint, physical parameter or comparison tolerance is changed.
