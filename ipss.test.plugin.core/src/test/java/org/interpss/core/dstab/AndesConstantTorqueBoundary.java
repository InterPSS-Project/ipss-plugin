package org.interpss.core.dstab;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.common.DStabSimuException;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;

/**
 * Match the mechanical boundary of the ANDES 2.0.0 no-governor fixtures.
 * Its genbase.py holds tm=tm0; PSS/E GENROU instead holds mechanical power.
 * See https://github.com/CURENT/andes/blob/v2.0.0/andes/models/synchronous/genbase.py.
 * The exciter equations and all reference values/tolerances remain unchanged.
 */
final class AndesConstantTorqueBoundary extends DStabSolverImpl {
    private final String machineId;
    private double torque;

    private AndesConstantTorqueBoundary(DynamicSimuAlgorithm algo,String machineId) {
        super(algo); this.machineId=machineId;
    }

    static void install(DynamicSimuAlgorithm algo,String machineId) {
        algo.setSolver(new AndesConstantTorqueBoundary(algo,machineId));
    }

    @Override public boolean initialization() {
        if (!super.initialization()) return false;
        var m=dstabAlgo.getNetwork().getMachine(machineId);
        if (!(m instanceof IeeeVcRoundRotorMachine) || m.hasGovernor() || m.getD()!=0.)
            throw new IllegalArgumentException("Expected an undamped native GENROU without a governor");
        torque=m.getPm()/m.getSpeed();
        return true;
    }

    @Override public void diffEqnIntegrationStep(double t,double dt,DynamicSimuMethod method,int flag)
            throws DStabSimuException {
        var m=dstabAlgo.getNetwork().getMachine(machineId);
        // At each integration stage, P=torque*speed supplies the constant torque
        // required by ANDES through the native model's power-input boundary.
        m.setPm(torque*m.getSpeed());
        super.diffEqnIntegrationStep(t,dt,method,flag);
    }
}
