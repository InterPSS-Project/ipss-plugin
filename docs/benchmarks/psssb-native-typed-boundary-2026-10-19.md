# PSSSB native-reader / typed-model boundary

The native reader no longer accepts the PowerWorld/PSLF `PSSSB` layout as
a native DYR model. Its explicit typed builder and runtime are retained.
The approved-list native mapping is `PSS2A`, not a spelling alias for the
boost-equipped typed model. Renaming a PSSSB record is not a data conversion:
the field order, width and topology differ.

## Source and discriminator

The approved source catalog retains its May 2026 PDF identity,
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.
The installed model-library PDF has identity
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`;
its 1,059-page text search contains no PSSSB entry. Absence in that search
alone is not the acceptance/rejection discriminator.

Two fresh processes per model load the same tiny solved SMIB network and
the existing exact model-specific input fixtures. Native PSS2A attaches with
6 ICON, 17 CON, 16 STATE and 4 VAR allocations. PSSSB reports an undefined
model, ignores the record, and returns error 4 for every stabilizer-allocation
query. The dynamic-file load call itself returns zero in both cases. Hence
file-load success must not be confused with model attachment or conformance.

`tools/psssb_dialect_probe.py` reproduces the check; use model `PSSSB` or
`PSS2A` and a fresh alphanumeric run tag. Run under the installed native
simulator's Python environment. `tools/audit_psssb_dialect.py` accepts the four
run folders and verifies the logs, input/output hashes, repeat identities,
allocations and shared engine/library identities. Exact tool provenance and
raw logs remain in private evidence, not public prose.

The two final repeat tags are `b` and `c`. Initial tag `a` pilots are retained:
the supported-model pilot failed an incorrect probe assertion about its
state/VAR counts. That assertion was removed from the allocation-only probe;
no model constants or production dynamics were changed to make it pass.

PowerWorld separately documents the transient branch's switch, lag, washout,
voltage limit, boost magnitude and cutout fields. They remain in the typed
model rather than being silently discarded by an alias. See
[PowerWorld's PSSSB reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSSSB.htm).

## Implementation and regression contract

- Remove PSSSB from the native catalog and dispatch. Both ordinary and strict
  reader paths report it as unsupported; strict import throws. The diagnostic
  explains the non-interchangeable PSS2A layout and explicit builder alternative.
- Rejected records cannot replace an already attached typed stabilizer.
- Map the approved row to the real native PSS2A path. Keep the fourteen-entry
  evidence inventory: thirteen source-family mappings plus separate typed
  PSSSB evidence. Do not delete its benchmark to make the inventory pass.
- Construct PSSSB through explicit named data fields in focused/SMIB tests.
  Load only GENROU, ESST1A and GENCLS through the native reader. The old
  PSSSB fixture remains unchanged as reference-tool input, not native evidence.
- Preserve the runtime equations, core GENROU, controller values, 26 channel
  limits, checkpoints, original event exclusions and comparison horizon.
  This slice neither expands those exclusions nor claims the old short-window
  comparison covers the full one-second trace.

Focused tests cover the native/typed boundary, import status, unchanged device
attachment, typed boost states, cutout and existing trajectory comparison.
Offline negative checks reject corrupted inputs, installation failures passed
off as unsupported models, absent rejection logs and contradictory allocation.

Final regression reports contain 104 focused tests, 427 core-plugin tests
and 1,832 scoped SMIB/model/solver tests, with zero failures/errors and seven
existing skips across the latter two gates. The 64 offline checks and paired
cold-allocation auditor pass. The generated support/evidence documents match
their Java generators. The source audit reconciles 1,876 Java/build files,
139 unchanged reference-resource parts and the pinned core dependency.
No full-system dynamic gate is included.

The isolated build uses `tmp/wtdtb1-powerworld-verify54b/pom.xml`, profiles
`isolated-verification,extended-tests`, offline dependency cache
`target/repca-native-verification-m2-20261019`, and core identity
`1.4.3-salient-refresh-20261019`. Reactor integration tests use `-am`.
The focused selection is `DynamicModelCatalogTest`,
`DStabNetworkBuilderStabilizerTest`, `PsssbIndependentSmibConformanceTest`
and `EmbeddedTrajectoryCoverageInventoryTest`; core units use `-Dtest=*`.
The broad gate retains the preceding slice's complete scoped selection,
including `DynamicModelSmibTestSuite` and its model/solver supplement.
Report run identifiers are `psssb-dialect-focused-2`,
`psssb-dialect-units-1` and `psssb-dialect-smib-1` under `.verification-build/`.
The initial focused run's stale generated-matrix failure is also retained,
not counted as a passing final gate.

The private seal is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-psssb-native-typed-boundary-final`.
It retains scoped sources, reports, pilots, final cold runs, fixtures and
dependency provenance, with a hash inventory. Text-fixture comparison permits
only CRLF-to-LF normalization; both original byte forms are retained and hashed.
No numerical reference normalization or acceptance-bound change is made.

## Remaining work

- Complete typed PSSSB selector/limit/lifecycle profiles and all-row independent
  trajectory acceptance beyond the legacy short window.
- Audit the distinct PSS1A input dialect; its typed evidence is not a native
  schema contract.
- Continue the wider model-coverage plan. No full Texas2k dynamic simulation
  is run or claimed by this slice.
