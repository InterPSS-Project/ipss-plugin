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
The native library SHA-256 is
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

The full plugin suite needs local loopback socket access for Jena's HTTP
initialization even when importing local CIM fixtures. Without it, 74 parser
tests fail at infrastructure initialization; that sandbox run is not used as
the model regression result.
