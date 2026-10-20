# PSS5C proper static low transducer

## Implemented source contract

The published low transfer function has the exact proper static reduction

```
Hlow(s) = (1 + DLI*s)/(1 + BL*s + AL*s*s)
DLI = AL = BL = 0  =>  Hlow(s) = 1
lowSignal = speedDeviation
```

The native thirty-constant reader/builder now accepts this case with each
already supported high-transducer order: full order, TH zero, or AH zero
with a proper degree-two denominator. The constants are preserved literally;
no epsilon, minimum-time substitution, fitted derivative or undocumented
reference-tool bypass is introduced.

Both retained low companion coordinates are inactive and initialize/freeze at
zero. Existing thirteen-state names remain unchanged. The six low/intermediate
band memories still evolve normally, using the instantaneous input at their
own predictor/corrector endpoint. At non-nominal constant speed, their initial
values are KC1 times speed deviation, giving a zero stabilizer output at
equilibrium. Queries do not advance or overwrite states.

The analytical local matrix has two zero low-state rows and zero low-state
input entries. Each active low/intermediate band has its normal negative lag
pole and input-vector term `rate*KC1`, without coupling from the inactive
coordinates. These frozen internal modes are not evidence of whole-system
marginal stability. The full-order companion/cascade observation API rejects
the static case rather than mislabelling inactive storage as native states.

If AL and BL are zero but DLI is nonzero, the transfer contains an input
derivative and remains explicitly unsupported. Even an arbitrarily tiny
nonzero DLI is not silently rounded away. Positive AL or BL retains its
existing dynamic order. Documented whole-model bypass still avoids evaluating
unsupported equations; static-low support is not itself a whole-model bypass.

The source diagram and formula are retained in the foundation archive,
document SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`,
printed pages 106-107. This implementation is published-equation support,
not certification that every reference release supports the degenerate input.

## Independent reference attempts: rejected, not scored

Flat/fault profiles at 0.0005 and 0.00025 seconds are each executed twice with
only the first three constants changed to zero. The eight provenance-recorded
runs retain exact network/dynamic inputs, executable probe/helper bytes,
finite parameter readbacks, internal 1/30/13/6 allocation, logs and raw output.
Preflight explicitly records the model-library, source-document, Python and
wrapper hashes before initialization/stepping; it is not an acceptance label.

All eight runs print a successful initialization check, then publish NaN for
the low signal, including flat runs without any fault. The channel reader
subsequently raises a TypeError. That export exception is secondary evidence:
the guard independently requires the named nonfinite low signal in the log.
No CSV is fabricated from the invalid output and no replay or acceptance
envelope is generated from it. Undefined/ignored models, missing preflight,
changed raw files, wrong parameter readbacks and allocations are rejected as
different conditions, not reclassified as this counterexample.

Two freshly generated unchanged positive-coefficient flat controls use the
same library/wrapper/Python/document identities. Both pass initialization and
run quality, retain 1,005 finite samples and repeat their CSV/input/history
bytes exactly. This distinguishes the rejected configuration from a general
installation/license failure. The library SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Exact release identities remain private.

Eight earlier static attempts without the expanded preflight are also
preserved, not substituted for the final rejection set. A separate attempted
batch could not launch when the drive was full; those launch failures are
storage evidence, not simulator/model results. No valid independent native
trajectory for the static case is claimed. The typed PowerWorld interface
does not expose these three native transducer fields, so its existing benchmark
is not presented as an exact substitute.

## Verification

Twenty new registered controller tests cover strict native parsing for all
three high orders, independently calculated low-band slopes, full-vector
predictor/corrector reconstruction, non-nominal equilibrium, instantaneous
feedthrough and both output limits, frozen-mode diagnostics, independent
sinusoidal magnitude/phase, improper nearby polynomials, whole-model bypass
and rejection by the full-order observer. The analytic frequency test uses
the existing family's fixed numerical allowance; no native error sets a bound.

Twelve additional imported SMIB cases combine the static low transducer with
the three high orders, flat and three-cycle three-phase fault profiles, and
two steps. They reuse the existing core round-rotor machine and keep the
original nonstatic cases and exports separate. Their source/static identity
and inactive-coordinate assertions run at every accepted point.

Ten offline negative controls validate the failed-evidence contract, including
synthetic float32 readbacks. Every negative control first proves its unmodified
synthetic failure metadata passes; the initial test-fixture precision mistake
was corrected to genuine float32 readbacks, not by loosening the guard.
These synthetic fixtures are not independent simulator data.

Final report-based gates pass 231 focused, 427 core and 1,962 scoped
SMIB/model/solver tests, with zero failures/errors and seven existing skips
across the core and broad gates. The focused gate has no skips. All 127 offline
checks pass. Source reconciliation covers 1,888 Java/POM files and 139 unchanged
fixture parts against the tested snapshot and pinned dependency. The 24 prior
coupled CSV files and typed benchmark error vectors remain unchanged; twelve
new static-low coupled CSV files are retained separately. These counts are
overlapping gates, not a count of distinct test methods or independent reference
trajectory acceptances.

## Reproducibility and storage interruption

The Java/POM baseline is `49358e00a537a5aea6d1c7360aaf6eff2626cea8` plus this
slice. Concurrent documentation-only commits do not change the tested code;
the source audit reconciles the actual final tree. The established isolated
build uses `-am`, offline cache and core identity
`1.4.3-salient-refresh-20261019`; its jar SHA-256 remains
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.

The first focused launch failed before testing because the drive was full.
A completed task-generated observation build directory was losslessly NTFS-
compressed, and every one of its 5,379 file hashes was checked unchanged.
System free space also recovered independently; compression itself reduced
stored data by about 24 MB. No evidence, pinned dependency, protected directory
or unrelated source file was deleted or moved. Testing resumed with a fresh
run identifier; bootstrap failure is not counted as a passed gate.

The private evidence destination is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-static-low`.
It retains every rejected attempt, the healthy controls, exact reports and
source/dependency identities, the compression log, new coupled exports and
the preceding evidence inventory. Existing raw references and bounds remain
unchanged.

## Remaining work

Native compatibility for the degenerate transducer remains unaccepted. The
frequency convention, numerical order reductions, conditional aggregate and
zero-gain behavior, remaining degenerate/high-zero cases, lifecycle and full
independent coupled trajectory requirements remain open. A green source-
equation/SMIB gate is not complete PSS5C verification or completion of the
broader model plan. No full Texas2k dynamic simulation is run.
