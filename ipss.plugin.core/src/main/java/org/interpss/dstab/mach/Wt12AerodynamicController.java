package org.interpss.dstab.mach;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Common coupling boundary for Type-1/2 aerodynamic controllers. */
public interface Wt12AerodynamicController extends ICMLStateProvider {
    void initialize(double electricalPower, double turbineSpeedDeviation,
            double aerodynamicPower, double terminalVoltage);
    void step(double dt, double electricalPower, double turbineSpeedDeviation,
            double terminalVoltage, int flag);
    double getOutput();
    double getSpeedReference();
    double getPowerReference();
}
