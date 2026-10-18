package org.interpss.dstab.renewable;

/** Common staged mechanical boundary; concrete models retain their own state coordinates. */
public interface WindDriveTrain {
    void initialize(double power,double speed);
    void step(double dt,double mechanicalPower,double electricalPower);
    void step(double dt,double mechanicalPower,double electricalPower,int flag);
    void stepTwoStep(double dt,double mechanicalPower,double electricalPower);
    double getInitialInputPower();
    double getGeneratorSpeed();
    double getTurbineSpeed();
}
