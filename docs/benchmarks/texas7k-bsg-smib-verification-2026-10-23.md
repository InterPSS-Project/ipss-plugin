# Texas7k import and BSG response verification

## Scope and status

The supplied original `Texas7k_20210804` case contains 6,717 buses. Its power flow
converges with a 0.001 pu mismatch tolerance. BSG response handling is implemented
and independently exercised on a three-bus SMIB. Full Texas7k dynamic accuracy
is **not accepted**: its dynamic file has the coverage and data issues listed below.

The input files are preserved unchanged under
`ipss.test.plugin.core/testData/private/texas7k/`. They are private test dependencies,
not checked into Git. Stage another local copy with:

```powershell
python tools/stage_texas7k_case.py <supplied-case-directory>
```

The helper copies only RAW, DYR, BSG and the two preparation IDV files. It refuses
to overwrite changed inputs and records relative filenames and SHA-256 hashes.
It also reads the companion dynamics AUX without copying the full evidence tree.
All 120 BSG entries match its 120 `Fixed` generator responses exactly, including
unit IDs other than `1`.

## BSG contract

A legacy record has four whitespace-separated columns:

```text
120493 "BRACKETTVILLE 4 2" 13.80 "1"
```

The identity is bus number plus unit ID; the descriptive name is retained as
metadata. A four-column record requests fixed response. The reader also accepts
an explicit fifth code: `0` normal, `1` down only, `2` fixed. Invalid fields,
conflicting duplicate records, missing generators and unsupported controller
targets fail explicitly. Identical duplicates are idempotent. The supported
runtime targets are REGCA1 with REECA1, REECB1 or initialized constant commands.
Synchronous-governor BSG actuation is not implemented by this change.

Pass a `.bsg` as an additional `PSSEMultiFileLoader.loadDStab(...)` argument, or
place `<dynamic-stem>_basegen.bsg` beside the DYR. An explicitly supplied BSG takes
precedence over sibling discovery. The loader applies response
policy after attaching models and before dynamic initialization. Its
`getLastResponseEntries()` and `getLastDynamicImportReport()` expose what was
applied and what failed to attach. Configure response policy before initialization.

For REEC controls, down-only changes the effective upper active-power bound to
its initialized order; fixed changes both bounds. REPCA uses zero increment for
that same initial operating point. The immutable model input data are preserved.
Fixed response prevents active-power order from responding to frequency, including
plant droop control. Renewable model families can provide frequency support in
other configurations. Current limits, voltage response and converter dynamics
remain active, so fixed response does not imply constant terminal MW during a fault.

This interpretation also agrees with the documented
[generator response-limit modes](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Overview_Generator.htm)
and [BASEGEN helper export](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Data_GEDYD.htm).
The companion AUX provides the independent case-specific witness for the legacy
four-column file.

## Independent SMIB check

Twenty profiles are each executed twice from a fresh reference-engine process:
normal, explicit fixed, explicit down-only, native flag 1 and native flag 2;
positive and negative 0.1 pu plant-reference steps; 0.5 ms and 0.25 ms integration
steps. Each run lasts 0.5 seconds with a fault from 0.20 to 0.25 seconds. The plant
reference changes at 0.05 seconds through its documented input VAR. Changing the
downstream electrical input would be overwritten by the plant controller and
would not be an adequate discriminator.

All 40 runs pass the reference engine's initial-conditions check and network
convergence checks. Entered constants equal their initialization readback.
Every sample and duplicate event timestamp is retained. Cold repeats have zero
maximum difference in all recorded states. Exact engine/library/document
provenance, scripts, inputs, readback, full trajectories and hashes are retained
in private manifests under `testData/private/bsg-smib-v2/`.

| Check | Result |
| --- | --- |
| Native flag 1 | Down-only response; blocks increase and permits decrease |
| Native flag 2 | Fixed response; blocks both reference directions |
| Native flag 2 vs explicit fixed limits: active-power order | Maximum difference 1.4901e-8 pu |
| Native flag 2 vs explicit fixed limits: terminal P | Maximum difference 5.9605e-8 pu |
| Native flag vs explicit limits: bus voltage | Maximum difference 1.1921e-7 pu |
| Java fixed active-power order vs reference | Maximum difference 1.4901e-8 pu |
| Java fixed plant active-power output increment | Zero |

`BsgSmibVerificationTest` replays every electrical and plant state in both staged
modified Euler and the existing two-step diagnostic scheme. The general replay
state gate is 0.004 pu; the fixed-order and output-increment assertions are much
tighter. The staged voltage-filter error decreases from about 0.00144 to 0.000741
pu when halving the step. The fixed-order result is 1.49e-8 pu at both steps;
these different observations must not be presented as one overall accuracy number.
A further production-solver SMIB test imports the BSG automatically, changes the
plant reference and applies a fault. Its order stays fixed while terminal power
changes by more than 0.02 pu.

A separate reference run without an electrical-control record initializes and
retains constant converter current commands through the fault. The importer now
uses this behavior only when no electrical record was declared for that unit;
a malformed declared record remains an import failure. Optional upstream plant
and wind controls do not replace the missing electrical-control port.

## Texas7k gates and remaining limitations

`Texas7kSimulationTest` checks power-flow convergence, the 120 BSG assignments,
coverage reporting and a 0.1-second diagnostic no-event run with selected voltage
and machine channels. These are execution diagnostics, not a full-network
trajectory accuracy benchmark. This case is not assumed to be an equilibrium fixture.

| Dynamic import disposition | Records |
| --- | ---: |
| Attached | 2,555 |
| Unsupported EXPIC1 | 86 |
| REAX3BU1 / REAX4BU1 attachment failures | 13 / 2 |
| WTTQA1 data errors | 18 |
| IEEEST without an excitation input port | 31 |

The WTTQA errors include non-increasing power breakpoints and a selector outside
its documented domain. Inputs have not been silently corrected. The 31 stabilizers without a loaded excitation port are reported explicitly.
Some can depend on currently unsupported exciter records; this does not establish
that fixed excitation was intentional in the supplied case.
The full-case test preserves this gap inventory and refuses to classify the
import as strictly complete. EXPIC1, REAX attachment and the torque data issues
must be resolved before a full-case accuracy or performance acceptance claim.

One IEEEG1 governor previously failed initialization because its accommodated
limit and CML state used algebraically equivalent expressions with different
floating-point operation order. Calculating the limit in the same order as the
state removes that rounding discrepancy. The partial-case diagnostic now initializes
without disabling any governor; the independent governor conformance gates also pass.

## Validation

The complete plugin test inventory passes: 4,078 Java tests passed and 86 skipped;
all 396 Python tests passed. The main reactor run was interrupted by a tool-session
refresh after 635 integration classes completed. A successful closure ran its
remaining 37 classes; all 672 integration classes are accounted for, with no
failure or error. The six existing Texas2k smoke gates and strict ALLSYM equilibrium
gate passed. Sources stayed unchanged across validation. Local build/install passed,
and installed main class hashes match the tested build.

The [checkpoint](checkpoints/texas7k-bsg-smib-verification-2026-10-23.json) records
module counts and private evidence hashes. The core engine source and version are
unchanged. The new case and SMIB tests explicitly select the pure-Java solver.
The neutral-language check passes with 577 documents/new Java files checked.
