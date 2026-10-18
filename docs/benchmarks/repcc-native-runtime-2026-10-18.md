# REPCC standard import and bounded coupled runtime

## Status and exact source boundary

REPCC now has a dedicated standard-native parser, deferred REGCA1/REECA1
attachment, thirteen-state kernel composition and downstream reference routing.
The approved-family inventory advances to **26/27 runtime paths**; HPLNTD
remains missing. This is bounded runtime/evidence coverage, **not complete
REPCC verification or support for every source alias, host or initialization**.

The standard record retains exactly 35 mixed-type ICONs and 57 CONs in source
order. Integer fields, character circuit/shunt identifiers, finite values and
bounds are checked. Unused zero-bus shunt slots may use an unquoted zero ID, as
specified in the source. The source-list `REPCCU` name is retained in approval
metadata but is not invented as a native alias or user-model allocation.

Authority remains the installed Model Library section 27.4 and its diagram,
with document SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Executable/library provenance and runnable input/probe files are retained in
the private reference manifests. The separate PowerWorld dialect is not merged
into the native 57-CON schema.

## Production composition

- A new `RepccModel` owns the exact kernel and physical measurement boundary.
  Main branch P/Q/current, independent voltage/frequency buses, signed measured
  ends and machine/system base conversion reuse the independently checked
  physical boundary rather than a legacy plant-controller substitute.
- The kernel stores absolute machine-base active and Q/V commands. The model
  subtracts their initialized values once, and REECA adds those offsets to its
  initialized references once. Q-command versus voltage-command routing follows
  the downstream electrical-controller selector.
- Both modified-Euler stages reach the plant. A corrector cannot run without
  its predictor, and physical dispatch cannot run with a pending corrector.
  The converter's separate two-step path is explicitly rejected for this plant.
- REPCA and REPCC cannot overwrite each other's input port. Duplicate REPCC
  records, mismatched owners and incompatible converter/controller hosts are
  rejected. A plant record may precede its converter/electrical records because
  attachment is deferred until those records have been processed.
- The converter exposes stable prefixed plant named values. The kernel's
  thirteen dynamic coordinates remain distinct from the 32 shunt-monitoring
  values. Snapshots are immutable. An inactive generator does not advance the
  plant kernel or dispatch shunts.

The current import path admits **enabled active/reactive loops, positive
integral gains and disabled automatic MSS switching**. Unsupported combinations
are reported/rejected, not mapped to a different controller or initialized with
fabricated states. The separately tested shunt state machine and physical
admittance API are not relabeled as automatic runtime MSS acceptance.

## Cold references and coupled comparisons

Four new exact native datasets—flat and three-cycle fault, each at 0.5 ms and
0.25 ms—have eight cold runs. Inputs and probes agree across every pair. Three
complete raw CSV pairs are byte-identical. The fourth differs only in an
inactive recovery timer below 1e-18 seconds; both raw files and hashes remain
preserved. No model parameter, initial condition or reference trajectory is
rewritten to force agreement.

The three-bus SMIB has a 50-MVA converter on a 100-MVA system base, REGCA1,
REECA1, the exact REPCC plant and a high-inertia GENCLS reference. The Bus 2
three-phase fault is applied at 0.05 seconds and cleared at 0.10 seconds:
three cycles at 60 Hz. Fault impedance is **j1.0 pu on the system base**, not a
bolted fault; this bounded control-loop profile does not certify severe LVRT
or current-limited fault behavior. Simulation ends at 0.30 seconds.

Every raw row is compared: 605 at the coarse step and 1,205 at the fine step.
The two negative-time initialization-history rows are explicitly compared to
the initialized state. At each repeated event time, both pre-event and
post-network/pre-integration snapshots are checked. The test-only solver
observer reads the normal solver boundary; it does not advance the model,
alter events or replace the network solution. No event-side duplicate is
dropped or accepted using a broad time window.

Comparison channels are three bus voltages, physical converter P/Q on system
base and all thirteen plant states on their published coordinates. The actual
converter has no synchronous rotor-speed state; an artificial rotor-speed
comparison is not used to certify it. Four finite 19-column endpoint traces,
four 39-column paired-row comparisons with explicit history/pre/post side
markers, and the complete raw native traces are retained.

Maximum absolute errors:

| Profile | Bus 1 V (pu) | Bus 2 V (pu) | P (system pu) | Q (system pu) | Largest plant-state error |
|---|---:|---:|---:|---:|---:|
| Flat, either step | 2.575e-7 | 9.025e-8 | 2.116e-7 | 1.184e-6 | 2.367e-6 |
| Three-cycle fault, 0.5 ms | 5.678e-6 | 2.914e-6 | 9.752e-5 | 1.746e-6 | 5.162e-4 |
| Three-cycle fault, 0.25 ms | 6.658e-6 | 3.278e-6 | 4.946e-5 | 1.843e-6 | 2.628e-4 |

Each profile/channel has a separate explicit bound, approximately ten percent
above its measured maximum, with a small numerical floor for inactive states.
The earlier provisional diagnostic limit is not the acceptance gate. Maximum
error times are printed by the regression test.

The coarse/fine leading filter and active-command errors roughly halve, while
some voltage/frequency and initialization residuals reach a floor. Network
convergence, integration, precision and callback contributions are not fully
separated; bounded errors are not proof of full numerical parity. The
weakest-step voltage at Bus 2 is about 0.90958 pu; flat-run drift
is below 7e-16 pu over 0.3 seconds.

Tightening only InterPSS's initial load-flow tolerance to 1e-10 reduces the
flat Q difference from about 3.10e-5 to 1.18e-6 system pu, approximately 26-fold.
The former stationary offset was largely a load-flow convergence difference,
not corrected by tuning a controller. All model parameters remain unchanged.

Eight existing native foundation configurations also run stationary in the
coupled solver: line compensation, Q/PF control, lead-lag, droop, disabled
feedforward, disabled electrical feedback and missing-branch fallback. These
stationary checks do not establish disturbance acceptance for every mode.
Stage, incremental-reference, inactive-owner and port-ownership tests are
separate from independent native generator-trip/lifecycle acceptance.

## Remaining implementation and verification

- Automatic enabled-MSS initialization, the zero-time/startup endpoint,
  per-device fixed-shunt conductance retention at import and fully coupled
  switching. Preserve the source-correct discharge lockout; do not copy the
  documented native premature-reclosure discrepancy.
- Disabled active/reactive loops, proportional-only/zero-integral paths,
  PF-without-feedback and low-power combinations, initialization limit
  accommodation, further zero-time paths and active limiter/rate profiles.
- Additional compatible converter/electrical and type-3 hosts, charge/base
  profiles and exact source-listed alias/wrapper contracts.
- Longer-horizon flat/disturbance runs, bolted/severe faults, native coupled
  reference changes, generator trip/restart and other lifecycle
  profiles at both steps; tighter separation of integration and observation
  floors. Current inactive-owner checks are local tests only.
- An independently activating PowerWorld plant/shunt benchmark. Earlier
  non-activating PowerWorld shunt runs remain diagnostics, not lockout parity.
- HPLNTD's independent multi-aggregate schema, implementation and verification.

No full Texas2k dynamic simulation is required or run.

## Final gates and evidence archive

The final gates on plugin baseline `c8f866351` pass with zero failures/errors:
**102 focused checks, 344 plugin-core tests (six optional skips), and 1,404
registered SMIB/model/solver checks (one existing diagnostic skip)**. The focused
gate includes the newly committed governor history regressions as baseline
compatibility checks; this slice does not modify or restage that governor work.

The audit matches 26 owned Java/resource/fixture files to the tested snapshot
and 2,079 other baseline Java/POM files to Git, verifies every file in all eight
native run manifests, checks all four complete paired-row/endpoint traces and
rechecks seven earlier sealed plant archives. Generated support/evidence
documents are checked against their runtime catalogs. The private archive is
`2026-10-18-repcc-native-runtime`; prior raw references are never overwritten.
The tested RAW snapshot differs from the original native input only in CRLF
versus LF serialization. The audit compares record content with that explicit
line-ending normalization and retains both raw hashes; numeric input is not
changed.

All report classpaths use the frozen core artifact from commit `38e269ecf`,
SHA-256 `fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
The earlier accepted dependency remains unchanged. No production dependency
version or shared Maven installation is silently replaced. Build the plugin
with the companion core API from that commit or later; an older same-version
jar does not contain the physical shunt boundary's cache-update method.
