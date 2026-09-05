package org.interpss.dstab.renewable;

/** Common signal contract between a REGC_A converter and REEC_A/REEC_B controls. */
public interface RenewableElectricalController {
    void initialize(double p, double q, double v);
    void step(double dt, double p, double q, double v, double frequency);
    double getIpcmd();
    double getIqcmd();
    Repca1Model getPlantController();
    void setPlantController(Repca1Model plantController);
}
