# Selecting the Newton execution mode

The mode belongs to each `LoadflowAlgorithm` instance. Set it before calling
`loadflow()`; changes apply at the next solve boundary. The network selects polar
or rectangular coordinates independently. Both modes of preparation use the
same formulation for the selected coordinates.

| Mode | Behavior |
| --- | --- |
| `AUTO` | Retain supported preparation; otherwise report and use fresh evaluation. |
| `CACHED` | Require reusable preparation; reject an unsupported solver/model/backend with a diagnostic. |
| `GENERIC` | Prepare fresh electrical inputs through the common formulations. |

The following complete example accepts a supplier that imports a fresh ordinary
network. It exercises all three modes in both coordinate forms. Configure Java
KLU once during application setup for the cached examples.

```java
import java.util.function.Supplier;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrExecutionMode;
import com.interpss.core.algo.NrExecutionReport;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

public final class NrModeExamples {
    public static LoadflowAlgorithm solve(AclfNetwork network, boolean polar,
                                         NrExecutionMode mode) throws Exception {
        network.setPolarCoordinate(polar);
        var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
        algorithm.setLfMethod(AclfMethodType.NR);
        algorithm.setNrExecutionMode(mode);
        if (!algorithm.loadflow()) {
            throw new IllegalStateException("Load flow failed: "
                    + algorithm.getTerminationReport());
        }
        NrExecutionReport report = algorithm.getNrExecutionReport();
        System.out.printf("%s, polar=%s, actual=%s, builds=%d, factors=%d%n",
                report.requestedMode(), report.polar(), report.actualStrategy(),
                report.preparationBuilds(), report.factorizations());
        for (var diagnostic : report.diagnostics()) {
            System.out.println(diagnostic.component() + ": " + diagnostic.reason());
        }
        return algorithm;
    }

    public static void bothCoordinates(Supplier<AclfNetwork> freshNetwork)
            throws Exception {
        SparseEqnSolverProvider.useJavaKlu();
        for (boolean polar : new boolean[]{true, false}) {
            for (NrExecutionMode mode : NrExecutionMode.values()) {
                solve(freshNetwork.get(), polar, mode);
            }
        }
    }

    public static void unsupportedCacheThenAuto(AclfNetwork network)
            throws Exception {
        SparseEqnSolverProvider.useCSJ();
        network.setPolarCoordinate(true);
        var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
        algorithm.setLfMethod(AclfMethodType.NR);
        algorithm.setNrExecutionMode(NrExecutionMode.CACHED);
        try {
            algorithm.loadflow();
            throw new IllegalStateException("Expected strict cache rejection");
        } catch (UnsupportedOperationException unsupported) {
            System.out.println(unsupported.getMessage());
        }
        algorithm.setNrExecutionMode(NrExecutionMode.AUTO);
        if (!algorithm.loadflow(false)) {
            throw new IllegalStateException("Fresh fallback failed");
        }
        var report = algorithm.getNrExecutionReport();
        if (report.actualStrategy() != NrExecutionReport.Strategy.GENERIC
                || report.diagnostics().isEmpty()) {
            throw new IllegalStateException("Expected an explained fresh fallback");
        }
    }
}
```

`SparseEqnSolverProvider` changes process-wide sparse factories. Run the CSJ
demonstration separately from an application configured for Java KLU. Mode
selection itself remains per algorithm instance. Unsupported coordinate/model
equations remain explicit; `AUTO` does not select different coordinates.

The report describes the most recent solve. Its build/refresh counters are
per-solve observations, so a reused plan may report zero builds. Cached selection
does not promise retained voltages or unchanged coefficients for every model.
The existing mutation guards still choose structural rebuild, numerical
refactorization or bounded LU reuse independently of the requested mode.

The former `setPreparedNewtonEnabled(false)` maps to `GENERIC`; `true` maps to
`AUTO`. Existing public compatibility hooks remain available. For supported
models and remaining implementation work, see the
[consolidation plan](newton-raphson-consolidation-plan.md) and
[coverage manifest](nr-consolidation-test-coverage.json).
