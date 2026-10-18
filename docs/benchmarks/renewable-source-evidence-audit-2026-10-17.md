# Approved renewable source and evidence audit

## Material coverage correction

The May 2026 approved-model PDF contains renewable families absent from the
runtime catalog. The earlier loadability matrix enumerated implemented models;
it was not a complete transcription of the renewable source section. Checking
only that matrix could therefore miss a model entirely, rather than detect an
unsupported status. Texas2k's input-model coverage does not prove coverage of
every model in the approved list.

Visual review of pages 3-4 and the new approval-catalog transcription establish:

- 45 source renewable rows, including approved, retired, unapproved and
  never-approved rows.
- 29 approved rows; 28 have concrete native PTI names. PVD1's PTI cell is blank,
  so its PowerWorld name is not invented as a native PSS/E alias.
- 27 distinct planned native families, because WT1T and WT2T share WT12T1.
- **17 loadable families and ten genuinely missing implementations.** This is
  not a statement that the 17 loadable models are fully verified.

The missing families are **REGFMB1, REGFMC1, REECE1/REECEU1, WTGWGOAU,
WTGIBFFRA, WTPTBU1, WTDTBU1, REPCCU, HPLNTD and REPCGFMC1**. Their planning
mappings do not create runtime descriptors, guessed parameter counts, dummy
classes, import aliases or fallback substitutions.

The source is the user's five-page May 2026 PDF, SHA-256
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.
Its page renders, exact source file, implementation snapshot and test reports
are preserved privately. The approval catalog retains frozen source statuses,
including the REPC_B row's anomalous printed date, rather than guessing a
corrected year or replacing it with today's status.

## Implementation and evidence inventories

`WeccApprovedDynamicModelCatalog.renewables()` retains all source rows and
separates planned/runtime mapping from approval. `hasPsseModel()` now also
rejects annotated phrases such as `Not converted (1)` as model identifiers.
Descriptive downloadable vendor-package cells are kept in comments, not turned
into fictitious native record names. The source REA3XBU1 interface spelling is
retained as a source fact, not silently corrected into a parser alias.

The generated [renewable evidence inventory](../renewable-model-evidence.md)
links every approved native family, including missing ones:

- Eight have native recorded-profile links.
- REGCA1 has a partial native Iolim/selected-endpoint profile; its complete
  high-voltage/fault/event-post gap is not hidden.
- Six have the bounded coupled ANDES comparison, not native PSS/E conformance.
  The test explicitly selects an ANDES-specific WTTQA speed convention and
  documents REECA VFLAG/REPCA dialect differences.
- REECD and WT12A1U_B have focused tests but no linked exact independent
  model-specific trajectory. The older WT12A1 reference is not accepted as
  WT12A1U_B evidence.
- Ten have no parser/runtime implementation.

Inventory guards check set completeness, uniqueness, runtime and focused-test
presence, suite registration, actual SMIB discovery patterns, checkpoint
readability/integrity, fixture model/cardinality and generated-document sync.
The native REGFMA1 checkpoint map retains its existing fixed fingerprints;
legacy inline REGCA1 data is classified as partial with its provenance/digest
audit explicitly open. The inventory itself is not new simulator evidence and
does not label any model VERIFIED.

## Previously omitted tests

`Type3WindAndesTrajectoryTest` and `RenewableAggregateQvModeTest` were registered
in the core suite but did not match `DynamicModelSmibTestSuite`'s include-name
patterns. Both use small public networks/controllers, not full Texas2k dynamics.
They are now explicitly selected. The inventory checks both selection and
registration so that a class name cannot silently drop the evidence gate.

The new focused run includes these existing checks and passes without changing
their tolerances, physical parameters, references or vendor-mode settings.
The ANDES voltage/state envelope remains a bounded cross-tool comparison, not
proof of native-model identity. The Type-3 PowerWorld comparison currently
asserts finite diagnostic errors only and is not relabeled accepted parity.

## Verification snapshot

The focused inventory/catalog/newly selected checks pass **35 tests, zero
failures/errors/skips**. The expanded final gate passes **1,191 SMIB/model tests,
zero failures/errors, one existing opt-in diagnostic skip**. The final plugin-core
gate passes **135 tests, zero failures/errors, six optional skips** (four KlusolveX
native checks and two AUX fixture checks). All nine scoped catalog/test/generated-
document files match the tested snapshot byte-for-byte.

Run IDs are `renewable-evidence-focused-20261017`,
`renewable-evidence-smib-final-20261017` and
`renewable-evidence-unit-final-20261017`, using `extended-tests,isolated-verification`
and core override `1.4.3-regfma-native`. The full selection is
`DynamicModelSmibTestSuite,DynamicModelCatalogTest`; the complete unit-module run
selects `ipss.plugin.core` with `-am`. Fresh Surefire XML, not a quiet Maven exit,
establishes each count.

The reproducible snapshot includes committed GENSAL torque correction
`16dbd253e`, committed renewable two-step work `94321b10c`, the PSS1A slices and
this catalog/evidence/discovery slice. Current uncommitted governor/staggered-
solver edits and the unrelated JAX test are excluded from the isolated snapshot
and left untouched. No full Texas2k, WECC or IEEE39 dynamics are run.

The ignored private `2026-10-17-renewable-source-evidence` archive preserves the
exact PDF and page renders, source/fixture/checkpoint snapshots, generated
documents, focused logs and completed final test reports. All **732 evidence
files** are sealed and hash-verified with a SHA-256 inventory. No proprietary
source PDF or raw simulator script is force-added to Git.

## Next implementation work

The plan now lists all ten missing families as actual implementation TODOs,
not benchmark-only gaps. Installed PSS/E 36.7 documentation contains relevant
entries, sometimes under newer standard names such as WTDTB1, WTPTB1, REPCC,
REECE and WTGWGOA. Those entries do not by themselves establish compatibility
with a historical USRMDL name or allocation.

The next model slice should freeze WTDTBU1/current WTDTB1 native schemas,
implement its drivetrain-emulation states and attachment, generate small native
PSS/E and PowerWorld discriminators, verify initialization/limits/integration,
run the full selected SMIB/unit gates, and commit separately. The updated
development plan also retains the pitch/weak-grid/inertial/electrical/plant/GFM
families, exact-reference gaps, supplemental legacy/vendor evidence expansion,
and the remaining non-renewable source-section audit. This renewable audit does
not claim that every other section of the approved list is complete.
