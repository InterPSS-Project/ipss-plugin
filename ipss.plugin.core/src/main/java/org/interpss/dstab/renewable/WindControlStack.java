package org.interpss.dstab.renewable;

/**
 * Typed signal hub for the WTAR_A/WTDTA1/WTPT_A/WTTQ_A stack. If WTDTA1 is
 * absent, the selected electrical-controller convention either retains the
 * initialized shaft speed (PSS/E) or follows the torque speed reference.
 */
public final class WindControlStack {
    private Wtara1Model aerodynamics;
    private Wtpta1Model pitchController;
    private Wttqa1Model torqueController;
    private Wtdta1Model driveTrain;
    private Wtdtb1Model torsionalEmulation;
    private double generatorSpeed = 1.0;
    private double turbineSpeed = 1.0;
    private double pref;
    /** PSS/E retains initialized SPEED when no mechanical model supplies it. */
    private boolean holdInitialSpeedWithoutDriveTrain;
    private boolean predictorActive;
    private double predictorGeneratorSpeed;
    private double predictorTurbineSpeed;
    private double predictorPref;
    private double predictorMechanicalPower;
    void setSinglePrecisionStates(boolean enabled) {
        if (torqueController!=null) torqueController.setSinglePrecisionStates(enabled);
        if (pitchController!=null) pitchController.setSinglePrecisionStates(enabled);
        if (driveTrain!=null) driveTrain.setSinglePrecisionStates(enabled);
    }

    public void initialize(double power) {
        if(torsionalEmulation!=null)torsionalEmulation.setAerodynamicPresent(aerodynamics!=null);
        WindDriveTrain driveTrain=activeDriveTrain();
        Wttqa1Model torqueController=activeTorqueController();
        Wtpta1Model pitchController=activePitchController();
        predictorActive = false;
        generatorSpeed = turbineSpeed = 1.0;
        pref = power;
        if (torqueController != null) {
            torqueController.initialize(power);
            generatorSpeed = turbineSpeed = torqueController.getSpeedReference();
            pref = torqueController.getPref();
        }
        if (driveTrain != null) {
            driveTrain.initialize(power, generatorSpeed);
            generatorSpeed = driveTrain.getGeneratorSpeed();
            turbineSpeed = driveTrain.getTurbineSpeed();
            if (torqueController != null) {
                torqueController.initialize(power, generatorSpeed);
                pref = torqueController.getPref();
            }
        }
        if (aerodynamics != null) aerodynamics.initialize(driveTrain == null
                ? power : driveTrain.getInitialInputPower());
        if (pitchController != null) {
            double theta0 = aerodynamics == null ? 0.0 : aerodynamics.getData().theta0();
            pitchController.initialize(theta0, turbineSpeed);
        }
    }

    public void step(double dt, double electricalPower, double pOrder) {
        step(dt, electricalPower, pOrder, false);
    }

    public void step(double dt, double electricalPower, double pOrder,
            boolean voltageDip) {
        Wttqa1Model torqueController=activeTorqueController();
        double powerReference = torqueController == null
                ? electricalPower : torqueController.getInitialPower();
        step(dt, electricalPower, pOrder, powerReference, voltageDip);
    }

    /** Advance the complete stack with REPCA1's absolute {@code Pref0}. */
    public void step(double dt, double electricalPower, double pOrder,
            double powerReference, boolean voltageDip) {
        WindDriveTrain driveTrain=activeDriveTrain();
        Wttqa1Model torqueController=activeTorqueController();
        Wtpta1Model pitchController=activePitchController();
        if (driveTrain != null) {
            double mechanicalPower = aerodynamics == null
                    ? driveTrain.getInitialInputPower() : aerodynamics.getMechanicalPower();
            driveTrain.step(dt, mechanicalPower, electricalPower);
            generatorSpeed = driveTrain.getGeneratorSpeed();
            turbineSpeed = driveTrain.getTurbineSpeed();
        }
        if (torqueController != null) {
            if (driveTrain == null && !holdInitialSpeedWithoutDriveTrain) {
                generatorSpeed = turbineSpeed = torqueController.getSpeedReference();
            }
            torqueController.step(dt, electricalPower, generatorSpeed,
                    powerReference, voltageDip);
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

    /**
     * Advances the complete wind-control stack on one modified-Euler stage.
     * Every flag-1 component evaluates its derivative with the same predicted
     * internal endpoint captured at flag 0; no component observes another
     * component's already-corrected final state during that evaluation.
     */
    public void step(double dt, double electricalPower, double pOrder,
            double powerReference, boolean voltageDip, int flag) {
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("wind-stack integration flag must be 0 or 1");
        }
        if (flag == 1 && !predictorActive) {
            throw new IllegalStateException("wind-stack corrector called without predictor");
        }
        if (flag == 0) {
            stepPredictor(dt, electricalPower, pOrder, powerReference, voltageDip);
        } else {
            stepCorrector(dt, electricalPower, pOrder, powerReference, voltageDip);
        }
    }

    private void stepPredictor(double dt, double electricalPower, double pOrder,
            double powerReference, boolean voltageDip) {
        WindDriveTrain driveTrain=activeDriveTrain();
        Wttqa1Model torqueController=activeTorqueController();
        Wtpta1Model pitchController=activePitchController();
        double mechanicalPower = aerodynamics == null
                ? driveTrain == null ? electricalPower : driveTrain.getInitialInputPower()
                : aerodynamics.getMechanicalPower();
        // Every predictor derivative must use the same old internal endpoint.
        // Do not let the torque controller observe an already-predicted drive-
        // train speed or let the pitch controller observe an already-predicted
        // torque command.
        double oldGeneratorSpeed = generatorSpeed;
        double oldTurbineSpeed = turbineSpeed;
        double oldPref = pref;
        if (driveTrain != null) {
            driveTrain.step(dt, mechanicalPower, electricalPower, 0);
        }
        if (torqueController != null) {
            torqueController.step(dt, electricalPower, oldGeneratorSpeed,
                    powerReference, voltageDip, 0);
        }
        if (pitchController != null) {
            pitchController.step(dt, pOrder, oldPref, oldTurbineSpeed, 0);
        }
        generatorSpeed = driveTrain == null
                ? torqueController == null || holdInitialSpeedWithoutDriveTrain ? oldGeneratorSpeed
                        : torqueController.getSpeedReference()
                : driveTrain.getGeneratorSpeed();
        turbineSpeed = driveTrain == null ? generatorSpeed : driveTrain.getTurbineSpeed();
        if (torqueController != null) pref = torqueController.getPref();
        if (aerodynamics != null) {
            double pitch = pitchController == null
                    ? aerodynamics.getData().theta0() : pitchController.getPitch();
            aerodynamics.step(pitch);
        }
        predictorGeneratorSpeed = generatorSpeed;
        predictorTurbineSpeed = turbineSpeed;
        predictorPref = pref;
        predictorMechanicalPower = aerodynamics == null
                ? mechanicalPower : aerodynamics.getMechanicalPower();
        predictorActive = true;
    }

    private void stepCorrector(double dt, double electricalPower, double pOrder,
            double powerReference, boolean voltageDip) {
        WindDriveTrain driveTrain=activeDriveTrain();
        Wttqa1Model torqueController=activeTorqueController();
        Wtpta1Model pitchController=activePitchController();
        if (driveTrain != null) {
            driveTrain.step(dt, predictorMechanicalPower, electricalPower, 1);
        }
        if (torqueController != null) {
            torqueController.step(dt, electricalPower, predictorGeneratorSpeed,
                    powerReference, voltageDip, 1);
        }
        if (pitchController != null) {
            pitchController.step(dt, pOrder, predictorPref,
                    predictorTurbineSpeed, 1);
        }
        generatorSpeed = driveTrain == null
                ? torqueController == null || holdInitialSpeedWithoutDriveTrain ? generatorSpeed
                        : torqueController.getSpeedReference()
                : driveTrain.getGeneratorSpeed();
        turbineSpeed = driveTrain == null ? generatorSpeed : driveTrain.getTurbineSpeed();
        if (torqueController != null) pref = torqueController.getPref();
        if (aerodynamics != null) {
            double pitch = pitchController == null
                    ? aerodynamics.getData().theta0() : pitchController.getPitch();
            aerodynamics.step(pitch);
        }
        predictorActive = false;
    }

    public boolean isComplete() {
        return torsionalEmulation != null && aerodynamics == null
                || aerodynamics != null && pitchController != null && torqueController != null;
    }

    /** All differential inputs are captured before any component is advanced. */
    void stepTwoStep(double dt, double electricalPower, double pOrder,
            double powerReference, boolean voltageDip) {
        WindDriveTrain driveTrain=activeDriveTrain();
        Wttqa1Model torqueController=activeTorqueController();
        Wtpta1Model pitchController=activePitchController();
        double oldSpeed = generatorSpeed;
        double oldTurbineSpeed = turbineSpeed;
        double oldPref = pref;
        double mechanicalPower = aerodynamics == null
                ? driveTrain == null ? electricalPower : driveTrain.getInitialInputPower()
                : aerodynamics.getMechanicalPower();
        if (driveTrain != null) driveTrain.stepTwoStep(dt, mechanicalPower, electricalPower);
        if (torqueController != null) torqueController.stepTwoStep(dt, electricalPower,
                oldSpeed, powerReference, voltageDip);
        if (pitchController != null) pitchController.stepTwoStep(dt, pOrder, oldPref, oldTurbineSpeed);
        generatorSpeed = driveTrain == null
                ? torqueController == null || holdInitialSpeedWithoutDriveTrain ? oldSpeed
                        : torqueController.getSpeedReference()
                : driveTrain.getGeneratorSpeed();
        turbineSpeed = driveTrain == null ? generatorSpeed : driveTrain.getTurbineSpeed();
        // Publish algebraic torque*speed using the committed endpoint speed.
        if (torqueController != null) {
            torqueController.stepTwoStep(0.0, electricalPower, generatorSpeed,
                    powerReference, voltageDip);
            pref = torqueController.getPref();
        }
        if (aerodynamics != null) aerodynamics.step(pitchController == null
                ? aerodynamics.getData().theta0() : pitchController.getPitch());
    }

    public Wtara1Model getAerodynamics() { return aerodynamics; }
    public void setAerodynamics(Wtara1Model model) { aerodynamics = model; }
    public Wtpta1Model getPitchController() { return pitchController; }
    public void setPitchController(Wtpta1Model model) { pitchController = model; }
    public Wttqa1Model getTorqueController() { return torqueController; }
    public void setTorqueController(Wttqa1Model model) { torqueController = model; }
    public Wtdta1Model getDriveTrain() { return driveTrain; }
    public void setDriveTrain(Wtdta1Model model) {
        if(torsionalEmulation!=null)throw new IllegalStateException("A WTDTB1 drive train is already attached");
        driveTrain = model;
    }
    public Wtdtb1Model getTorsionalEmulation() { return torsionalEmulation; }
    public void setTorsionalEmulation(Wtdtb1Model model) {
        if(driveTrain!=null||torsionalEmulation!=null)
            throw new IllegalStateException("A wind drive train is already attached");
        torsionalEmulation=java.util.Objects.requireNonNull(model);
    }
    /** Configured torque/pitch records are ignored in Type-4A torsional-emulation mode. */
    public boolean isTorqueControllerActive() { return activeTorqueController()!=null; }
    private boolean emulatesPitch() { return torsionalEmulation!=null&&aerodynamics==null; }
    private WindDriveTrain activeDriveTrain() { return torsionalEmulation!=null?torsionalEmulation:driveTrain; }
    private Wttqa1Model activeTorqueController() { return emulatesPitch()?null:torqueController; }
    private Wtpta1Model activePitchController() { return emulatesPitch()?null:pitchController; }
    public double getGeneratorSpeed() { return generatorSpeed; }
    void setHoldInitialSpeedWithoutDriveTrain(boolean value) {
        holdInitialSpeedWithoutDriveTrain = value;
    }
    public double getTurbineSpeed() { return turbineSpeed; }
    /** WTTQA1 already multiplies torque by speed, matching the ANDES wiring. */
    public double getElectricalControllerSpeed() {
        return activeTorqueController() == null ? generatorSpeed : 1.0;
    }
    public double getPref() { return pref; }
}
