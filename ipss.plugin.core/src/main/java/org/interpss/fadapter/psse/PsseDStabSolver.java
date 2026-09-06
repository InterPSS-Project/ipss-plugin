package org.interpss.fadapter.psse;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.device.DynamicDevice;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.IntegrationStepAware;

/**
 * PSS/E dynamic solver adapter that preserves a solved RAW initial condition
 * when the load-flow and dynamic network representations differ slightly.
 *
 * <p>The ordinary dynamic network is a Norton {@code YV=I} system and may not
 * use control and zero-impedance-branch equations present in the load-flow
 * solution but absent from the dynamic Norton matrix. Rounded RAW data can
 * therefore leave a small, but
 * dynamically visible, KCL residual when the representation changes. Capture
 * that residual as a fixed Norton compensation current before the first
 * integration step.</p>
 */
final class PsseDStabSolver extends DStabSolverImpl {
    private static final String COMPENSATION_KEY =
            PsseDStabSolver.class.getName() + ".compensation";
    PsseDStabSolver(DynamicSimuAlgorithm algorithm) {
        super(algorithm);
    }

    @Override
    public boolean initialization() {
        configureIntegrationStepAwareModels(dstabAlgo.getNetwork(), dstabAlgo.getSimuStepSec());
        if (!super.initialization()) return false;

        BaseDStabNetwork<?, ?> network = dstabAlgo.getNetwork();
        // Do not overwrite a hybrid-simulation current source supplied by a caller.
        Hashtable<String, Complex> existing = network.getCustomBusCurrInjHashtable();
        Object previous = network.getExtraInfo().get(COMPENSATION_KEY);
        if (existing != null && existing != previous) return true;

        Hashtable<String, Complex> compensation = new Hashtable<>();
        Complex[] voltage = new Complex[network.getNoBus()];
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            voltage[bus.getSortNumber()] = bus.isActive() ? bus.getVoltage() : Complex.ZERO;
        }
        Complex[] networkCurrent = network.getYMatrix().multiply(voltage);
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            try {
                Complex deviceCurrent = bus.injCurDynamic(network.getStaticLoadModel(),
                        network.getStaticLoadSwitchVolt(),
                        network.getStaticLoadSwitchDeadZone());
                Complex residual = networkCurrent[bus.getSortNumber()].subtract(deviceCurrent);
                if (residual.abs() > 1.0e-10) compensation.put(bus.getId(), residual);
            } catch (Exception e) {
                return false;
            }
        }
        network.setCustomBusCurrInjHashtable(compensation);
        network.getExtraInfo().put(COMPENSATION_KEY, compensation);
        return true;
    }

    static void configureIntegrationStepAwareModels(BaseDStabNetwork<?, ?> network,
            double timeStep) {
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            for (DStabGen generator : bus.getContributeGenList()) {
                DynamicDevice device = generator.getDynamicGenDevice();
                configure(device, timeStep);
                if (device instanceof Machine machine) {
                    configure(machine.getExciter(), timeStep);
                    configure(machine.getGovernor(), timeStep);
                    configure(machine.getStabilizer(), timeStep);
                }
            }
        }
    }

    private static void configure(Object model, double timeStep) {
        if (model instanceof IntegrationStepAware aware) {
            aware.configureIntegrationStep(timeStep);
        }
    }

}
