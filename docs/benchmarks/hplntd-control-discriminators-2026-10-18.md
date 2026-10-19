# Hybrid plant disabled-control and zero-gain discriminators

Historical discriminator checkpoint. Disabled-path runtime support is now
implemented in the [follow-up slice](hplntd-disabled-controls-2026-10-18.md);
enabled zero-gain initialization remains open. The results below describe the
original probe snapshot, not a claim that the later runtime still rejects flags.

## Scope and acceptance boundary

This slice freezes independently repeatable four-bus HPLNTDU/REAXDU reference
experiments before implementing disabled selectors and zero-integral-gain
initialization. It does **not** extend the production runtime or mark these
branches verified. No full Texas2k dynamic simulation is required or run.

The existing production foundation/coordinator still rejects disabled control
paths and nonpositive integral gains. Those explicit rejections remain preferable
to silently substituting a different initialization or publishing zero power.

## Reproduction

From the repository root, use the installed reference Python environment:

```text
reference-python tools/hplntd_control_mode_probe.py UNIQUE_TAG 0.0005 MODE
reference-python tools/hplntd_control_mode_probe.py UNIQUE_TAG 0.00025 MODE
```

Modes: `closed`, `p-disabled`, `q-disabled`, `both-disabled`, `ki-zero`,
`kig-zero`, `both-zero`. Each run has a fresh process and a new output directory;
existing evidence is never overwritten. Active/reactive references step by
0.05/0.02 plant-base pu at 0.05 seconds. A pause at 0.1 seconds and the 0.2-second
endpoint match the common fault runner, but these profiles apply no fault.

The input generator checks the frozen source hash and exact wrapper allocation,
then changes only declared numeric tokens. Other records, quotes, whitespace,
line endings and parameters remain intact. The common runner checks all 61
entered plant constants against the loaded single-precision values.

Raw output retains all 89 channels, including all 13 plant states, both units'
states/variables, initial/absolute/incremental commands, voltage/angle, unit
power and frequency. Both event/pause duplicate rows are retained. There are
405 samples at 0.5 ms and 805 at 0.25 ms.

The private archive records exact runtime/source hashes, runnable scripts,
RAW/DYR inputs, binary output, CSV, logs and manifests. Public prose uses neutral
benchmark language; runtime identifiers in inputs and code are preserved.

## Observations and implementation implications

In the tested reactive-power reference mode with electrical feedback and
feedforward enabled:

- `p-disabled`: active states 4, 5, 6, 9 and 10 initialize to zero and stay zero.
  Absolute active command remains its initial value and the active increment is
  exactly zero. The reactive loop continues responding.
- `q-disabled`: reactive states 0, 1, 2, 3, 8, 11 and 12 initialize to zero and
  stay zero. Absolute reactive command remains its initial value and its
  increment is exactly zero. The active loop continues responding.
- `both-disabled`: both sets stay zero. The shared voltage measurement remains
  a physical measurement; it is not replaced by zero.
- `ki-zero`: reactive integral storage is zero, while the initial reactive
  reference changes to support proportional-only equilibrium. In the coarse
  run it changes from approximately 0.09410558 to 0.19730937 pu; the initial
  command remains approximately 0.10320380 pu. Retaining the positive-integral
  initialization and merely multiplying the derivative by zero would be wrong.
- `kig-zero`: active integral storage is zero; the initial active reference
  changes slightly. This one feedback/feedforward combination is not enough to
  establish general proportional-only initialization.

These are reference observations, not proof for every reference selector,
compensation mode, feedback/feedforward combination or host type. In particular,
the source parameter table's positive reactive-integral-gain note and the
observed zero-gain initialization must remain distinguishable. Do not infer a
general zero-gain contract or fit an initial bias from one trajectory.

The follow-up implementation must derive proportional-only equilibrium,
discriminate voltage/power-factor modes and disabled electrical feedback, then
test predictor/corrector stages, algebraic bypass, named states, main import,
command dispatch and coupled SMIB responses. Instantaneous fault callback parity,
switching, alternate hosts and broader lifecycle coverage remain separate gaps.

## Gates

Accepted evidence: fourteen profiles at two steps, each cold-generated twice
(28 successful runs), with byte-identical CSVs in every pair. All seven coarse
profiles also match the earlier exploratory outputs exactly. Both common-runner
fault profiles retain their previously frozen CSV hashes. These claims are
checked by `python tools/audit_hplntd_control_modes.py`, not inferred from
successful process exit codes.

Final regression gates on committed baseline `7866eb36e`: ten offline probe/audit
tests, six wording-policy tests, 379 plugin-core tests (six existing optional
skips), and 1,493 selected SMIB/model/solver tests (one existing diagnostic skip).
There are zero failures/errors. The isolated test snapshot was synchronized to
the committed core/integration Java sources and POMs before the final rerun;
unrelated working edits are not part of this slice. No Java equations or
trajectory tolerances changed.

The sealed private evidence is under
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-hplntd-control-discriminators`,
with raw runs, exact probe sources, hash inventory, validation summary and
Surefire reports. The broader coverage objective remains incomplete.

The offline tests check exact token-only mutations and reject changed inputs,
unknown modes, modified output hashes, missing channels, nonfinite samples and
discarded duplicate rows. The artifact auditor verifies cold-pair byte equality,
all raw file hashes, source/runtime consistency, disabled-state/held-command
invariants and zero-integral storage without changing raw samples.

The common-runner refactor must also reproduce both previously frozen fault CSV
hashes exactly. This protects existing fault evidence from a reference-generator
regression; it does not resolve the documented coupled fault mismatch.
