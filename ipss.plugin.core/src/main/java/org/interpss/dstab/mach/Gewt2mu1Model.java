package org.interpss.dstab.mach;

import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** GE two-mass wind-turbine shaft attached to a GE converter host. */
public final class Gewt2mu1Model implements ICMLStateProvider {
    private final Gewt2mu1Data data;
    private final Wt12t1Model kernel;

    public Gewt2mu1Model(Gewt2mu1Data data) {
        this.data = data;
        // Native GEWT2MU1 uses the shaft torque directly in the generator
        // equation. The turbine/generator speed ratio applies to WT12T1.
        this.kernel = new Wt12t1Model(data.asTwoMassData(), false);
    }

    public void initialize(double electricalPower, double speed, double frequencyHz) {
        kernel.initialize(electricalPower, speed, frequencyHz);
    }

    public void step(double dt, double electricalPower, int flag) {
        kernel.step(dt, electricalPower, flag);
    }

    public Gewt2mu1Data getData() { return data; }
    public double getAerodynamicPower() { return kernel.getAerodynamicPower(); }
    public void setAerodynamicPower(double value) { kernel.setAerodynamicPower(value); }
    public double getShaftAngle() { return kernel.getShaftAngle(); }
    public double getTurbineSpeed() { return kernel.getTurbineSpeed(); }
    public double getGeneratorSpeed() { return kernel.getGeneratorSpeed(); }
    public double getGeneratorAngleDeviation() { return kernel.getGeneratorAngleDeviation(); }
    public double shaftStiffness() { return kernel.shaftStiffness(); }

    @Override
    public Map<String, Double> getNamedStates() { return kernel.getNamedStates(); }
}
