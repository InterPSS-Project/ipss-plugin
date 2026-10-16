# Dynamic-model zero-time audit — 16 October 2026

The JAX-EMT finding also occurs in InterPSS: setting a differential equation's
derivative to zero is insufficient when its state has become an algebraic
output. A bypass must read the current input, and downstream feedback, machine
inputs and diagnostics must use that physical output. Inactive storage must not
be mistaken for the output. No epsilon time constants were introduced.

## Scope and evidence

The reproducible source screen covers **690 Java files**: 252 in core's DStab
tree, 422 in plugin's DStab tree, and 16 in the three-phase dynamic tree. This
includes interfaces, parameter classes and support code; it is not a count of
690 independently verified models. The accompanying
[inventory](dynamic-zero-time-inventory-2026-10-16.json) records paths, hashes,
time-constant divisions, zero branches, output methods and CML use. A static
screen is not a proof that every parameter combination is correct.

Manual review followed the suspicious branches into their physical output,
feedback and initialization paths. The starting evidence was JAX-EMT commit
`78cb65ea63b26901c89dda5df704e6a7354a9f16`, its zero-time output contract and
native SMIB audit. The installed **benchmark model documentation** was used to check
native parameter restrictions. In particular, AC1C/AC2C/AC3C/AC7B require
positive exciter field time TE, REXSYS requires positive TE, and HYGOVR1 requires
positive TW (printed manual page 405). Zero gain, disabled washout and zero
discrete delay are distinct from zero lag time.

## Repairs

| Implementation | Defect and repair | New evidence |
|---|---|---|
| Core washout `K Ts/(1+Ts)` | T=0 divided by zero and exposed a stale state. Output and derivative are now zero. | Analytic block regression |
| Core lead-lag | Zero denominator time bypassed non-windup output limits. The algebraic output now retains its limits. | Upper/lower limit regression |
| Core second-order transfer block | Zero quadratic coefficient incorrectly rejected the valid first-order reduction. Initial output/derivative states were reversed; corrector derivatives used different stage vectors. | First-order reduction, nonzero equilibrium and two-step Heun checks |
| Annotated CML controllers | Output queries could use stale inputs of bypassed blocks. Refresh algebraic inputs and static feedback without executing state advances or overwriting predictor derivatives. Iterate the output to a numerical fixed point; report nonconvergence as NaN instead of silently returning an unsettled result. | Live input, preserved state/history, repeated-query tests; SCRX/ESST4B native replay |
| REXSYS, TA=0 and FBF=0 | Feedback read inactive VR storage. Solve the bounded algebraic regulator/feedback loop and use the resolved regulator in the feedback derivative. | Analytic loop equation and native replay |
| GASTWDD/GAST2AD, TCD=0 | Mechanical output used cached turbine output and speed; GASTWDD also sampled its bypassed transport path at the wrong predictor time. Evaluate the live delayed fuel output at the active stage time, preserve inactive gas-lag storage, and make zero-duration steps inert. | Live-speed/state-preservation tests and native replay |
| REGCA1, TG/TFLTR=0 | Norton injection and diagnostics could read stale current/voltage storage. Read current commands and bus voltage through the zero-time algebraic path, including LVPL, without advancing storage. | Live command/voltage, limits, repeated-query and unchanged-storage tests |
| PSS4B/PSS4C | Bypassed filter paths were only evaluated by time stepping. Output queries now evaluate their transfer paths without advancing their memories. | Zero-lag PSS4B analytic test; existing PSS4B/PSS4C trajectory regressions |
| H6E's existing TW=0 extension | Flow froze while the gate moved. Enforce steady hydraulic head: flow = flow area × sqrt(operating head), and use it in power and diagnostics. | Moving-gate, steady-head analytic test; existing positive-TW trajectory regression |
| HYGOVR/HYGOVR1 | Accepted TW=0 despite the native model's positive-TW requirement, then held flow fixed. Reject that invalid parameter. | Parameter validation regression and native manual |

Other reviewed paths already distinguish these cases: the AC1C/AC2C/AC3C/AC7B
implementations compute their live algebraic regulator outputs; HYG3, PIDGOV
and WPIDHY have explicit zero-water-time output paths; synchronous-machine
time constants are checked as physical positive parameters through their data
checking chain. Renewable-controller sampled inputs and relay/timer memories
must not be globally replaced by a `step(0)` call. No such blanket change was
made.

The CML refresh retains the existing supported-block boundary. Controllers with
custom dynamic blocks that cannot prepare inputs independently retain their
existing stepping path. A generic noncontractive algebraic feedback loop is
not newly qualified by the fixed-point refresh; model-specific implicit loops,
such as REXSYS, need their own equation solve.

## Benchmarking results

Nine fresh benchmarking SMIB profiles were generated twice in separate processes.
Every numeric sample and initialized constant reproduced exactly. Both native
generation and InterPSS replay use **0.0005 s** steps. Native network settings:
**1e-6 tolerance, 100 maximum iterations**. Duration is 0.2 s, with a +0.01 VREF
step for exciters or −0.02 GREF step for governors immediately after the 0.05 s
sample. These are the JAX audit's model parameters, without fitting.

InterPSS uses strict two-stage modified Euler (Heun). The replay supplies the
recorded voltage, speed, electrical power and field current and initializes
from the same physical boundary. GAST2AD's public power-reference API receives
the documented conversion from the native speed-reference change. Every one
of the 401 samples is scored; no event samples or output offsets are discarded.
The native single-precision clock is retained alongside the integer-step clock.

This isolates controller equations. **It is not a coupled-network parity
claim.** Native internal state arrays are archived for diagnosis, but are not
declared cross-simulator state-coordinate matches. REGCA1's zero-lag case and
H6E's zero-inertia extension are equation-tested, not newly native-qualified.
The AC profiles use TA=0 and positive TE; no native claim is made for TE=0.

| Model | Compared output | Maximum absolute error (pu) | Time (s) | Regression limit (pu) |
|---|---|---:|---:|---:|
| SCRX | EFD | 0.0005593273 | 0.0510 | 0.0006 |
| ESST4B | EFD | 3.330111e-06 | 0.0505 | 4e-06 |
| AC1C | EFD | 1.729507e-06 | 0.1985 | 3e-06 |
| AC2C | EFD | 2.399613e-06 | 0.1800 | 3e-06 |
| AC3C | EFD | 8.393507e-05 | 0.0505 | 0.0001 |
| AC7B | EFD | 4.848149e-05 | 0.0505 | 6e-05 |
| REXSYS | EFD | 4.273007e-05 | 0.0725 | 5e-05 |
| GASTWDD | PMECH | 5.647744e-05 | 0.1760 | 7e-05 |
| GAST2AD | PMECH | 0.0001937825 | 0.2000 | 0.00025 |

## Regression results and limits

These are the initial audit results. The [release follow-up](#release-follow-up-pss3c-failure-resolved)
below resolves the PSS3C failure and records the subsequent release validation.

- Core full build/test/install: **2,904 tests, zero failures/errors**.
- Plugin complete registered suite, including `extended-tests`: **2,545 tests;
  2,436 passed, 108 skipped, one failure, zero errors**.
- The sole failure is the unchanged pre-existing PSS3C fault EFD mismatch:
  `0.0110020990` at `0.0985001102 s`, versus its `0.01` limit. The limit was not changed.
- All nine new boundary replay profiles pass their individual limits and
  repeated-output-query assertions. Both cold native generations match.
- IEEE39 was exported again using the unchanged modified-Euler default and
  `1e-6` network tolerance. Flat: **246/246** channels pass at both 0.25 ms and
  4.333 ms. Fault: **18** channels exceed limits at 0.25 ms and **128** at
  4.333 ms, the same counts as before this audit. There are no unmatched
  immediate post-event samples. These discontinuity-related parity gaps remain.

The private six-case Texas2k Series 24 dataset is absent from the configured
`testData/private/texas2k` root. Its six-case smokes, strict production-step gate
and 15-fault matrix therefore are not acceptance evidence for this change.
Existing model-specific regressions and the available IEEE39 exports provide
broader coverage, but cannot replace those missing gates. The source audit and
nine profiles do not establish benchmark reference equivalence for every dynamic model or
every combination of zero parameters.

## Reproduction and artifact policy

Public inputs are in `ipss.test.plugin.core/testData/adpter/psse/v33/zero-time`.
Exact numeric boundary checkpoints are embedded in
`EmbeddedZeroTimeBoundaryValues`, following the repository's existing reference
pattern. `ReferenceArtifactManifestIntegrityTest` verifies their SHA-256 hashes.
The benchmark model documentation SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.

The complete local archive is
`ipss.test.plugin.core/testData/reference/zero-time-audit-20261016/`:
producer, profiles, two cold runs, RAW/DYR, native `.out`, full native states,
logs, normalized CSVs, comparison results and hashed manifest. Simulator-driving
Python and full external trajectories remain private under the existing
`.gitignore` policy. The source-screen script is also archived there.

Using the archived benchmarking Python environment, run the archived producer
twice with different fresh output directories:

```text
python producer.py --profiles profiles.json --output native-new-a
python producer.py --profiles profiles.json --output native-new-b
```

Run core `mvn -o install`, then from plugin:

```text
mvn -o -pl ipss.test.plugin.core -am test -Dtest=ZeroTimePsseBoundaryReplayTest,ReferenceArtifactManifestIntegrityTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -o test -Pextended-tests --fail-at-end
```

An earlier sandbox run failed 74 parser tests because Jena 4.10's generic RDF
reader initialized an HTTP client even for local CIM bytes. On Windows, that
client creates a selector that needs a loopback connection; those infrastructure
failures were not used as the model regression result.

Local CIM and CGMES imports now share a direct RDF/XML language-reader path
that avoids HTTP-client initialization. DTD and external-entity rejection,
base-URI handling, namespace prefixes and typed RDF values are retained. A
fresh-JVM regression rejects selector creation, verifies both local parsing
paths still succeed, and confirms that HTTP-client initialization fails as a
positive control.

After this fix, `mvn -o -Pextended-tests test` passed under the restricted
sandbox permissions: 2,640 tests reported, 2,532 passed and 108 existing skips,
with zero failures/errors. The log is `target/cim-offline-full-tests.log`.

## Release follow-up: PSS3C failure resolved

The PSS3C failure reported above came primarily from mismatched load models in
the SMIB comparison. The native producer calls `CONL` with four zero percentages,
which preserves constant-MVA load (benchmark API documentation, `conl`, `LOADIN`).
The InterPSS test had used the default constant-impedance representation. The
test now selects constant active and reactive power and removes those loads
from the fixed network admittance matrix. It asserts the initialized load
components and requires network convergence to `1e-6`, with at most 100
iterations. RAW/DYR inputs, embedded native checkpoints, channel limits and
the existing event-window comparison rules are unchanged.

Three additional numerical defects were corrected and covered by independent
regressions:

- A zero-duration PSS3C refresh preserves positive-time-constant filter states
  and evaluates the compensated-frequency signal at the new terminal voltage.
- The compensated-frequency transducer corrector samples the predicted
  washout state, so its derivative uses the same Heun endpoint as the filter.
- `SecondOrderLeadLagBlock` calculates both corrector slopes before updating
  either state, preserving simultaneous modified-Euler integration.

Enforcing the network tolerance also exposed a core convergence-monitor defect:
the bus scan stopped at its first mismatch before saving the new voltage. A
nonlinear load could therefore converge numerically while the monitor continued
comparing against an obsolete iterate. The scan now checks and refreshes all
active buses on every iteration. Two regressions cover successful convergence
and rejection when the final bus has not settled within the iteration limit.
The simulation runner also stops and clears initialization after a failed
step, instead of retrying forever without advancing time; a third regression
covers that failure path.

Two internal system checks needed their numerical assumptions made explicit.
The IEEE39 compressor-motor regression now enforces a `1e-6` network tolerance
and passes its unchanged voltage checkpoint. The five-plant synthetic renewable
test retains its historical default `1e-4` tolerance and 20-iteration limit,
and refreshes its internal repeatability snapshot for the corrected stopping
rule. It is renamed to identify that repeatability-only purpose. No native
reference, assertion delta or physical recovery bound was changed.
That synthetic deep-fault case did not converge in separate mandatory-check
diagnostics: `1e-10`/100 iterations at `0.051 s`, and `1e-4`/20 iterations at
`0.0535 s`. This is a remaining limitation of the fixed-point network coupling,
not a zero-time-constant model fix or evidence of a converged benchmark-equivalent
trajectory. The existing test does not enforce convergence; its near-zero
fault-voltage snapshot must not be interpreted as an accuracy claim at the
number of printed digits. The PSS3C and IEEE39 motor checks do enforce their
declared network tolerance.

The original embedded comparison now passes all 19 channels. Its maximum EFD
error falls from `0.0110020990` to `0.0005039830` pu. No acceptance limit was
increased. This corrects the earlier interpretation of that failure as an
unresolved solver-parity difference.

Two fresh benchmarking runs used the same `0.0005 s` time step and `1e-6`
network tolerance as InterPSS. Their complete 2,005-row CSVs are byte-identical,
SHA-256 `03a024c51f45e1e1c14f5e043ccec9f917f41bbcfbf2d7105c3262b51f8a619e`.

Across 1,996 aligned rows under the existing event-window rule, all 19 channels
pass the existing limits. Maximum EFD error is `0.0006938271` pu; bus-voltage
errors are `0.0001852579` and `0.0000631741` pu; relative-speed error is
`1.174456e-6` pu. The denser native record includes points absent from the
embedded checkpoint set, hence the different maximum errors. These results
do not claim exact parity inside the excluded one-step event windows.

The producer, two native outputs, comparison script and per-channel errors
are retained locally under `target/pss3c-release/` and the private reference
archive, following the artifact policy above. The earlier IEEE39 parity and
missing-private-dataset qualifications still apply.

### Final release validation

Both complete registered suites passed before changing the versions:

- Core: **2,907 passed, zero failures/errors/skips** (`mvn -o install`).
- Plugin with `extended-tests`: **2,548 executed; 2,440 passed, 108 skipped,
  zero failures/errors** (`mvn -o --fail-at-end test -Pextended-tests`).

Core was then bumped to **1.4.2**, and plugin to **1.4.5** with its core
dependency set to **1.4.2**. Both release artifact sets were built and installed
locally. At the new coordinates, **9 core** zero-time/convergence regressions
and **29 plugin** model/reference/integrity/system checks passed. The PSS3C
Surefire runtime classpath explicitly contains `ipss.core.lib-1.4.2.jar` and
does not contain the prior core version. Core release commit: `ddd517b80`.

The full logs, release-build logs, exact native producer and per-channel
comparison are preserved in the private archive's `release-followup/` folder,
with a SHA-256 inventory. No claim is made that skipped private-data gates,
the existing IEEE39 fault parity gaps, or the synthetic renewable deep-fault
convergence limitation have been resolved.
