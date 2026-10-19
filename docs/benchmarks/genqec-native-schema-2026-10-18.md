# GENQEC native schema and saturation-selector slice

Status: exact input/selector boundary accepted for this slice, not complete
machine parity. The scoped implementation commit is recorded in Git history;
private source/run/report digests are sealed under
`2026-10-18-genqec-native-schema`.

## Source contract and confirmed defects

The approved May 2026 list, visually checked on page 2, has three approved
generator mappings: GENROU, GENQEC and GENQEJ. Their existing runtime presence
does not prove exact input schemas. This work reuses the existing GENQEC/core
round-rotor engine; it changes no generator differential or network equation.

The installed model library, pages 47–55, specifies standard GENQEC as one
integer selector followed by sixteen constants. Their order is T'do, T''do,
T'qo, T''qo, H, D, Xd, Xq, X'd, X'q, X''d, X''q, Xl, S1, S12, Kw. The
GENQECU/GENQEJU allocation wrapper precedes the same selector and constants
with 1 1 1 16 6 1. GENQEJU's current-library contract is wrapper-only; a flat
GENQEJ record is not inferred. Exact document/runtime hashes are private.

The previous standard reader instead accepted eighteen fields: a leading
acceleration property, sixteen constants and a trailing saturation setting.
That interchange form is not the documented native record. It rejected genuine
seventeen-field standard input and admitted malformed native inputs.

Native zero means exponential saturation; every nonzero integer means scaled
quadratic. The previous wrapper path passed the selector into the typed
three-way setting: 2 selected unscaled quadratic and −1 disabled saturation.
The new native boundary normalizes nonzero selectors to scaled quadratic.
The [PowerWorld GENQEC](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEC.htm)
and [GENQEJ](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEJ.htm)
typed properties have a separate three-way saturation selection. That typed
API remains unchanged, including its published validation rules.

Ra remains supplied by RAW generator source impedance. Compensation and
acceleration are typed properties, not extra native DYR fields. Required
constants are parsed without silent defaults; nonfinite values, fractional
selectors, wrong counts and wrong allocation headers are rejected.

## Evidence and scope

Five input profiles—standard selectors 0, 1, 2, −1 and wrapper selector 1—run
the same three-cycle j0.2 system-pu fault on a public two-bus testbed. At 0.5
and 0.25 ms, every profile was cold-generated twice: twenty native runs.
The source, input, allocated constants/selector, initialization log and every
raw state/boundary sample are retained. Each profile has 16 channels, all six
machine states and 605/1,205 rows including negative history and event duplicates.

At both timesteps the full native CSV is byte-identical for selectors 1, 2 and
−1. The entered ICON is retained as 2/−1 internally, so this demonstrates the
functional nonzero branch rather than an automatic data rewrite. Selector 0
has a distinct saturation response. Standard versus wrapper native outputs
retain small implementation-level differences: maximum E'd residual is about
4.62e-6/2.77e-6 pu, and maximum terminal-power residual about
1.67e-4/2.01e-4 pu. Those are diagnostics, not bit-parity acceptance.

InterPSS imports all ten frozen inputs and runs their three-cycle faults. All
nonzero-selector and wrapper accepted-endpoint trajectories match to 1e-12
across the recorded states and boundary channels. Exponential saturation stays
distinct. These paired checks validate the input/selector boundary; they do not
establish complete native-versus-InterPSS trajectory conformance.

PowerWorld also runs the exact standard selector-0/1 input at both timesteps,
twice cold: eight final artifacts with eleven channels, including six machine
states. Auto-correction and parameter overrides are disabled. Complete outputs
retain all 603/1,203 rows and both event duplicates. They are repeatable
trajectory diagnostics; post-initialization parameter round-trip and complete
cross-tool acceptance are not established by this slice.

Three exploratory PowerWorld attempts remain separate: a parameter-table export
stalled after load flow; an intermediate native export wrote a correct input
round-trip but did not return; a completed solve stalled on an incorrect state
export selector. The final probe uses the repository-tested TSMachineState
selector and no intermediate export. None of the three attempts is counted
as an accepted model trajectory.

Two public GENQEC fixture files were migrated to native syntax. Their effective
numeric parameters are unchanged: old acceleration 0 and typed saturation 0
become native selector 0; Kw remains the final constant. Existing immutable
reference artifacts and their historical input hashes are not rewritten.

## Checklist and remaining work

- [x] Capture four assertion failures before fixing the native reader.
- [x] Correct cardinality, exact parameter ordering and nonzero selector mapping.
- [x] Preserve the typed three-way API and existing machine engine.
- [x] Register exact schema and paired fault tests; pass 51 focused checks.
- [x] Audit twenty native cold runs and eight final PowerWorld runs.
- [x] Pass 1,559 registered SMIB/model/solver checks (one existing diagnostic
  skip) and 401 plugin-core tests (six optional skips), with zero failures/errors.
- [x] Audit all 1,793 unchanged Java/POM baseline files, nine owned Java sources,
  exact fixtures and embedded references. Exclude unrelated worktree edits.
- [x] Seal source/run/report hashes for the scoped implementation commit.
  Retain the resource-sync attempt with three missing-resource errors separately
  from the fully synchronized successful focused and broad runs. Eight offline
  probe/auditor checks and six language-policy checks pass.
- [ ] Close full coupled/native/PowerWorld trajectory agreement, zero-winding
  and time-constant branches, larger current compensation, typed active
  saturation limits, initialization corrections and lifecycle coverage.

The catalog is a loadability/schema inventory, not a VERIFIED label. No full
Texas2k dynamic simulation is run or required for this slice.
