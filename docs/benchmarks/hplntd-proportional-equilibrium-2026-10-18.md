# Hybrid plant proportional-only equilibrium

## Implemented equations

HPLNTDU initialization now supports feasible enabled loops with zero integral
gain. Zero-gain integral storage is initialized to zero, not to an output bias.
The existing runtime equations, predictor/corrector method, exact wrapper schema
and downstream command interface are unchanged.

For the active path, let F be the binary feedforward flag, P0 the initial
aggregate active command, and Pfb the measured active power when electrical
feedback is enabled (zero otherwise). With zero active integral gain:

```text
Plantref = (P0 + Kpg * Pfb) / (Kpg + F)
activeIntegral = 0
```

Initialization checks the used error/PI/output limits and rejects singular or
infeasible configurations without changing gains or limits. Feedforward can
support zero Kpg as well as zero Kig; unused error limits cannot constrain its
zero proportional contribution. With positive Kig, Plantref=Pfb and the usual
integral bias remains. PF's shared active measurement is **not** Pfb when
electrical feedback is disabled. This distinction fixes the previously rejected
PF/no-feedback initialization, including positive-integral operation.

For zero reactive integral gain, the required limited error is Q0/Kp. Its
inverse shifted-deadband error is e+dbd2 for positive e, e+dbd1 for negative e,
and zero for zero e. Add that offset to the compensated-voltage measurement,
Q measurement or requested PF-derived Q, according to the reference selector.
The reactive integral is zero, while the lead-lag/output states retain Q0.
All enabled reference/error/output limits are validated. The equilibrium
derivation is tested with both error signs, compensation modes and all three
reference selectors. Negative integral gains and singular proportional paths
remain explicitly rejected.

The source is the installed diagram and state/parameter tables (PDF pages
1035-1040), identified by hash in each private manifest. Its parameter table
requests positive reactive integral gain; the installed native implementation
nevertheless supports the bounded zero-gain configurations exercised here.
This is versioned behavioral evidence, not a claim that the table documents
every zero-gain combination.

## Independent evidence

Twenty-four immutable profiles at 0.5/0.25 ms cover zero reactive/active/both
integral gains, compensated/droop voltage control, PF control, all four active
feedback/feedforward combinations, feedforward with zero Kpg, and PF without
electrical feedback with either zero or positive integral gains. Every cold
pair is byte-identical across all 89 raw channels and every event/pause sample.
Thirty-six fresh runs and twelve reused runs support the 24 pairs.

Recorded-input replay compares all thirteen plant states and four absolute/
incremental commands without injecting native states. The worst replay error
is approximately 2.095e-6 pu. Imported coupled SMIB tests compare four voltages,
four unit powers, thirteen plant states and four downstream states. They also
verify exact modified-Euler reference-slew recurrences, once-per-stage group
advancement and whole-group reseeding after initialization.

New per-profile/channel limits are approximately 10% above observed maxima,
with a 1e-12 floor. These are bounded cross-method coupled comparisons, not
complete trajectory parity: the reference STORE slew realization and default
modified Euler remain distinct. Existing selector/fault/other-model bounds and
raw histories are unchanged. Additional voltage/PF profiles establish feasible
initialization and network-coupled response to the active reference step; they
do not establish every voltage/PF-reference disturbance or saturation branch.

Reproduce fresh configurations using the installed reference Python environment:

```text
reference-python tools/hplntd_proportional_probe.py UNIQUE_TAG 0.0005 MODE
reference-python tools/hplntd_proportional_probe.py UNIQUE_TAG 0.00025 MODE
python tools/audit_hplntd_proportional.py
```

The mode map explicitly declares all token changes. The audit validates raw
hashes, exact inputs, actual loaded constants, runtime/source consistency,
byte-identical cold histories, embedded resources and zero integral storage.
Ordinary Maven tests require only the immutable resources, not licensed execution.

## Retained counterexamples, not acceptance profiles

- Nonzero reactive deadband with zero Ki: the native initializer subtracts the
  upper threshold in the observed positive-error case. It reports a suspect
  lead-lag initial derivative of -0.4 pu/s and drifts before any disturbance.
  Its reference is approximately 0.18730938 rather than the stationary
  diagram-derived 0.20730938 pu. InterPSS uses the stationary inverse-deadband
  solution, tested independently for both signs; this rejected native profile
  is not advertised as exact native initialization agreement.
- Zero Ki/Kp and zero Kig/Kpg with feedforward disabled: the nonzero initial
  commands are infeasible. Native initialization is suspect; InterPSS rejects
  the singular path explicitly.
- Zero Ki voltage control requiring Vref above Vrefmax: the native tool accepts
  the initial reference (about 1.11141 versus configured maximum 1.08) without
  changing the audited CON limit. Its initialization/runtime boundary semantics
  remain unresolved. InterPSS retains its explicit bounded-reference rejection;
  no unexplained limit expansion or bypass is introduced to match this trace.

All four raw diagnostic runs, statuses and exact inputs remain separate from
the feasible-profile acceptance set. Their source/version and binary outputs
are retained privately. A successful native process alone does not prove a
stationary or source-conforming model.

## Final gates and retained artifacts

Final source-audited baseline: plugin `bdd6d02ba`, including its newly committed
state-observation APIs. Gates pass with zero failures/errors: 47 focused checks,
393 plugin-core tests (six existing optional skips), and 1,514 selected
SMIB/model/solver tests (one existing diagnostic skip). Twenty-six offline
probe/audit/wording checks also pass. Seven of the eight new kernel tests fail
against the preceding implementation; the eighth is the infeasibility guard.

The private archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-hplntd-proportional-equilibrium`.
It retains 48 raw accepted runs, four diagnostic runs, 24 state-replay and 24
coupled traces, runnable sources, exact inputs, runtime/source hashes,
fail-before output and final Surefire reports. Earlier discriminator and
disabled-control evidence is checked for immutability. Unrelated working edits
are excluded from the isolated verification snapshot and this commit.

## Remaining work

Resolve initialized reference-limit semantics and nonzero-deadband native
initialization differences; add independently excited voltage/PF-reference,
active-limit and remaining feedback profiles. Branchless/alternate downstream
hosts, switching, exhaustive trip/reconnect and instantaneous fault
current/frequency-callback parity remain open. The overall model-coverage goal
is incomplete. No full Texas2k dynamic simulation is run for this slice.
