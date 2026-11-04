package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.common.DStabSimuException;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;

/** Explicit fixture policy with certificates for each accepted network solution. */
final class CertifiedWeakGridSolver extends DefaultDynamicSimulationSolver {
    private int calls, maximumIterations;
    private double maximumCorrection;

    CertifiedWeakGridSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
        setNetworkAccelerationEnabled(true);
    }

    @Override public boolean networkSolutionStep() throws DStabSimuException {
        boolean converged = super.networkSolutionStep();
        var diagnostic = getLastNetworkSolutionDiagnostics();
        assertTrue(converged, diagnostic.toString());
        assertTrue(diagnostic.converged(), diagnostic.toString());
        assertEquals(50, diagnostic.iterationLimit());
        assertTrue(diagnostic.iterations() <= 50, diagnostic.toString());
        assertEquals(1.e-4, diagnostic.voltageTolerance(), 0.);
        // The solver certifies the original fixed-point correction, never the
        // smaller mixed update. This is a voltage residual, not a KCL residual.
        assertTrue(Double.isFinite(diagnostic.maximumVoltageChange()));
        assertTrue(diagnostic.maximumVoltageChange() <= diagnostic.voltageTolerance(),
                diagnostic.toString());
        calls++;
        maximumIterations = Math.max(maximumIterations, diagnostic.iterations());
        maximumCorrection = Math.max(maximumCorrection, diagnostic.maximumVoltageChange());
        return converged;
    }

    void assertAccelerationExercised() {
        assertCertifiedSolutions();
        assertTrue(getNetworkAccelerationApplications() > 0);
    }

    void assertCertifiedSolutions() {
        assertTrue(calls > 0);
        System.out.println("Weak-grid certificate: calls=" + calls + ", maximum iterations="
                + maximumIterations + ", maximum original voltage correction=" + maximumCorrection
                + ", bounded mixes=" + getNetworkAccelerationApplications());
    }
}
