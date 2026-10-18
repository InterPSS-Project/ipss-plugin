# REGFMC1U native import and coupled SMIB verification

Status: native reference import and hybrid network adapter implemented, with bounded
two-step flat/fault/trip comparisons. Approved renewable runtime coverage rises
from 23/27 to 24/27. This is not complete model verification; REPCGFMC1 plant
routing and precision/history/callback/lifecycle acceptance remain open.

## Exact native path

The existing [source contract](regfmc-native-contract-2026-10-18.md) and
[eleven-state kernel](regfmc-kernel-2026-10-18.md) remain authoritative for this
versioned implementation. Native USRMDL allocation is `101 1 3 34 11 27`,
with 43 fields after the model name: six allocation integers, three ICONs and
34 CONs. The reader checks every allocation field, numeric type and exact count.
Only REGFMC1U is accepted. Neither source-list REGFMC1 nor a standard record is
guessed as an alias. RAW machine-base ZSOURCE remains separate from DYR data.

The builder requires an active, existing bus/generator with no synchronous
machine or existing dynamic device. It rejects implicit RAW generator
transformers, preserves existing attachments, and honors GNET/MODREMOVE skips.
`Regfmc1Model` attaches the actual eleven-state kernel, not a substitute machine.
It requires finite positive machine/system MVA bases and a valid impedance
conversion factor. Physical injection is the jointly limited hybrid current,
converted once from machine to system base; the source includes the exact
Norton admittance cancellation already present in the core matrix.

The core device initialization API does not supply simulation timestep.
The kernel's new deferred-step initialization derives the equilibrium without
guessing a timestep, then applies the documented Te >= 2h policy on the first
positive predictor. It does not reset solved states or reference inputs.
An analytic short-Te unit check proves equivalence with explicit-step
initialization through both stages. Other native time-constant policies are not
replaced with PowerWorld corrections.

Monitoring exposes eleven semantic states, machine-base P/Q, absolute speed and
the required bus-device identity. Output queries do not mutate states. Trips
return zero physical output and do not advance retained controller states.
No automatic restart/reconnection policy is invented.

REGFMC1U has distinct GFL P/Q and GFM voltage/speed/manual P/Iq inputs. Its native
REPCGFMC1 plant controller is still missing. REPCA1 is therefore rejected in both
record orders instead of being silently attached with an unproved signal mapping.
The other established REPCA1 owner tests remain part of the focused gate.

## Native evidence and comparison policy

The same public three-bus RAW/DYR fixture uses 100-MVA system base, a 50-MVA
converter, 230-kV buses and ZSOURCE=0.015+j0.23 pu. InterPSS uses ordinary
modified Euler at 0.5/0.25 ms, with simulation end 0.30 s. The synchronous
reference is the existing very-high-inertia GENCLS, not a fake machine attached
to the converter. Converter buses without a synchronous machine are explicitly
permitted because their real dynamic generator device is attached.

Six imported runs cover stationary flat operation, a Bus 2 three-phase j1-pu
fault applied at 0.05 s and cleared at 0.10 s (three 60-Hz cycles), and a Bus 1
generator trip at 0.20 s. Existing immutable native flat/fault references are
reused. Four new cold native trip runs add two lifecycle datasets, each repeated
at both steps. They initialize O.K., pass `PsseRunQuality`, and have byte-identical
cold-pair CSVs with exact allocation and every ICON/float-CON checked.
The complete evidence now includes 28 native runs / fourteen datasets, with
four new runs rather than claiming the older references were regenerated here.

Trip exports retain 606/1,206 samples, all eleven STATEs, 27 VARs and physical/bus
channels: 48 channels plus time. Two negative history rows and three duplicate
API/event rows remain in immutable exports. Native inactive STATEs and VARs can
retain old values even when physical P/Q are zero. Lifecycle references are kept
separate from active-controller replay so retained VAR current is not mistaken
for continuing network injection.

Coupled comparisons cover V1/V2/V3, system-base P/Q and converter speed deviation
at all 601/1,201 regular endpoints, including the first step after each event.
Native float clock is mapped to the nearest declared fixed tick within 1e-5 s;
there is no fitted time shift, interpolation or initial-state adjustment.
Two negative history rows and two/three duplicate event-side rows are retained
and counted but **not matched to a separate InterPSS post-event callback**.
Full instantaneous post-event parity therefore remains open. The earlier
algebraic replay separately retains and tests both native event sides.

Per-channel fixed tolerances are approximately 25% above measured maxima.
Comparison traces are generated in `target/regfmc-import-traces/`, with maxima
and their times in test logs. Raw reference hashes are verified before use.

## Results and scope

Both InterPSS flat runs have zero observed converter-bus voltage drift. The fault
minimum Bus 2 voltage is about 0.92411702 pu. Both trip runs freeze all eleven
states while bus voltage changes and physical P/Q become exactly zero.

Flat maximum errors are V1=2.575e-7, V2=2.831e-6, V3=5.961e-8 pu,
P=2.116e-7 and Q=3.101e-5 pu on 100-MVA system base; speed is stationary to
roundoff. These initial power-flow offsets are retained rather than fitted away.

For the three-cycle fault, coarse/fine maximum errors are:

- V1: 4.441e-6 / 2.763e-6 pu.
- V2: 5.109e-6 / 3.952e-6 pu.
- V3: 2.454e-7 / 1.358e-7 pu.
- P: 6.001e-5 / 5.284e-5 pu, system base.
- Q: 5.581e-5 / 4.429e-5 pu, system base.
- Speed deviation: 8.943e-7 / 8.935e-7 pu.

The largest voltage transient error occurs near clearing; the speed residual
persists near 0.2345 s, consistent with the independently documented native
absolute-speed float floor. Uniform step-halving convergence is not claimed.
Trip maximum V1/V2 errors are approximately 6.162e-6/6.157e-6 pu, with V3
3.183e-7 pu. Trip P/Q differences are the pre-trip flat offsets; after trip both
tools report zero. Full current-limited severe-fault coupled behavior is still
outside this mild-fault import checkpoint, despite its earlier native algebraic
and kernel evidence.

## Validation and remaining work

The final acceptance snapshot runs 75 focused tests, 299 plugin-core tests
(six optional skips), and 1,348 registered SMIB/model/solver tests (one existing
diagnostic skip), with no failures or errors. The two-step imported comparisons
are included in both the focused and registered SMIB gates. The later wording
policy change is included in this retested source snapshot.

The source audit checks seventeen scoped source/resource files, verifies the
remaining baseline Java/POM files, checks every native file hash and all
fourteen byte-identical cold pairs, and rechecks the preceding sealed kernel
archive. The core dependency remains unchanged. Concurrent exciter edits in the
shared parser/builder/suite are excluded from the tested model snapshot and commit;
they remain in the worktree for their own validation.

Reproducibility bundle: local private archive
`verification-artifacts/2026-10-18-regfmc-native-import/`, with the exact tested
sources, reports, raw native inputs/outputs, runnable probes, six imported
comparison traces, validation summary and SHA-256 inventory. Exact simulator
provenance remains in its private manifests, not public prose.

- [x] Exact native import, allocation/cardinality/type guards and non-overwriting attachment.
- [x] Base/Norton, monitoring, removal, invalid-MVA, pure observation and trip guards.
- [x] Actual-first-step Te correction without state/reference reset.
- [x] Coupled flat/fault/trip comparisons at both steps and cold native trip artifacts.
- [ ] Exact REPCGFMC1 controller and compatible plant measurement/reference routing.
- [ ] Broader coupled selectors/current limiting, reference disturbances and reconnect.
- [ ] Additional native correction/non-default initialization discriminators;
  resolve precision/history/instantaneous callback gaps and independent PowerWorld
  acceptance before claiming complete verification.

HPLNTD, REPCCU and REPCGFMC1 remain missing. No full Texas2k dynamic simulation
is included; its source-model inventory remains a coverage requirement.
