# REGFMC1U native contract and hybrid-boundary foundation

Status: foundation only, not a dynamic runtime or a loadable parser path.
Approved renewable coverage remains 23/27 loadable families. REGFMC1,
REPCGFMC1, REPCCU and HPLNTD still need implementations. No alias to REGFMB1,
REGFMA1 or REPCA1 is introduced.

## Exact source and dialect

The subsequent [eleven-state kernel slice](regfmc-kernel-2026-10-18.md) builds on
this foundation. Implementation status and gate counts below describe this snapshot.

reference simulator Model Library section 21.9, printed pages 820-828 (PDF 832-840),
defines the exact native record:

```text
IBUS 'USRMDL' ID 'REGFMC1U' 101 1 3 34 11 27 <3 ICONs> <34 CONs> /
```

ModelLibrary.pdf SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Native DSMDL.DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The diagram, priority equations, phasor limiter and non-windup pseudocode were
rendered and visually checked. The source-list name REGFMC1 is not sufficient
evidence for a standard-record alias; native reference uses REGFMC1U.

ICON order is Fflag, PQFlag, VFlag. CON order is mq, Kpe, Kie, dEmax/min,
Tvr, Te, Tvf, Tif, Tpf, H, D1, D2, wD, mp, dPgfmMax/min, dPdMax/min,
dwmax/min, Twref, Twm, PrefMax/min, QrefMax/min, Vmin, Vref0, Kqv, dbVh/l,
Kf and Imax. The eleven states are terminal-voltage filter, GFM voltage-reference
filter, GFM reactive-current filter, voltage PI integral, GFM internal voltage,
speed filter, reference-speed filter, GFM power filter, speed deviation,
transient-damping storage and GFM network-frame angle.

Native impedance comes from machine-base RAW ZSOURCE, not extra CONs. The
[PowerWorld REGFM_C1 documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_C1.htm)
was consulted independently: it exposes Re/Xe and additional automatic corrections.
These must not be silently mixed into native reference simulator ordering or parameter handling.

## Implemented primitives

`Regfmc1Data` preserves the exact arrays with defensive copies and explicit
domain checks. Derived native rules retain the original raw values: nonzero
flags select one; Kf=0 means one; Vmin<=0 becomes 0.01; Vref0=0 uses initial
terminal voltage; Te is at least twice the simulation step. Other PowerWorld
corrections are not applied. Native probes below use valid, uncorrected inputs;
the correction helpers are documentation/unit evidence, not native correction
discriminators.

`Regfmc1NetworkBoundary` implements the GFL command limits, voltage denominator,
voltage-error deadband/injection, P/Q priority circle and terminal-frame rotation.
The non-priority radius uses the **unclipped reference** specified by native
note 12, not the priority-clipped output. Reactive current is positive for
injected Q; the network phasor uses Ip-jIq.

The hybrid boundary first calculates:

```text
Igfm_raw = (Egfm - Vterminal) / Zsource
It_raw   = Igfm_raw + Igfl_raw
k        = abs(It_raw) / Imax
scale    = 1 / max(1, k)
Igfm     = scale * Igfm_raw
Igfl     = scale * Igfl_raw
E_norton = Vterminal + Zsource * Igfm
```

Both branches share the same scale; separate branch caps would change the
terminal current direction. The returned record keeps raw and limited branches
distinct. Native VAR5/6, VAR22/23 and VAR24 report **raw GFM** P/Q, terminal-frame
components and current magnitude, even while the network current is capped.
The severe-fault experiment distinguishes these coordinates by more than
3 pu; treating VAR24 as the limited GFM branch would fail independently.

At equilibrium the GFM branch supplies zero P/Q and its internal voltage equals
the terminal phasor. GFL carries the original machine-base P/Q. This differs
from REGFMB1 initialization and prevents reusing its kernel as a substitute.

## Native campaign and comparisons

Twelve native cold runs cover three profiles at 0.5/0.25 ms, twice each:
stationary flat run; Bus 2 three-phase j1-pu fault; and Bus 1 three-phase
j0.001-pu fault activating the terminal-current limit. Faults start at 0.05 s
and clear at 0.10 s, three cycles at 60 Hz; runs end at 0.30 s. The three-bus
RAW uses 100-MVA system base, a 50-MVA converter and ZSOURCE=0.015+j0.23 pu.
No full Texas2k dynamic simulations are run.

All twelve pass the existing `PsseRunQuality` initialization/log/finite-CSV/end-time
audit; all six cold pairs have byte-identical CSVs. Actual post-initialization
allocation, every ICON and every float-rounded CON match the exact inputs.
Exports retain all eleven STATEs, 27 VARs, three bus voltage/angle pairs and
converter angle/P/Q/speed: 48 channels plus time. There are 605/1,205 rows,
including two negative history rows and two duplicate-event rows. No rows are
dropped or interpolated for boundary replay. The limit is active at 101/201 rows
of the severe profile, including its event side.

The public fixture is `renewable/regfmc1u_native.dyr`. Native CRLF SHA-256:
`5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0`.
Canonical LF SHA-256:
`fbbb36711abc5dd80d45cdd4e5d06c08c72f231a474fcb3d494433c6fb7a6794`.
The fixture test explicitly reconstructs CRLF for the native hash and checks
every ICON/CON against the typed data. All six complete CSVs are losslessly
gzip/base64 embedded with immutable raw SHA-256 checks in
`Regfmc1NativeContractReference`; raw native inputs/logs/outputs and the runnable
`psse_regfmc_native_probe.py` remain in the local private verification archive,
following repository policy for simulator-driving programs.

Per-channel maximum errors across all rows/profiles are approximately:

- GFL voltage injection: 1.163e-7 pu; priority Iq bound: 4.292e-8 pu.
- GFL Ip bound/output: 2.434e-6 pu; Iq output: 8.080e-8 pu.
- Raw-current ratio k: 1.070e-6; limited source magnitude: 1.193e-7 pu.
- Limited source angle: 4.077e-6 degrees; GFL angle: 1.309e-5 degrees.
- Limited GFL magnitude: 1.193e-7 pu; terminal-current magnitude: 5.260e-7 pu.
- Raw GFM magnitude: 7.265e-7 pu; raw GFM real power: 1.128e-7 pu.

Each has a separate regression bound with a modest margin above the measured
maximum. The GFL Ip circle becomes sensitive to native float rounding near
tangency; its larger bound is not a dynamic-trajectory tolerance. Commands and
states are not fitted. The hybrid check consumes recorded native Egfm, angle,
terminal voltage and GFL commands: it proves algebraic equations, **not** that
InterPSS independently evolves the eleven states correctly.

## Validation and remaining work

Final XML/source audit: 46 focused checks, 290 plugin-core unit tests (six
optional skips), and 1,333 registered SMIB/model checks (one existing diagnostic
skip), zero failures/errors. All six new native-contract tests are present in
the SMIB XML; all seven new unit tests run in the unit gate. Thirteen scoped
source/resource/fixture files match the isolated tested snapshot, and every
remaining baseline Java/POM matches HEAD after declared CRLF/LF normalization.
The unrelated Texas2k report edits and untracked WECC240/JAX test are excluded.
The original sandboxed focused attempt failed on Maven-cache access before
testing; its log is retained, followed by successful authorized runs.

Core 1.4.3 SHA-256 remained unchanged before/after the gates:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The sealed private evidence directory is
`testData/private/verification-artifacts/2026-10-18-regfmc-native-contract`.
It retains exact tested sources, native cold runs and executable probe,
dependency JAR, XML/logs, rendered source pages, generation/audit scripts and
SHA-256 inventory. Public CSVs remain immutable; no native dynamic state
trajectory or system acceptance is claimed by this boundary-only slice.

- [x] Exact native schema, documented states and hybrid phasor boundary.
- [x] Typed data, GFL priority/deadband and joint-current limiter primitives.
- [x] Independent initialization and all-row algebraic replay; cold references.
- [ ] Eleven-state kernel: semantic coordinates, initialization derivatives,
  predictor/corrector stages, zero-time paths, PI/speed bounds and damping.
- [ ] Native selector/reference-step discriminators, P-priority, correction
  behavior, negative power and limiter release/re-entry.
- [ ] Exact native parser, builder/device attachment, reference routing,
  system/machine-base Norton injection and lifecycle/trip/reconnect.
- [ ] Independent recorded-input kernel replay and coupled InterPSS SMIB
  flat/fault/trip comparisons at both steps; PowerWorld acceptance where applicable.

Keep this family classified as missing runtime until an actual loadable path
exists. The wider model coverage goal remains incomplete.
