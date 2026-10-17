# REGCA1 native network-convergence audit

Follow-up: the localized Iolim direction error is now implemented and fully
regression-tested in the [next model slice](regca-iolim-direction-2026-10-17.md).
The iterative high-voltage boundary remains open. The evidence and pre-fix
results below retain this report's original baseline.

Baseline: plugin `c06869052` (1.4.6), using the locally rebuilt core 1.4.3 at
`e886019ce`. This slice does not publish or change the core dependency.

## Correction to the preceding reference

The preceding [zero-Q selector report](regca-zero-q-selector-2026-10-17.md)
used a twice-repeatable native CSV with SHA-256
`24f709656b6885be8aa2b9e91ef1a299197626e445995a75c45f7ab3e5536385`.
Review of its complete native log finds **95 network non-convergence messages**,
from approximately 0.101 through 0.1245 s. An initialized simulator, zero API
return codes, a complete CSV and repeatability did not establish run quality.
That reference is retained unchanged as diagnostic history, not accepted
transient evidence. The prior endpoint numbers and comparisons are historical.

The REGCA1 zero-Q directional-selector implementation is unchanged. Its public
final-recovery test now uses a clean reference, with the same 1e-4 bounds, not
the failed-network run. No physical parameter, dynamic equation, initial Q,
integration step or fault is adjusted in this slice.

## Fresh native probes

PSS/E Xplore 36.7.0 runs the same three-bus, 100-MVA, exact-zero-Q lossless
fixture at 0.25 ms through 0.5 s. The fault is j0.01 pu at Bus1, 0.05–0.10 s.
The native API's `dynamics_solution_param_2` documents INTGAR(1) as the network
iteration limit. Setting it to 200 eliminates the recognized non-convergence
diagnostics; a separate 400-iteration run produces the **identical raw CSV**.
Both budgets are cold-regenerated twice. All four logs have an initial-condition
O.K. marker and no recognized run-failure diagnostics. Every manifest's saved
input/output fingerprint is checked against the actual bytes.

| Profile | Iteration budget | Failed-network messages per run | Cold CSV SHA-256 |
|---|---|---:|---|
| Original baseline | Unchanged native default | 95 | `24f709656b6885be8aa2b9e91ef1a299197626e445995a75c45f7ab3e5536385` |
| Same baseline | 200 and 400 | 0 | `5a1d1a0724556c861c10f00e4a0693cfdc4fec13fa0eb32f92cd8fe73384d345` |
| Khv=0 discriminator | Unchanged native default | 0 | `3d61841b4c103bf28cc42291e4d00bf565850d90342c77165701fe5f74271321` |
| Accel=0.4 discriminator | 200 | 0 | `d12ad530569937847762d807b4fbd779bf3880c5744d9d1353aaf9ee09b1c1b8` |
| Capacitive-step / Iolim=-0.25 and -1.3 discriminators | 200 | 0 | `3766e5d03291aa57f88cb71f5d7eee873444150b4dc2043a101a733c23b4d50e` |
| Three-cycle fault / Iolim=-0.25 discriminator | 200 | 0 | `80e092f8bc0c3a523d1a0d301ca6b9067b1713ac128918f604683866641cd13a` |

Every setting has two identical cold CSV exports, 2,005 samples and eight channels.
The last time is 0.500009357929 s, reflecting native single-precision clock
drift. Two negative-time initialization samples and two repeated event times
are preserved. The capacitive profile is a +125-Mvar admittance step at 0.05 s,
left applied: it is **not** a three-cycle fault. Changing only Iolim from -0.25
to -1.3 yields the identical complete eight-channel CSV, despite reactive output
falling below -0.25 pu. Actual CON readbacks confirm both limits; token comparison
confirms no other DYR field changes. Native Iolim is therefore not acting as a
universal output floor on this profile. This does not prove that it is unused in
every native operating region. Do not silently map this result onto the
differently labeled WECC appendix limit.

The earlier Accel=0.4/default-budget and capacitive/default-budget runs have
54 and 118 non-convergence messages respectively. They are likewise retained
as rejected diagnostic runs, not accepted oracles.

Native model DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Probe script SHA-256:
`1764cf1973f34d8c00f93797eb0432fd26fd872f5aecebdd50fb1cfde1d57ee8`.
The native PSS/E model library PDF remains the installed 36.7 source identified
in the preceding report. Exact input files, requested solver settings, actual
model CON values, logs, raw outputs and manifests are preserved in the ignored
private archive `2026-10-17-regca-network-quality`; no existing reference is
overwritten. Simulator availability still does not permit full Texas2k native
simulation under the installed 50-bus license.

## Permanent quality gate

`PsseRunQuality` is a reusable public test utility and command-line check:

```text
java -cp ipss.test.plugin.core/target/test-classes org.interpss.core.dstab.reference.PsseRunQuality native.log native.csv 0.5 0.00001
```

It requires the English initialization O.K. marker; rejects recognized network
failure, suspect/failed initialization and Python traceback diagnostics; checks
unique nonblank channel names, CSV width, numeric finiteness, nondecreasing
time, a time-zero sample and the declared final time with an explicit clock
tolerance. It counts rather than drops negative-time and repeated-time samples.
The final-time tolerance is a clock allowance, not a trajectory tolerance.

Ten registered tests cover these checks. The CLI rejects the actual old baseline
at its first non-convergence diagnostic and passes all fourteen clean logs in the
inventory above. It also rejects all six known failed-network logs (three
twice-cold profiles). Their individual CLI outputs are archived. This is a **necessary run-quality
gate**, not an assertion of complete log-language coverage, hash provenance,
model parity, channel completeness or mathematical convergence. Those remain
separate checks. Existing historical embedded datasets are not retroactively
declared clean by this utility's presence; their original logs still need audit.

## Current InterPSS comparison and source contract

A fresh unchanged InterPSS run is compared to the 200-iteration native baseline
on nominal-step endpoints, retaining event-pre samples. Native instantaneous
event-post samples have no counterpart in this observation path. Maximum
native clock-grid drift is 9.36 microseconds. Thus this is a diagnostic
comparison, not complete event-side trajectory acceptance.

| Channel | Maximum absolute error (pu) | Physical time (s) |
|---|---:|---:|
| Ip regulator state | 0 | All compared samples |
| Iq regulator state | 0.02667334 | 0.13375 |
| Filtered V | 0.01047486 | 0.12300 |
| Terminal V | 0.01916784 | 0.11850 |
| Q | 0.13103134 | 0.11850 |

The clean native peak is still 1.20973337 pu at approximately 0.1005 s, so the
earlier peak was not solely caused by the later convergence failures. Final
recovery absolute errors are 0, 7.85e-5, 4.58e-5, 3.84e-5 and 7.81e-5 pu in
the listed order; all meet the unchanged final-point bounds. This does not
accept the early transient.

The [WECC January 2014 specification](https://www.wecc.org/sites/default/files/documents/meeting/2024/WECC-Second-Generation-Wind-Turbine-Model%20Spec-012314.pdf),
Appendix A, PDF pages 34–35 (printed A-1/A-2), was rendered and visually checked.
It places a retained reactive-power correction and voltage deceleration inside
the algebraic network iteration, then clears the working correction after
solution. Its lower-limit label is reactive power; the native model library
labels Iolim as current. The appendix also warns of software-dependent behavior.
Its generic acceleration notation alone does not prove a native Khv/Accel
mapping. PDF SHA-256:
`aef13405133f110351eeb341ffb4c674af5b498bd7881a936c03521e3584caea`.

[PowerWorld's REGC_A documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
describes voltage control in the network boundary and explicitly does not use
Khv/Qmin. Keep that vendor convention distinct. The native baseline's exported
correction is much closer to a power-coordinate correction than to a current
subtraction: maximum active-sample residuals are 2.80e-5 versus 0.02576 pu.
This is localization evidence, not permission to fit a gain or change the
dynamic Iq state during output reads.

Remaining implementation work: establish the native Khv/Accel/limit mapping
with isolated boundary probes, introduce an explicit idempotent network-
iteration lifecycle where required (including non-PSS/E builder paths), and
verify every state and limiter before repeating the full system gates.
No such production network-boundary fix is claimed in this slice.

### Current-limit sign localized for the next implementation slice

A further pair changes only Iolim to -0.25 in the original three-cycle fault,
without the capacitor. It produces the distinct repeatable CSV listed above;
both logs pass the quality gate. During 165 fault samples, the native regulator
state is greater than +0.3 pu, terminal voltage is below 0.1 pu and the exported
HV correction is zero, yet generated reactive current `Q/V` is +0.25 pu (maximum
residual below 1e-6). The limit is therefore active in the **positive generated-
current direction**, independently of high-voltage activation on this profile.
The actual Iolim CON readback is -0.25.

InterPSS currently applies `max(Iolim, generatedIq - correction)`, a negative
generated-current floor instead. The capacitive and fault discriminators together
localize a sign/coordinate error in that limit, not an inactive parameter or a
universal reactive-power floor. Correcting the native generated-current ceiling
and testing its interaction with the HV correction is the next model slice.
This evidence commit intentionally does not change production equations while
the separately scoped model fix and its full regression remain outstanding.

## Regression

Focused quality, recovery and checkpoint-integrity tests pass: 15 tests,
zero failures/errors/skips. The full registered reactor exits zero: 2,722 tests,
2,633 passed, 89 skipped, zero failures/errors. Only fresh Surefire reports
written after this run started are counted; the unrelated untracked
WECC240/JAX test is excluded.

```text
mvn -q test -Pextended-tests --fail-at-end -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| ipss.plugin.core | 129 | 4 | 0 |
| ipss.plugin.3phase | 468 | 28 | 0 |
| ipss.test.plugin.core | 2,125 | 57 | 0 |

All six strict one-second flat cases pass with maximum voltage drift
3.12838644e-12 pu and speed drift 5.66213743e-14 pu. All six flat/fault/trip
smokes and all fifteen topology-fault execution gates pass. All fifteen complete
fault CSVs are byte-identical to the preceding committed production baseline;
this verification slice changes neither equations nor physical responses.
Broad execution gates are still not independent physical fault acceptance.

The complete log, fresh reports, enabled settings and fifteen traces are
preserved in the private archive's `completed-regression` directory. Source
opt-ins are restored to false after the run. Full regression log SHA-256:
`5078e6518cb826beb7dd4ef6f48e794d0305d81fe06618382907dd306aa3f81e`.
