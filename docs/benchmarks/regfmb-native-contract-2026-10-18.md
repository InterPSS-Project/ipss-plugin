# REGFMB1U native contract and algebraic foundation

Status: foundation only. REGFMB1 is still a missing dynamic runtime; approved
renewable loadability remains 22/27. No placeholder catalog entry, guessed alias,
parser, generator attachment or dummy dynamics is added.

## Source/version boundary

The subsequent [fourteen-state kernel slice](regfmb-kernel-2026-10-18.md) builds
on this foundation. Counts and implementation status here describe this snapshot.

The installed reference simulator Model Library, section 21.8, printed pages 811-819
(PDF pages 823-831), defines REGFMB1U. The exact documented and experimentally
loaded record is:

```text
IBUS 'USRMDL' ID 'REGFMB1U' 101 1 6 25 14 17 <6 ICONs> <25 CONs> /
```

ModelLibrary.pdf SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
DSMDL.DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The diagrams and formula pages were rendered and visually reviewed, not inferred
from text extraction alone. The source-list REGFMB1 spelling remains unchanged;
it is not silently accepted as the native wrapper's name.

Flags are wFlag, VdrpFlag, QVFlag, PQFlag, Fflag and ESflag. Constants are mq,
Kpv, Kiv, mp, Dwmax/min, KpPLL, KiPLL, DwPLLmax/min, Tp, H, D1, D2, wD, ImaxSS,
Kf, Ki, ImaxF, Tpf, Tqf, Tvf, Tif, Ke and VPLLfrz, in that order. Native coupling
impedance is machine-base RAW ZSOURCE, not additional DYR constants.

PowerWorld exposes Re/Xe as model parameters and documents its own timestep
corrections/default handling. Those are not mixed into this native data contract.
[PowerWorld REGFM_B1](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_B1.htm).

The existing REGFMA1 implementation is droop-based and lacks this model's VSM,
PLL and fourteen-state realization. It is not an equation-equivalent substitute;
only interface/initialization structure can be reused in the upcoming runtime.

## Native-first campaign

Eight formal cold runs cover flat and Bus 2 three-phase fault profiles at
0.5/0.25 ms, each repeated twice. Fault impedance is j1 pu, application 0.05 s,
clearing 0.10 s (three 60-Hz cycles), end 0.30 s. The public three-bus RAW has
100-MVA system base, 50-MVA converter base and ZSOURCE=0.015+j0.23 pu.

All eight runs initialize O.K., pass the existing PsseRunQuality log/CSV audit,
reach the end within the declared 1e-5-s native float-clock tolerance, and have
finite complete exports. Every cold pair is byte-identical without normalization.
Raw samples are 605/1,205, with 41 channels plus time: all 14 STATEs, 17 VARs,
three voltage/angle pairs, converter angle, P, Q and speed deviation. Two negative
history and two duplicate-event-side rows are preserved. Every actual ICON and
single-precision CON is checked against the declared input after initialization.

The first preliminary allocation-only flat probe is retained separately; its
channels differ and it is not silently substituted for the formal references.

## Implemented foundation and measured scope

Regfmb1Data retains the exact six-ICON/25-CON native order and defensive copies,
finite-value/domain checks, raw source impedance and raw flags. Documented
nonzero flag interpretation, incompatible Vdrp/QV selection, effective swapped
current limits and VPLLfrz ceiling are explicit derived values: source arrays
are not mutated and PowerWorld-only corrections are not imported.

Regfmb1NetworkBoundary implements machine-base current reconstruction, the
phase-preserving fault-current cap, reconstructed Norton source, frame rotation,
initial internal phasor, steady-priority current bounds and their voltage bounds.
The fault cap is equation/unit-tested; the mild native fault does not establish
all active-cap behavior or lifecycle semantics.

Native initialization independently checks the phasor, terminal/PLL/internal
angle relation and factor-of-two system/machine power-base conversion. Native
moving-bound checks consume native filtered Id/Iq/V explicitly, comparing every
raw row. They are algebraic equation checks, not dynamic-controller replay.
Maximum Idmax/Iqmax/Emax/Emin errors over all profiles are approximately
1.11425e-7 / 4.29200e-8 / 1.29631e-7 / 9.49031e-8 pu. Per-channel bounds are
1.5e-7 / 5.6e-8 / 1.7e-7 / 1.25e-7 pu. Flat stationarity and actual fault dip
are also asserted. Complete native CSV resources are immutable and hash-checked.

## Gates and remaining implementation

Final XML audit: 32 focused checks without skips, 276 plugin-core tests (six
optional skips), and 1,313 registered SMIB/model tests (one existing diagnostic
skip), zero failures/errors. Eleven scoped source/resource files match the
isolated tested snapshot; remaining tracked baseline Java/POM files match HEAD
after declared line-ending normalization. The full SMIB XML contains all four
new native-contract checks. Raw artifact hashes, exact native allocation and
four byte-identical cold pairs are independently audited. The unrelated
untracked WECC240/JAX test is excluded. No full Texas2k dynamics are run.
Core 1.4.3 SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The dependency fingerprint matches before and after the gates.
Private archive: `testData/private/verification-artifacts/2026-10-18-regfmb-native-contract`.

Remaining work is the actual fourteen-state VSM/PLL/controller runtime, including
initialization and semantic state coordinates, corrector stages, zero-time
bypasses, all flag combinations, active-current angle integrators, voltage-PI
bounds and PLL freeze behavior. The printed angle-limit fallback and PLL freeze
description need exact-version discriminators, not guessed corrections.
Then add strict exact-wrapper parsing/attachment, plant reference routing,
base/lifecycle tests, complete recorded-input replay and independently coupled
flat/fault/trip comparisons. Keep REGFMB1 missing until a real loadable path exists.
