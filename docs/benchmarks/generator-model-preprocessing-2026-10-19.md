# Generator model checking and preprocessing

Data checking now collects references to active generators without a dynamic
generator or converter model during its existing bus/device pass. Preparation
consumes that inventory after power flow and before dynamic state initialization
and static-load conversion. It does not repeat the network-wide generator scan.

The inventory stores generator references, rather than P/Q snapshots. Preparation
reads each recorded unit's final solved P/Q, validates all pending powers, nets
the missing generation into a negative load, and takes that generator offline.
Modeled generators at the same bus retain their solved powers, and the solved
bus voltage is preserved. Existing equivalent-load IDs are not overwritten.

The negative load participates in the ordinary configured load conversion. For
the [confirmed Cases 3–6 preparation](case3-unmodeled-generation-diagnostic-2026-10-19.md),
this retains constant-current P and constant-impedance Q. The generic engine also
continues to support other explicitly selected P/I/Z combinations.

## Inventory lifetime

- One data-check pass produces the pending generator inventory.
- Solved P/Q and voltage updates leave the inventory valid.
- Preparation consumes the pending entries; repeated preparation is a no-op.
- Generator attachment/removal, generator or bus status changes, dynamic-model
  attachment/removal, and bus membership changes invalidate the inventory.
- If checking was bypassed or the inventory was invalidated, preparation builds
  one lightweight replacement inventory. Subsequent preparation reuses it.
- Programmatically constructed networks that omit the inherited load-flow bus
  checks still get one inventory pass. They can retain their existing workflow
  of attaching dynamic models after power flow; missing-model policy is enforced
  before dynamic initialization. Full data checking retains model validation.

No per-step voltage or P/Q observers are installed. The concrete network exposes
`getDynamicModelInventoryBuildCount()` to verify inventory reuse in tests.

## Import and strict checking

`PSSEMultiFileLoader` enables missing-model netting by default, while retaining
the active generators through import and power flow. The default solver calls
`prepareDStabModels()` before initializing the network; direct `initDStabNet()`
callers receive the same preparation.

Callers that require every active generator to have a dynamic model can disable
netting with `setAllowGenWithoutMach(false)`. Missing models then fail full data
checking and preparation before state initialization. Netting also rejects an unsolved
power flow or non-finite pending generator power.

## Validation

- The final core regression passed 3,006 tests, including 27 focused
  preprocessing tests. Coverage includes programmatic networks that attach
  models after power flow, subclass accessors that require attachment, and
  deactivation of offline generator devices.
- Loader/netting regression: 10 tests passed, including default preprocessing
  and strict rejection through the imported-network path.
- The three-phase regression passed all 35 tests against the final core build.
  The same run also passed all seven benchmark output-guard checks.
- The focused checks cover final solved-power use, one inventory across checking
  and initialization, fallback checking, invalidation, repeated initialization,
  multiple generators, load-ID collisions, and all nine P/I/Z combinations.
- The registered plugin suite ran 2,551 tests: 2,533 passed, 13 were skipped,
  and five failed. All five reproduce using the pre-change core sources: the
  Case 1 flat-run voltage drift, the 8,387-bus PQ power-flow case, two delimiter
  fixtures with non-finite shunts, and the P4 voltage-comparison fixture.
- All six cases passed the three-cycle fault and generator-trip smoke tests.
  The flat-run smoke stops at the existing Case 1 Bus1051 voltage-drift limit.

The broad suite was run with a 2 GiB Java heap. Two earlier attempts were
interrupted by disk exhaustion and are not counted as validation. The successful
core run, completed plugin-suite report, focused checks, and baseline comparison
are retained locally under `target/preinit-netting-20261019/`.

These checks establish preprocessing behavior and inventory reuse. They do not
establish a new six-case reference-trajectory acceptance result or a simulation
runtime improvement. The broader comparison goal remains open.
