package org.interpss.dstab.mach;

import com.interpss.dstab.BaseDStabBus;

/** Shared command and measurement contract for the WT3G1/WT3G2 hosts. */
public interface Wt3GeneratorModel {
    BaseDStabBus<?, ?> getDStabBus();
    double getAggregateRatedMw();
    /** MVA base of terminal P/Q measurements and active-current commands. */
    double getDeviceBaseMva();
    /** Terminal active power on the generator device base. */
    double getP();
    /** Terminal reactive power on the generator device base. */
    double getQ();
    double getInternalVoltageState();
    void setCommands(double activeCurrentCommand, double internalVoltageCommand);
    Wt3e1Model getElectricalController();
    void setElectricalController(Wt3e1Model controller);
    Wt3t1Model getDriveTrain();
    void setDriveTrain(Wt3t1Model driveTrain);
}
