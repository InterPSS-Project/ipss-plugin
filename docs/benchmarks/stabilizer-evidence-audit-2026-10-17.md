# Stabilizer evidence inventory and source-column correction

## Outcome

All fifteen approved stabilizer source rows now have an explicit inventory
covering fourteen existing InterPSS implementation mappings. This is an
evidence-link audit, not fourteen newly verified models. The generated
[stabilizer inventory](../stabilizer-evidence.md) distinguishes actual source
PTI cells, implementation names, oracle, comparison scope, fixture and readable
checkpoint. Both registered-suite membership and selection by the permanent
SMIB gate are checked automatically.

Thirteen mappings have model-specific comparison links: eight native PSS/E
and five PowerWorld. PSS1A's current comparison replays an IEEEST speed signal
through an equivalent PSS1A configuration. It is explicitly classified as
equivalent signal playback, not an exact PSS1A commercial SMIB reference.
That distinction does not deny the existing native IEEEST benchmark.

## Source bug found during the audit

The supplied `Approved Dynamic Models May 2026_1.pdf`, page 2, has SHA-256
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.
Its table was text-extracted and visually reviewed to preserve column alignment.
The previous catalog invented PTI names from other columns:

| Source row | Correct PTI PSS/E cell | Previous transcription |
|---|---|---|
| PSS1A | IEEEST | PSS1A |
| PSS3C | blank | PSS3C |
| PSS4C | blank | PSS4C |
| PSS5C | blank | PSS5C |

The PSSSB row's PTI cell is PSS2A; its PowerWorld cell is PSSSB. That existing
source distinction is retained. The twelve nonblank approved PTI cells contain
nine distinct names; this is not the same count as fourteen implemented
stabilizer mappings. A blank source cell is not evidence that a later installed
PSS/E release lacks a model. For example, an existing PSS3C comparison declares
native PSS/E 36.7 separately from this May table.

The regression fails before the correction: expected PSS1A source PTI name
IEEEST, observed PSS1A. The corrected test also freezes all three blank cells,
the PSSSB-to-PSS2A source cell and the twelve-cell/nine-name counts.

No implementation was removed, no model equation or simulation tolerance was
changed, and no new DYR alias or PSLF-to-PSS/E conversion was introduced.
The source catalog's metadata is corrected while implementation and benchmark
availability remain separately recorded. In particular, the inventory does not
claim that the dedicated PSS1A/PSSSB record layouts are validated native PTI
aliases merely because their source rows reference IEEEST/PSS2A.

## Permanent guards

- The approved-row set and fourteen implementation mappings must match the
  inventory exactly, without double-counting the duplicated PSS2C source row.
- Every fixture contains its declared implementation record exactly once and
  satisfies that implementation's catalog schema.
- Every focused/comparison class exists, is registered in CorePluginTestSuite,
  and is selected—not excluded—by DynamicModelSmibTestSuite.
- Every linked embedded checkpoint is readable; native checkpoint fingerprints
  are checked through the existing fixed-digest reader.
- PSS1A remains explicitly equivalent-playback-only until exact-model evidence
  is added. Presence of a reference does not assert exhaustive conformance.
- The checked-in Markdown must equal the inventory generator's output.

## Completed validation

The isolated verification checkout contains the five exact changed catalog,
test/inventory and generated-document files, hash-checked before execution.
The existing separate core dependency is unchanged (`1.4.3-regfma-native`).

- **1,158 selected SMIB/model tests: zero failures/errors, one existing opt-in
  REGCA1 diagnostic skipped; exit zero.**
- **130 plugin-core unit tests: zero failures/errors, six optional skips;
  exit zero.** The optional skips concern KlusolveX native solver checks and
  AUX fixtures, not the new source or inventory guards.
- The earlier inventory-only SMIB run passed before the source-column bug was
  found; it is retained but is not substituted for the final corrected run.
- Full Texas2k/WECC/IEEE39 dynamic simulations are neither required nor run.
  No new simulator reference was generated or cold-regeneration claim made.

Commands use the preceding slice's isolated-build profiles and core override,
with run IDs `stabilizer-source-inventory-smib-final-20261017` and
`stabilizer-source-inventory-unit-final-20261017`. The SMIB selection is
`DynamicModelSmibTestSuite,DynamicModelCatalogTest`; plugin-unit selection is
the complete `ipss.plugin.core` module with `-am`.

The ignored private `2026-10-17-stabilizer-evidence` archive preserves the source
PDF/render, source/fixture snapshots, failing-before log, generated inventory
and fresh regression reports with a verified SHA-256 inventory. Unrelated
concurrent Texas2k reference-check edits and the untracked JAX test are excluded.
The sealed archive contains **694 hash-verified evidence files**.

## Remaining gaps

- Exact PSS1A commercial-model SMIB reference; the existing equivalent play-in
  is useful but cannot stand in for that missing profile.
- Audit the dedicated PSS1A and PSSSB input dialects independently of the
  source-list PTI name. Do not infer native aliases or model conversions.
- Raw-version/provenance and twice-cold reproducibility audits for the thirteen
  model-specific links, followed by active-limit/selector/lifecycle coverage.
- Complete the renewable-model evidence inventory and its missing independent
  profiles. The overall SMIB coverage goal remains incomplete.
