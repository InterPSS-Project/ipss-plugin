package org.interpss.core.dstab;

import java.util.List;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/**
 * Electrical boundary of the PowerWorld GENROU reference fixtures, whose
 * published internal voltage is j*speed*subtransientFlux. PSS/E Flux0 omits
 * that speed multiplier. Controller equations and reference traces are unchanged.
 * Source: PowerWorld WebHelp, TransientModels_PDF/Generator/Machine/Machine Model GENROU.pdf.
 */
final class PowerWorldGenrouBoundary extends RoundRotorMachineImpl {
    static void install(BaseDStabNetwork<?, ?> network) {
        for (var bus : network.getBusList()) {
            for (var gen : bus.getContributeGenList()) {
                if (!(gen.getDynamicGenDevice() instanceof IeeeVcRoundRotorMachine source)) continue;
                if (source.getIeeeVcData() != null)
                    throw new IllegalArgumentException("IEEEVC is not part of these PowerWorld fixtures");
                var target = new PowerWorldGenrouBoundary();
                target.setMachData(EcoreUtil.copy(source.getMachData()));
                target.setId(source.getId());
                target.setName(source.getName());
                target.setStatus(source.isActive());
                target.setMachType(source.getMachType());
                target.setFG(source.getFG());
                target.getControllerList().addAll(List.copyOf(source.getControllerList()));
                gen.setDynamicGenDevice(target);
                target.setParentGen(gen);
                int deviceIndex = bus.getDynamicBusDeviceList().indexOf(source);
                if (deviceIndex >= 0) bus.getDynamicBusDeviceList().set(deviceIndex, target);
            }
        }
    }

    @Override protected double statorVoltageScale() { return getSpeed(); }

    /** Controllers sense electrical power; the stored Flux0 coordinate is torque. */
    @Override public double getPe() { return super.getPe() * getSpeed(); }

    @Override protected double cal_dW_dt(double speed, double baseFrequency) {
        double damping = getD() * .01 * baseFrequency;
        return ((getPm() - damping * (speed - 1.0)) / speed
                - super.getPe() - getPe_NegSeq()) / (2.0 * getH());
    }
}
