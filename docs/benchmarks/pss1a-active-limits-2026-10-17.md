# PSS1A active limits and voltage-cutout verification

## Scope

This extends the [exact typed PowerWorld playback slice](pss1a-exact-powerworld-playback-2026-10-17.md)
with independently active output limits and voltage gates. It does not establish
native PTI PSS1A DYR cardinality, acceptance of other input selectors, lifecycle
coverage, or complete network-trajectory parity. No production equation, gain,
initial state or physical parameter was fitted to a reference.

The existing two-bus GENROU/ESST1A case, speed input, fourteen-value typed PSS1A
construction and three-cycle Bus 1 fault are unchanged. Three declared branch
discriminators change only the parameters needed to activate the target branch:

- `limits`: Lsmax = 0.01, Lsmin = -0.01, both voltage gates disabled.
- `upper-gate`: Vcu = 1.002, Vcl = 0, original ±0.05 output limits.
- `lower-window`: Vcu = 2, Vcl = 0.99, original ±0.05 output limits. The upper
  threshold is outside this test's voltage range; the lower threshold is active.

Every profile runs at 0.5 and 0.25 ms, twice cold per step. All **twelve runs**
finish with byte-identical full CSVs within each pair. Each run has 25 channels,
with 2,003 or 4,003 raw rows, including both instantaneous event-post records.
The independent audit verifies exact model type, all declared fields before and
after initialization, input and canonical artifact hashes, named state columns,
finite complete trajectories, completed solve and unchanged executable identity.
Automatic correction is disabled throughout.

The simulator remains PowerWorld Education/Evaluation 24, version `24.2026.9.3`,
executable SHA-256
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
The public reference class freezes six complete raw-source digests and twelve
derived input/checkpoint fingerprints. Private artifacts preserve raw outputs,
runnable scripts/specs, logs, exported parameter records and the independent audit.

## Two important discriminators

The [official PSS1A diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS1A.htm)
places the magnitude limiter after the final lead-lag. PowerWorld's published
LL2 signal is therefore the **unclipped** lead-lag output, while Vstab is clipped
and then gated. Comparing that published signal with InterPSS's already-clipped
`FilterControlBlock.getY()` initially gives a misleading 0.02494 pu error.
The test observation now uses the existing realization's
`x + K*(T3/T4)*u` (or algebraic `K*u` if T4 is zero). No runtime integration or
limiter equation changes. The existing no-limit reference still passes unchanged.

The official diagram also appears to use Vcu in its lower-cutoff expression.
The `lower-window` runtime discriminator shows that the installed simulator
instead cuts out below **Vcl** and enables output inside the window. The existing
InterPSS Vcl comparison agrees with the recorded outputs; the diagram typo is
not copied into the model.

A separate attempted lower-only profile, Vcu = 0 and Vcl = 0.99, fails PowerWorld
validation because Vcl exceeds Vcu. Its empty trajectory is rejected by the
runner and retained as failed evidence. The valid window above is an explicit
new profile, not a silent automatic parameter correction or replacement of
that failed run. The exported values remain exactly 2 and 0.99 after initialization.

## Active branches and trajectory results

The raw PowerWorld output demonstrates both limit directions and release:
498 upper and 604 lower saturated regular endpoints at 0.5 ms; 994 upper and
1,207 lower endpoints at 0.25 ms. The lead-lag signal continues beyond the
output limits, proving that output clipping must not freeze its internal state.

The upper cutout is active with nonzero internal signal at 982 and 1,964 regular
endpoints. The lower cutout is active at 234 and 467 such endpoints. The permanent
checks independently require enabled and disabled output regions, continued
internal-state evolution during cutout, both limiter directions and release,
and algebraic clipping/gating at every replay endpoint.

The exact recorded **speed and terminal voltage** are replayed, without fitting
or substituting an input waveform. All six published stabilizer states and
Vstab are scored at 201 fixed 5 ms checkpoints per profile. All regular endpoints
are also scored in the archived diagnostic reports. The first duplicate at an
event is event-pre; two additional instantaneous event-post observations remain
in the raw reference and are not falsely equated with integrated endpoints.

Full regular-endpoint maximum Vstab errors at 0.5 / 0.25 ms are:

- Both output limits active: **4.1102e-6 / 7.2270e-7 pu**.
- Upper voltage cutout: **4.2504e-6 / 8.5727e-7 pu**.
- Lower voltage cutout: **2.9686e-6 / 7.6190e-7 pu**.

Every published channel improves under timestep halving. The permanent tests
use separate channel bounds and require at least 30 percent error reduction
in each channel. Fixed-digest positive and negative-mutation guards cover all
twelve derived input/checkpoint strings, with unknown dataset names rejected.
Repeated observations cannot mutate the CML state vector.

A local equation guard additionally checks exact cutoff equality, adjacent
representable values on either side, and disabled zero thresholds. Those exact
floating-point boundary probes are **local equation checks**, not a claim that
PowerWorld was separately forced to each representable boundary value.

## Regression and provenance

Seven focused PSS1A checks pass with no skips, failures or errors. The final
isolated gate passes **1,169 SMIB/model tests, zero failures/errors, one existing
opt-in diagnostic skip**. The same snapshot's plugin-core gate passes **133 tests,
zero failures/errors, six optional skips** (four KlusolveX checks and two AUX
fixture checks). All seven scoped source/inventory files match the final tested
snapshot byte-for-byte. Fresh Surefire XML counts, not quiet console output,
establish these totals.

Run IDs are `pss1a-active-isolated-smib-final-20261017` and
`pss1a-active-isolated-unit-final-20261017`, using
`extended-tests,isolated-verification` and the core override below. The SMIB
selection is `DynamicModelSmibTestSuite,DynamicModelCatalogTest`; the unit run
selects the complete `ipss.plugin.core` module with `-am`. The new active-profile
class is explicitly registered and its registration is guarded by the inventory.

The final reproducible snapshot includes production commit `94321b10c` and this
slice, using the isolated `1.4.3-regfma-native` core dependency. Thus the recently
committed renewable two-step integration and its tests are included.

The initial shared-checkout SMIB run has **1,173 tests, four failures, zero errors
and one skip**. The four failures are in concurrently added, uncommitted
`GensalConformanceTest.nativeMechanicalStagesConvertMechanicalPowerAndDampingToTorque`
parameterizations, not PSS1A. The failing reports are retained. They are not
removed, relaxed or represented as green. A final isolated snapshot excludes
those uncommitted GENSAL edits while retaining the latest committed production
and renewable tests. Concurrent GENSAL production work is neither overwritten
nor included in this PSS1A commit.

The shared-checkout plugin-core unit run passes **133 tests, zero failures/errors,
four optional skips**. Its optional AUX fixtures are present there; the isolated
checkout may report two additional optional fixture skips. No full Texas2k,
WECC or IEEE39 dynamic simulation is run for this slice.

The ignored private `2026-10-17-pss1a-active-limits` archive preserves all twelve
successful cold runs, the rejected lower-only probe, exact parameter exports,
frozen sources/specs, complete per-channel endpoint comparisons, both reference
audits, source snapshots, the failed shared-checkout gate and successful final
isolated reports. Its **1,639 evidence files** are sealed and hash-verified;
the SHA-256 inventory checks every archived file. Raw
simulator artifacts are not force-added to Git.

## Remaining work

- Complete other input-selector and lifecycle profiles; these are speed-input
  limit/cutout profiles, not exhaustive model acceptance.
- Resolve the complete SMIB network discrepancy and exact instantaneous
  event-post observation gap from the prior slice.
- Audit native input dialect separately from typed PowerWorld construction.
- Provide a production second-state observation accessor if needed; the
  existing test-only second-order coordinate audit remains test-only.
- Continue renewable evidence coverage and the other open plan requirements.
