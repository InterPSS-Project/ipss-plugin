package org.interpss.core.dstab;

import org.interpss.dstab.algo.StaggeredEulerSolver;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;

/** Select the integration path while retaining its declared default numerical policy. */
final class StaggeredExciterFixture {
    private StaggeredExciterFixture(){}
    /** Resolve truncation error at a common finer step without relaxing any channel gate. */
    static double referenceStep(boolean optimized,double originalStep){return optimized?originalStep/2:originalStep;}
    static void configure(DynamicSimuAlgorithm algorithm,boolean optimized){
        if(optimized){
            var solver=new StaggeredEulerSolver(algorithm);
            solver.setReuseNetworkEndpoint(true);
            algorithm.setSolver(solver);
        }
    }
    static void discontinuity(DynamicSimuAlgorithm algorithm){
        if(algorithm.getSolver() instanceof PsseStaggeredEulerSolver solver)solver.notifyDiscontinuity();
    }
}
