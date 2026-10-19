# PSS1A native-reader / typed-model boundary

The native DYR reader no longer accepts the separate fourteen-value PSS1A
typed interface as a native model record. The approved-list native mapping
is IEEEST. The dedicated PSS1A runtime and explicit `addPss1a` builder are
retained, including exact PowerWorld playback and the distinctly labelled
IEEEST-equivalent playback. No controller or solver equation changes here.

## Evidence and source contract

The approved-list source PDF remains identified by
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`;
its PSS1A row names IEEEST in the native column. The installed model-library
identity retained by the cold probes is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.

Four fresh small-case processes, two per exact model-specific fixture, prove
that the installed engine ignores PSS1A as undefined and leaves no stabilizer
allocation; all eight allocation queries return error 4. The same solved
SMIB network with native IEEEST allocates 2 ICON, 17 CON, 7 STATE and 2 VAR
entries. The dynamic-file load call returns zero for both. This is allocation
evidence, not a trajectory-conformance test or a claim about every tool version.

The native IEEEST layout has nineteen values, including two selectors and
seventeen constants, rather than the typed PSS1A interface's fourteen values.
Renaming a record does not convert its field order, filters or state realization.
The reader diagnostic directs callers to native IEEEST data or the explicit
typed API; it neither invents an alias nor drops parameters.

PowerWorld documents a distinct PSS1A parameter list with one input code,
two filter coefficients, lead/lag constants, washout fields, gain, output
limits and cutout thresholds. Its existing exact typed benchmarks remain
separate from the native IEEEST comparison. See
[PowerWorld's PSS1A reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS1A.htm).

## Repeatability and regression scope

`tools/pss1a_dialect_probe.py` takes `PSS1A` or `IEEEST` and a fresh
alphanumeric tag under the installed simulator's Python environment. It
stages both its source and the shared helper, preserves the input files and
log, and hashes all evidence. `tools/audit_pss1a_dialect.py` validates the four
folders against a fixed allocation contract using the shared auditor.
Repeat tags `a` and `b` preserve identical inputs, readback constants,
allocations and engine identities. Tool-version provenance stays private.

Four reader-boundary tests cover strict and ordinary import with an empty
or already-attached device. All four fail against the unchanged old reader.
After correction, unsupported records neither attach nor replace a device.
Existing selector, invalid-code and state-response checks now call the typed
builder directly instead of relying on an invented native record.

The equivalent-signal test loads only the native machine/exciter stack and
attaches typed PSS1A explicitly. Its input values, output tolerance,
interpolation, sample windows and event exclusions are unchanged. The exact
PowerWorld playback and active-limit/cutout test sources are untouched.
No gain, state, output bound, reference point or acceptance envelope is fitted.

The source list now resolves to twelve distinct catalog-backed family mappings plus
two separate typed evidence entries. All fourteen evidence links remain;
PSS1A stays `EQUIVALENT_SIGNAL_PLAYBACK` in its primary row and PSSSB stays
`TYPED_MODEL_SPECIFIC_COMPARISON`. Neither is silently upgraded or deleted.
The generated support and evidence documents follow these explicit contracts.

## Final gates and retained artifacts

Final reports contain 118 focused tests, 427 core-plugin tests and 1,836 scoped
SMIB/model/solver tests, with zero failures/errors. The latter two gates retain
six and one existing skips respectively. All 69 offline checks and both the
PSS1A/IEEEST and previous PSSSB/PSS2A allocation auditors pass. The thirteen
printed native/equivalent/typed comparison-result lines match the preceding
slice byte-for-byte; that check concerns reported error vectors, not a new
claim of complete trajectory equivalence.

The source audit reconciles 1,876 Java/build files and 139 unchanged reference
resource parts. It confirms the isolated build uses the intended pinned core,
not another cached version. Concurrent full-system documentation/acceptance
metadata is preserved but is not used to alter these SMIB acceptance criteria.

The build entry is `tmp/wtdtb1-powerworld-verify54b/pom.xml`, offline profiles
`isolated-verification,extended-tests`, cache
`target/repca-native-verification-m2-20261019` and core identity
`1.4.3-salient-refresh-20261019`. Reactor integration tests use `-am`.
The focused selection includes `DynamicModelCatalogTest`,
`DStabNetworkBuilderStabilizerTest`, `IeeestPsseSmibConformanceTest`,
`Pss1aPowerWorldPlaybackConformanceTest`, `Pss1aPowerWorldActiveConformanceTest`,
`PsssbIndependentSmibConformanceTest` and `EmbeddedTrajectoryCoverageInventoryTest`.
Core units use `-Dtest=*`; the broad gate retains the preceding slice's full
`DynamicModelSmibTestSuite` selection and model/solver supplement.
Report run identifiers under `.verification-build/` are
`pss1a-dialect-focused-1`, `pss1a-dialect-units-1` and `pss1a-dialect-smib-1`.
The failing-before gate is `pss1a-dialect-before-1`, with four failures and
zero errors, not counted as a passing final gate.

Private source, input, log, report and dependency evidence is retained at
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss1a-native-typed-boundary`.
Its inventory hashes every retained file and links the preceding sealed slice.
Text-fixture comparison permits CRLF-to-LF normalization only; both raw byte
forms remain individually hashed. Neither reference values nor trajectory
acceptance bounds are normalized or changed.

## Remaining work

- Complete exact typed PSS1A coupled-network SMIB acceptance and the remaining
  selector/lifecycle profiles; exact controller playback is not complete
  closed-loop parity.
- Continue typed PSSSB's broader profiles and the remaining coverage plan.
- Full Texas2k dynamic simulations are outside this task's current scope.
