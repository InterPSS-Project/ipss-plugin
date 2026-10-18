# Network-iteration lifecycle: integration with newer core

## Exact scope

This follow-up combines tested lifecycle commit `95051f797` with core master
`124bca40f`, preserving both histories. The merge is prepared in the separate
`codex/network-iteration-lifecycle-20261017` checkout, not in the concurrently
used original core master. Before testing, the staged source tree is recorded as
`1dcaf92677330e8f2a331b353f2ae1057757a156`.

The three newer core commits are:

- `3681e4e50`: CML expression parsing/allocation reduction, with symbol and
  expression-order regressions.
- `f7b082ed0`: count aggregate fixed/switched/SVC shunt admittance only once in
  sequence and dynamic-network paths.
- `124bca40f`: retain unmodeled generation as an active constant-power negative
  load without disabling a modeled generator sharing the bus; avoid duplicate
  equivalents on repeated initialization.

There are no merge conflicts and no additional model equations or tolerance
changes in this integration slice. No production model opts into the lifecycle.
The converter high-voltage, low-voltage and native REGFMA1 schema gaps remain
open as recorded in the development plan.

## Reproducible validation isolation

The core build uses an unpublished local Maven coordinate
`1.4.3-lifecycle-integration`, selected using `-Drevision`. This prevents
replacement of the existing local `1.4.3` binary during concurrent work; it is
not a published release or permanent version bump.

```text
mvn -q install --fail-at-end -Drevision=1.4.3-lifecycle-integration
```

The complete core run exits zero: **2,939 tests passed, zero skipped,
failures or errors**. Its JAR SHA-256 is
`6afd171679b2d3be70f665000099a3d5e5908d5c8c8a6e3bb49e5ade1784330b`.
The full core log SHA-256 is
`424572c54a09de8c6d925609789bdfc02435bf0b78f359251d396d65268230c1`.

Plugin verification uses a clean managed checkout of `3453ae674`; concurrent
REECA1/parser/test edits, public solver-visibility edits and the untracked
reference runner are absent. Only the two existing JSON opt-ins for the strict
flat and topology-fault gates are enabled. Private case inputs and independent
reference fixtures are accessed through directory junctions to their existing
locations; no original fixture is rewritten.

```text
mvn -q test -Pextended-tests,isolated-verification
  -Dverification.run.id=merged-lifecycle-20261017
  -Dipss.core.version=1.4.3-lifecycle-integration --fail-at-end
  -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
  -Dsurefire.failIfNoSpecifiedTests=false
```

The explicit JAX exclusion is unchanged from the previous run; that unrelated
untracked test is not present in the clean checkout. All committed test classes
remain selected. Separate generated-output paths protect this run from default
IDE rebuilds. Explicit test-written outputs remain local to this verification
checkout, so they do not overwrite prior Texas2k traces in the shared checkout.

## Failure found by combined validation

The first complete plugin run exits one: 2,731 reported tests, 91 skipped,
one assertion failure and no errors. Its 2,639 passes include the six-case
flat/fault/trip smokes, all six strict flats and all fifteen topology faults.
The only failed assertion is SVSMO1T2 Bus 4 voltage: maximum error 0.281564599 pu
at 0.0770000666 s, above the unchanged 0.27-pu diagnostic bound. It is not
discarded or called a clean integration. The failed run is retained privately
under `2026-10-17-merged-network-iteration/failed-before-load-law-fix`, with 1,515
hashed artifacts including the exact core binary/sources, fresh reports,
configuration, log, strict-flat rankings and complete fault traces.

The clean checkout has two additional Texas7k AUX-converter skips because its
sibling-directory RAW fixtures are absent. The previous shared-checkout total
also included one temporary `Bench2PreSolveMismatchSmokeTest` that is not in
committed source and is absent here. Neither difference suppresses a committed
dynamic test. Fresh XML counts, not Maven's aggregate duplicated-suite counter,
are used throughout these reports.

### Documented benchmark load-law correction

The frozen native SVSMO1T2 probe calls CONL with four zero percentages. The
installed PSS/E 36.7 API documentation describes those entries as the P/Q
fractions converted to constant current and admittance, respectively. Zero
therefore preserves the original constant-MVA load. The InterPSS test instead
left the default all-loads-in-Y representation enabled, making the load constant
impedance. It did not represent the same network response.

The installed HTML API page `psspy.conl.html` SHA-256 is
`408a09a934f4d1b39ae9be2b4f50970d512f1a3e9e66cbb5d6154f4b6c202abb`.
This local primary source avoids relying on a guessed interpretation of the
conversion flags. The PDF-reading route was inspected, but the installed HTML
API reference supplied the exact callable contract directly.

The benchmark now explicitly selects nonlinear constant-P and constant-Q loads,
with the all-loads-in-Y option disabled. A new perturbation test proves that
both load components remain 0.45 pu after voltage is reduced by ten percent.
Changing only the CONST_P enum while keeping all-loads-in-Y enabled produces
the same old trajectory; that failed discriminator is retained. Explicit
constant-Z outside Y also reproduces the old discrepancy, localizing the
load-law effect rather than attributing it to the representation switch alone.

With the correct constant-MVA load law, voltage error falls to 0.152376265 pu,
sensor-state error to 0.028223177 pu and gain-state error to 0.033632381 pu.
The susceptance-state error remains 0.165173574 pu; it is not hidden.
The voltage, sensor, PI, command, gain and Mvar diagnostic envelopes are
tightened, not widened; unchanged published parameters and native checkpoints
are retained. These are still diagnostic non-regression bounds, not tight
independent model-parity acceptance.

Two fresh native PSS/E 36.7 cold runs reproduce the old full reference exactly:
both 1,605-sample, 37-channel CSVs have SHA-256
`917a22970f52cf3b9a2927e2af6281fe12352648d1b3dd6059278122d90a0e87`.
Both pass the permanent native log/CSV quality gate: two initialization-history
samples, two repeated-time samples and final time 0.80000936985 s. The first
wrapper attempt completed simulation but failed while copying a file onto
itself; that attempt is preserved separately and is not counted as a successful
wrapper run. The corrected wrapper only redirects output and does not change
the native profile or simulator settings.

The initial operating point still differs: InterPSS Bus 4 is 1.021873761 pu,
while native is 1.024996519 pu, with corresponding B/Vref differences. Tightening
only the electrical load-flow tolerance to 1e-10 does not remove this difference;
it is not resolved by fitting initial B or model states to the native export.
Native point-control initialization and remaining transient differences require
separate diagnosis before SVSMO1T2 can be considered independently accepted.

### Texas2k differences caused by newer core

All fifteen complete fault exports differ from the prior lifecycle baseline.
They use the same original input hashes, step, event grid and channels. This is
not a no-op lifecycle effect: the newer core also changes physical shunt
aggregation and unmodeled-generation conversion.

| Event | Previous final voltage | Merged final voltage | Previous maximum speed deviation | Merged maximum speed deviation |
|---|---:|---:|---:|---:|
| Case 5 weak grid | 1.03031842226 | 1.06418648954 | 0.0314966831090 | 0.0219352290486 |
| Case 6 GFM terminal | 1.68147241035 | 1.51262038765 | 0.0268714872456 | 0.00797729368954 |

These are execution diagnostics, not proof of improved physical accuracy. The
Case 6 overvoltage remains severe; independent system parity is still open.

## Completed rerun and exact integration boundary

The corrected full clean run exits zero at 20:52:53 local time. Fresh XML totals
are **2,732 tests: 2,641 passed, 91 skipped, zero failures/errors**:

| Module | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| ipss.plugin.core | 129 | 123 | 6 | 0 |
| ipss.plugin.3phase | 468 | 440 | 28 | 0 |
| ipss.test.plugin.core | 2,135 | 2,078 | 57 | 0 |

All six-case flat/fault/trip smokes, six strict one-second flats and fifteen
topology faults execute. The fresh flat rankings show maximum voltage drift
1.3766765505351941e-14 pu and zero recorded machine-speed drift. All fifteen
complete fault trajectories repeat byte-for-byte against the first merged-core
run, proving that the SVSMO1T2 benchmark-only correction did not alter Texas2k
physics. They differ from the older core baseline as described above.

The successful plugin log SHA-256 is
`b91a33e6a51b49298ce3e5041cff44100911a71a162ae76926545d1c9a1bb3f7`.
The completed private bundle under
`2026-10-17-merged-network-iteration/completed-regression` contains **1,549
hashed artifacts**, all rechecked: exact core binary/sources, fresh core/plugin
reports, enabled settings, rankings, complete fault trajectories, native cold
runs/quality checks and localization sources/outputs. An initial incomplete
archive attempt is preserved separately; it is not the successful manifest.
Private opt-ins are restored to false after archiving.

Core merge commit `830b47d0d` preserves parents `95051f797` and `124bca40f` on
`codex/network-iteration-lifecycle-20261017`. Its source tree is exactly the
tested `1dcaf92677330e8f2a331b353f2ae1057757a156`. The ordinary local core 1.4.3
binary is not overwritten; the tested merged binary remains available under
the explicit `1.4.3-lifecycle-integration` coordinate shown above.

The benchmark source in the main plugin checkout matches its tested snapshot
exactly after CRLF/LF normalization. While this run was executing, newer plugin
commits `511618835` (native wind-speed dependency) and `be8bd790c` (immutable
reference producer) landed in the shared checkout. They were intentionally
absent from the clean `3453ae674` verification baseline and are **not** certified
by this green run. The separate supplied-reference report also records newer
core `9893d5bae`; it is outside this tested merge.

In particular, the native wind-speed finding questions the stationary-flat
premise for some supplied profiles. The old-convention stationary flat results
above must not be represented as validation of the newer native startup
behavior. Reconcile that gate with independently observed native initialization
and rerun combined latest-source acceptance before declaring current-main
Texas2k coverage complete. See the
[separate supplied-reference investigation](texas2k-native-reference-comparison-2026-10-17.md).

Still open: latest-source combined validation, native wind startup and fault
convergence, tight SVSMO1T2 point-control/trajectory parity, REGCA1 iterative
boundary behavior, native REGFMA1 schema and independent Texas2k physical
acceptance. A scoped green regression does not close these requirements.
