package org.interpss.dstab.renewable;

/**
 * Typed signal hub for the WTAR_A/WTPT_A/WTTQ_A stack. Texas2k omits WTDTA1;
 * in that documented direct-coupling path turbine and generator speed follow
 * the torque controller's filtered power-speed characteristic.
 */
public final class WindControlStack {
    private Wtara1Model aerodynamics;
    private Wtpta1Model pitchController;
    private Wttqa1Model torqueController;
    private double generatorSpeed = 1.0;
    private double turbineSpeed = 1.0;
    private double pref;

    public void initialize(double power) {
        pref = power;
        if (torqueController != null) {
            torqueController.initialize(power);
            generatorSpeed = turbineSpeed = torqueController.getSpeedReference();
            pref = torqueController.getPref();
        }
        if (aerodynamics != null) aerodynamics.initialize(power);
        if (pitchController != null) {
            double theta0 = aerodynamics == null ? 0.0 : aerodynamics.getData().theta0();
            pitchController.initialize(theta0, turbineSpeed);
        }
    }

    public void step(double dt, double electricalPower, double pOrder) {
        if (torqueController != null) {
            // No WTDTA1: use the specified direct-coupling path.
            generatorSpeed = turbineSpeed = torqueController.getSpeedReference();
            torqueController.step(dt, electricalPower, generatorSpeed);
            pref = torqueController.getPref();
        }
        if (pitchController != null) {
            pitchController.step(dt, pOrder, pref, turbineSpeed);
        }
        if (aerodynamics != null) {
            double pitch = pitchController == null
                    ? aerodynamics.getData().theta0() : pitchController.getPitch();
            aerodynamics.step(pitch);
        }
    }

    public boolean isComplete() {
        return aerodynamics != null && pitchController != null && torqueController != null;
    }

    public Wtara1Model getAerodynamics() { return aerodynamics; }
    public void setAerodynamics(Wtara1Model model) { aerodynamics = model; }
    public Wtpta1Model getPitchController() { return pitchController; }
    public void setPitchController(Wtpta1Model model) { pitchController = model; }
    public Wttqa1Model getTorqueController() { return torqueController; }
    public void setTorqueController(Wttqa1Model model) { torqueController = model; }
    public double getGeneratorSpeed() { return generatorSpeed; }
    public double getTurbineSpeed() { return turbineSpeed; }
    public double getPref() { return pref; }
}
