# Dynamic simulation solver selection

`DefaultDynamicSimulationSolver` is the default for imported positive-sequence
dynamic cases. It extends the generic core solver with initialization and
model-lifecycle policies. Both normally use the same modified-Euler integration
method. The additional class does not select a different integration formula.

The import loader already installs this solver. After `loadDStab(...)`, retrieve
the attached solver when needed; constructing and installing another instance
is unnecessary. Explicit installation is useful when creating an algorithm
through the core factory and choosing the imported-case policies.

## Which default is selected

| Entry point | Attached solver |
| --- | --- |
| `DStabObjectFactory.createDynamicSimuAlgorithm(network)` | Core `DStabSolverImpl` |
| `PSSEMultiFileLoader.loadDStab(...)` | Plugin `DefaultDynamicSimulationSolver` |
| Explicit `algorithm.setSolver(new StaggeredEulerSolver(algorithm))` | Optional staggered Euler solver |

The core factory belongs to the engine and does not depend on plugin classes.
The loader supplies the imported-case policy layer after creating the core
algorithm. The deprecated solver names remain compatibility entrypoints for
the same implementation as their neutral replacements.

## Differences from the generic core solver

The table describes untouched defaults and the additional initialization hooks.
Callers can explicitly configure the generic engine and individual models.

| Behavior | Generic core solver | Imported-case default solver |
| --- | --- | --- |
| Integration method | Uses the algorithm's selection; the factory selects `MODIFIED_EULER` | Uses the same algorithm selection |
| Parameter preparation | Core/network/model initialization | Also runs plugin parameter rules and configures `IntegrationStepAware` models before state initialization |
| Initial network balance | Uses the initialized dynamic network | Also retains a fixed Norton current residual to preserve the solved operating point, unless caller-owned custom current injections take precedence |
| Constant-current load reduction at low voltage | Network default is `SINE`, using the configured switch voltage | Selects `ELLIPTIC`, with reduction below 0.5 pu |
| Bus-frequency measurements | Standard factories create measurement blocks; the solver does not select dependencies | Selects required local, remote and output dependencies; unknown/custom consumers retain all-bus coverage |
| Controller preparation | Existing CML execution; callers/models can select compiled paths | Automatically prepares qualified controller graphs for modified Euler; custom graphs keep compatible execution |
| Model-specific event memory | General engine event and network lifecycle | Also synchronizes bus-frequency angle memory and supported voltage-derivative/plant-controller acceptance hooks at the prescribed boundaries |
| Maximum network iterations | Constructor default is 20 | Constructor default is 50; setters accept 1 through 50 |
| Network convergence defaults | `1e-4` pu voltage correction, `MAX_COMPONENT`, two minimum fresh iterations | Inherits those settings; benchmark overrides remain explicit |
| Anderson network mixing | Disabled by default | Disabled by default; qualified bounded mixing requires explicit opt-in |

The constant-current curve changes only the low-voltage reduction of the
existing current-load component. It does not choose the active/reactive ZIP
fractions. Load conversion, generator netting and coordinated power-flow
controls remain separate preparation decisions.

The initial network residual has the form:

```text
I_fixed = Y_dynamic * V_initial - I_devices(V_initial)
```

Keeping that residual prevents a representation or rounding difference between
the solved power-flow network and dynamic Norton network from immediately
moving the algebraic operating point. Caller-owned custom current-injection
tables are preserved. This residual is distinct from the auxiliary converter
Norton compensation removed by the optimized network profile.

Preserving initial network balance does not force controller derivatives to
zero, repair incorrect models or make an intentionally non-flat case flat.
The load-current and initialization policies can produce different trajectories
from an otherwise untouched generic core setup. That is different from changing
between the deprecated and neutral names, which retain the same implementation.

## Using the solver already supplied by the loader

Imports used in these examples:

```java
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
```

```java
var context = new PSSEMultiFileLoader().loadDStab("case.raw", "case.dyr");
var algorithm = context.getDynSimuAlgorithm();
var solver = (DefaultDynamicSimulationSolver) algorithm.getSolver();
```

The last line obtains the existing solver for configuration or diagnostics.
Set the case's integration step, duration and output selection before
initialization, then solve power flow and initialize as usual. The constructor
does not automatically select the Texas benchmark's quarter-cycle step,
ten-second duration, tighter tolerance or full recording profile.

## Creating an algorithm manually

If `network` is an existing positive-sequence dynamic network and the
imported-case policies are wanted:

```java
var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
algorithm.setSolver(new DefaultDynamicSimulationSolver(algorithm));
```

Install the solver before initialization. Constructing a solver object alone
does not replace `algorithm.getSolver()`. Keep the generic core solver when its
existing network/model policies are the intended experiment. Specialized
three-phase or hybrid solvers retain their own network and coupling roles.

## Selecting staggered integration

```java
var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
algorithm.setSolver(new StaggeredEulerSolver(algorithm));
algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
```

This is a different integration recurrence with its own derivative history and
event policy. `MODIFIED_EULER` remains the underlying derivative API selection;
it does not turn the staggered recurrence into ordinary predictor/corrector
integration. Use a fixed step and check model support at initialization.

## Shared performance features

Sparse factor reuse, primitive current buffers, packed network iteration,
converter-compensation reformulation and accepted-endpoint certificates are
implemented in the shared engine. They are not separate numerical engines
provided only by the imported-case wrapper. Their qualification and explicit
profile settings still apply. Selecting this class alone does not enable the
complete optimized benchmark profile or establish reference accuracy.

See [controller preparation and runtime settings](benchmarks/modified-euler-controller-preparation-2026-10-25.md)
and [bus-frequency selection](benchmarks/bus-frequency-selection-2026-10-25.md)
for those specific policies.

The [current-handoff comparison](benchmarks/texas2k-current-handoff-2026-10-25.md)
separates the primitive interface from its dependent packed and endpoint paths,
including whole-run timing and numerical differences with the property OFF/ON.

## Source locations

The core factory and defaults are in `DStabObjectFactory`,
`AbstractDStabSolver`, `DStabSolverImpl` and `BaseDStabNetworkImpl` in the sibling
core repository. Plugin entrypoints and selection are in
[DefaultDynamicSimulationSolver](../ipss.plugin.core/src/main/java/org/interpss/dstab/algo/DefaultDynamicSimulationSolver.java),
[StaggeredEulerSolver](../ipss.plugin.core/src/main/java/org/interpss/dstab/algo/StaggeredEulerSolver.java)
and [PSSEMultiFileLoader](../ipss.plugin.core/src/main/java/org/interpss/fadapter/psse/PSSEMultiFileLoader.java).
