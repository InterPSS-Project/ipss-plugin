# REGFMB1U native import and network-device slice

Status: LOADABLE native 36.7 exact wrapper, with bounded partial native evidence;
not complete model conformance. Baseline `15e54ec71`. Approved native renewable
runtime-path coverage is now 23/27. Four remain missing: HPLNTD, REPCCU,
REGFMC1 and REPCGFMC1. Original source-list REGFMB1 spelling is retained, not
accepted as an inferred alias for REGFMB1U.

## Implementation

Add a real DynamicGenDevice adapter around the independently exercised
[fourteen-state kernel](regfmb-kernel-2026-10-18.md). The exact DYR contract is
USRMDL REGFMB1U with allocation 101 1 6 25 14 17, six ICONs and 25 CONs, no
defaults or extra fields. The descriptor's 37 parameters include the six wrapper
allocation fields. RAW machine-base ZSOURCE is retained; implicit generator
transformers are rejected. Missing, inactive, duplicate and synchronous-machine
hosts cannot be overwritten. GNET/model removal is honored before attachment.

Initialization derives machine-base P/Q once from solved system-base generation
and sets the core impedance multiplier before matrix construction. The adapter
injects physical machine current converted to system base plus the exact Norton
admittance cancellation. Outputs are algebraic and do not modify dynamic states.
StateMonitor identity is present; the first development attempt exposed and
fixed the missing identity key. Trip freezes kernel states and reports zero
physical output power. Restart uses the kernel's clean initialization contract.

REPCA1 can attach in either source order through its established incremental
reference and network-measurement contract. Its older converter constructors
retain their behavior. This proves routing/initialization, not complete plant
trajectory conformance.

The public DYR is the original native campaign input with line-ending
normalization only. Native CRLF SHA-256:
`4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4`.
Canonical LF SHA-256:
`200d305771956adb36e2dd04034f706ae2c02e64e04ff8fdf640c553185535a8`.
A permanent check reconstructs the native digest exactly. Prior raw references,
source-version fingerprints and cold-run artifacts are reused unchanged.

## Full-solver SMIB and independent comparison

Imported flat, fault and trip profiles run at 0.5/0.25 ms through the standard
modified-Euler solver. Flat voltage drift is zero at reported precision. The
SMIB setup explicitly permits a dynamic generator device without a synchronous
Machine object, as in the existing renewable fixtures; strict import still
requires the actual device to attach. This is not a missing-model substitute.
The
three-phase Bus 2 fault has j1-pu impedance, starts at 0.05 s and clears at
0.10 s (three 60-Hz cycles); recovery runs to 0.30 s. Faulted-bus minimum is
approximately 0.924748 pu. Trip at 0.20 s removes physical injection while
retaining all fourteen states. Machine/system bases are 50/100 MVA.

Six boundary channels compare independently with frozen native PSS/E output:

| Maximum absolute error | 0.5 ms | 0.25 ms |
|---|---:|---:|
| Bus 1 voltage (pu) | 1.50641e-6 | 1.08323e-6 |
| Bus 2 voltage (pu) | 3.34290e-6 | 2.94810e-6 |
| Bus 3 voltage (pu) | 1.19937e-7 | 1.27123e-7 |
| P (system pu) | 9.89553e-7 | 5.30591e-7 |
| Q (system pu) | 3.48714e-5 | 3.51109e-5 |
| Virtual speed deviation (pu) | 4.65757e-8 | 1.17206e-8 |

Permanent per-channel/per-step bounds sit modestly above these errors. Native
float times align by declared step tick, without interpolation or fitting.
601/1,201 endpoints match; two initialization-history and two instantaneous
event-post rows remain in raw data but are explicitly unmatched. The first
following dynamic sample remains included. Q/voltage precision floors do not
improve monotonically with step; no full numerical convergence claim is made.
Every maximum/time is retained in the derived diagnostic and raw traces.

## Gates and remaining work

Final XML audit: 70 focused checks without skips, 283 plugin-core tests (six
optional skips), 1,327 registered SMIB/model tests (one existing diagnostic skip),
zero failures/errors. Fourteen scoped source/fixture files match the tested
snapshot; remaining baseline Java/POM files match HEAD after declared line-ending
normalization. New import/SMIB tests are confirmed in full-suite XML. All 1,093
files of the preceding archive pass SHA-256 checks; its native cold-pair and
log/CSV quality evidence is reused, not claimed as a new simulator campaign.
No full Texas2k dynamics are run. The existing
25-family Texas2k inventory is unchanged; REGFMB1U belongs to the additional
approved-model catalog, not an invented case record.
Core 1.4.3 SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The fingerprint matches before and after the final gates.
Private archive: `testData/private/verification-artifacts/2026-10-18-regfmb-native-import`.

Remaining work includes active angle/voltage/current limits, further selector
and base/zero-time/negative-power profiles, plant disturbances, reconnect and
PLL recovery/history. The kernel report retains explicit native STORE versus
modified-Euler distinctions. Independent PowerWorld evidence and full model
acceptance are still open; this slice does not promote the family to VERIFIED.
