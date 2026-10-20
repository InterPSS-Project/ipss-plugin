# BASOEL2 equation foundation and reference diagnostics

## Status

BASOEL2 now has typed three-selector/twelve-constant data and a standalone
three-state kernel in plugin.core. It is not yet a loadable network model:
native import, exciter attachment and coupled SMIB acceptance remain open.
The approved-list loadable limiter count remains four. No full Texas2k
dynamics are run.

## Published contract

The [WECC 2026 specification](https://www.wecc.org/sites/default/files/documents/meeting/2026/16%20-%20JansenH%20-%20BASOEL2%20Model%20Specification_May%202026.pdf)
and [PowerWorld documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20BASOEL2.htm)
were checked visually. They describe summation-point operation, three input
choices, cumulative delays, hysteresis and separate integral/output bounds.
Any nonzero enable flag enables its output. The V/Hz branch uses terminal-bus
frequency; its minimum applies after the frequency gain, not before it.

The WECC appendix and current PowerWorld pseudocode differ at zero delay:
PowerWorld retains at least 1e-6 seconds of timer memory; the appendix does
not. The kernel requires an explicit timer policy and tests both, including
the literal appendix's subsequent zero-memory reset. Neither policy is
implicitly advertised as the installed reference simulator's implementation.
Source hashes and exact native-tool provenance are retained privately.

## Implementation and equation tests

Continuous filters and the PI integral use explicit modified Euler. Zero-time
filters are algebraic bypasses, not epsilon delays. The independent integral
bound and summed PI bound both use the supplied exciter voltage reference.
The negative OEL contribution and upper-zero V/Hz contribution are summed.

Timer memory advances only at an explicitly accepted endpoint. Predictor,
corrector and repeated observations cannot advance it. Cancellation restores
unaccepted continuous state. Read-only state/timer snapshots, invalid stage
sequences, nonfinite arithmetic, disabled-output/internal-state separation,
partial exposure, cooldown, hysteresis and zero-delay behavior are covered.
There is no default timer dialect, fitted parameter or hidden host binding.

## Independent reference observations

Seven flat/fault profiles, two timesteps and two cold runs retain 42,140 raw
rows across 28 exports, each with time plus 38 channels. Profiles exercise
Efd, Ifd and Vfe selections, V/Hz, disabled outputs and a zero input filter.
All physical channels and continuous states repeat exactly. Only three of
fourteen pairs have identical whole CSVs: other pairs differ in one or more
opaque timer-storage channels. These channels and both original exports are
retained, not normalized, dropped or relabeled as repeatable evidence.

The zero-filter Ifd profile proves exact input/physical-field-current identity
on every raw sample at both steps and in both cold runs. Disabled profiles
also prove zero local and machine-boundary limiter outputs. Permanent audit
checks reject incorrect routing or any nonzero disabled output.

A separate terminal-frequency observer adds two cold exports (2,010 rows,
time plus 39 channels). It changes no physical trajectory. Bus-frequency and
shaft-speed deviation differ by up to 4.1940151e-4 pu in this profile, so a
future host must not silently substitute shaft speed for bus frequency.

The shared producer now distinguishes three entered selectors from twelve
additional internal integer slots. Existing controller control-run output
remains byte-identical. The observed native timer reset differs from the
published capped-memory algorithm; this is an unresolved dialect discrepancy,
not justification for fitting a timer to a fault trace.

## Remaining work

Final validation: 134 focused, 431 core and 2,086 scoped SMIB/model tests,
zero failures/errors and seven existing skips; 227 offline checks also pass.
The 28 new kernel invocations are registered. Source reconciliation covers
1,905 Java/POM files. All 22 earlier SMIB exports and six event exports remain
unchanged, and all 1,362 files in the preceding evidence seal retain their hashes.
Neutral-language checks pass. Unrelated worktree edits are excluded.

Raw references, specifications, exact dependency provenance, logs, reports and
the validation summary are retained in the private
`2026-10-19-basoel2-foundation` evidence directory with a SHA-256 inventory.

- [ ] Resolve native timer semantics with independent discriminators and
  recorded-input replay; preserve the documented policies separately.
- [ ] Verify all three physical input/base mappings and native initialization.
- [ ] Add exact parser, runtime, terminal-frequency measurement and bounded
  summation-host lifecycle integration after those contracts are established.
- [ ] Run coupled flat/fault SMIB comparisons and require RMSE < 0.001 for
  every new channel, retaining peak and event-side errors separately.
- [ ] Extend supported host/profile coverage without claiming takeover support.

The next slice is runtime/measurement integration, not a full-system run.
