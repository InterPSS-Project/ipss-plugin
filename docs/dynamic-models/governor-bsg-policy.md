# Initial governor travel policy

Comma-separated governor BSG files and whitespace/double-quoted renewable
BASEGEN files have separate parsers and target contracts. The multi-file loader
dispatches by the first data row, not the `.bsg` extension alone. Governor input
is explicit; the loader does not execute the compiled source routine or discover
an arbitrary neighboring governor policy.

```text
1, 'GEN, NORTH', 13.8, '1 ', 1
2, 'GEN SOUTH', 13.8, '1', 0
```

Five fields are required: bus number, single-quoted bus name, base kV,
single-quoted generator ID, and integer code. Doubled single quotes and quoted
commas are supported. Conflicting duplicate target codes and malformed rows fail
with source/line diagnostics before replay. Entered bus/name/kV/ID/code fields
are retained in the report. Generator ID padding is trimmed for lookup.

## Public API and lifecycle

```java
var loader = new PSSEMultiFileLoader();
var context = loader.loadDStab("case.rawx", "case.dyr", "governors.bsg");
var registered = loader.getLastGovernorBlockingEntries();
var algorithm = context.getDynSimuAlgorithm();
var solver = (PsseDStabSolver) algorithm.getSolver();
// Solve load flow and configure the ordinary dynamic algorithm, then:
if (!algorithm.initialization()) throw new IllegalStateException("Initialization failed");
var outcomes = solver.getGovernorBlockingReport();
```

Manual solvers can register `PsseGovernorBsgProcessor.read(path)` with
`solver.setGovernorBlockingPolicy(entries)` before initialization. The default
solver inherits this API. Retrieve the loader-installed solver instead of
replacing it and losing its registration.

Registration is inert. Replay happens after `acceptInitialPlantEndpoints()`
returns successfully and before controller execution plans are compiled. Startup
shaft-power reconciliation may initialize a governor again, so applying earlier
would allow the final initialization to overwrite the cap. This is an initial
policy, not an event or an integration-stage callback. The core's initial output
callback precedes this final hook; the dedicated policy report is authoritative
for the final travel limits. Reinitializing a model restores its original
numerical limits; the solver then reapplies its registered policy.

## Model-owned effective limits

Codes 1 and 2 use the same upper cap in the tested source routine. Code 0 leaves
normal governor operation unchanged. No policy changes a lower bound, reference,
state, integration history, turbine characteristic, entered data object or
immutable constant array. Each supported model exposes
`applyInitialUpperTravelLimit(double value)` for the numerical upper bound.

| Family | Initialized coordinate | Effective storage |
|---|---|---|
| TGOV1 | `getValvePosition()` | `vmax` and the existing numeric T1 block limiter |
| GGOV1 | `getValveStroke()` | `effectiveVmax` |
| HYGOV | `getGatePosition()` | `effectiveGmax` |
| IEEEG3 | `getGatePosition()` | explicit-equation `pmax`; inactive annotation blocks are untouched |
| WSHYGP | `getGatePosition()` | runtime maximum, separate from entered constant 13 |
| WSIEG1 | projected `getValvePosition()` | runtime `upper`; shared shafts resolve one owner |

TGOV1's annotation is converted to a numeric CML block limit during construction.
Changing only its public `vmax` would leave that limiter stale. The API updates
it in place; it does not reconstruct or initialize blocks. Compiled and
compatible CML execution read the same live limiter. Existing non-windup and
predictor/corrector behavior remains owned by the block.

## Coordinate evidence and precision

The cap is the initialized valve/gate value, with **no added epsilon**. A
requested mechanical power of 0.5 need not produce exactly 0.5 at initialization
because the initialized machine boundary includes losses. Early differences
near 1e-5 were not evidence of a policy margin.

Retained synthetic native snapshots distinguish the coordinates:

- TGOV1: initial valve state and written upper CON were both 0.5000100136.
- GGOV1: initial valve 0.2850236297, written upper 0.2850239873.
- WSHYGP: initial gate 0.4893430173, written upper VAR 0.4893429875.
- Nonlinear WSIEG1: initial projected gate and written maximum were both
  0.5333399773.
- Clipped nonlinear WSIEG1: unprojected requirement 0.5333399773, initial maximum
  and projected valve 0.2000000030; the routine made no changes. This rules out
  using the inverse-curve requirement or initial reference to expand travel.

The small GGOV1/WSHYGP differences are consistent with six-decimal textual
writeback followed by single-precision storage. This implementation retains the
full-precision initialized coordinate, without assuming identical formatting
for every family. Native comparisons allow 1e-6 absolute travel error. Internal
retention checks remain exact. The boundary discriminator is reproduced with a
synthetic nonlinear curve and a clipped upper limit in `GovernorBsgPolicyTest`.
Source probe artifacts remain in the private QA evidence tree.

## Retained outcomes

`Entry` exposes source, line, original generator key, resolved generator ID, entered code/fields, status, model,
initialized position, old maximum, new maximum, and message. Registration and
outcome lists are immutable; retain the complete list, not only applied rows.

Exact generator IDs take precedence. If an exact target is absent, lookup may
use one unique case-insensitive match. Multiple candidates produce `ERROR`
without changing a model. Both the entered ID and resolved ID remain in the
report; inactive or no-governor targets retain their usual non-applied outcome.
Resolution is recomputed for each replay rather than reused from a prior network.

- `REGISTERED`: not yet replayed.
- `NORMAL`: code 0; no change.
- `APPLIED`: effective maximum updated.
- `UNCHANGED`: maximum already equals initialized travel, including clipped WSIEG1.
- `MISSING_TARGET`, `INACTIVE_TARGET`, `UNSUPPORTED_MODEL`: explicitly not applied.
- `DUPLICATE_OWNER`: a repeated/shared turbine owner already handled once.
- `ERROR`: numerical update failed; solver initialization fails with the report retained.

Unavailable numerical fields are null rather than fabricated old/new values.
Unsupported families and inactive/missing targets are reported without disabling
or substituting their controllers. Additional dialects are not automatically
qualified merely because they share a Java implementation. Existing conflicting
bounds remain visible and are not reordered by this policy.

Focused gates cover all six families, both nonzero codes, code 0, valid flat
behavior, immutable entered data and state/history retention, reinitialization,
TGOV1's live numeric block limit, quoted format errors, loader registration/reset,
post-initialization replay, and WSIEG1 nonlinear/clipped/conflicting/shared cases.
These gates establish policy mechanics, not full-system contingency stability.
