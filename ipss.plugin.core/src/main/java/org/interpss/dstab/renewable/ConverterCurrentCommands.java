package org.interpss.dstab.renewable;

/** Initialized constant current commands when a converter has no electrical-control record.
 * Optional upstream controls retain their states but do not replace the absent electrical port.
 */
public final class ConverterCurrentCommands implements RenewableElectricalController, com.interpss.dstab.controller.cml.ICMLStateProvider {
    private double ipcmd, iqcmd, initialPower;
    private ActivePowerResponseLimits response = ActivePowerResponseLimits.NORMAL;
    private Repca1Model plant;
    private WindControlStack wind;
    private boolean initialized;

    @Override public void initialize(double p, double q, double v) {
        initialPower = p;
        ipcmd = p / Math.max(.01, v);
        iqcmd = -q / Math.max(.01, v);
        if (plant != null) {
            plant.setActivePowerResponseLimits(response);
            plant.initialize(p, q, v);
        }
        if (wind != null) wind.initialize(p);
        initialized = true;
    }
    @Override public void step(double dt, double p, double q, double v, double frequency) {
        step(dt, p, q, v, frequency, 0);
        step(dt, p, q, v, frequency, 1);
    }
    @Override public void step(double dt, double p, double q, double v, double frequency, int flag) {
        if (plant != null) plant.step(dt, p, q, v, frequency, flag);
        if (wind != null) wind.step(dt, p, initialPower, getPlantPowerReference(initialPower), false, flag);
    }
    @Override public java.util.Map<String, Double> getNamedStates() {
        return java.util.Map.of("Active Current Command", ipcmd, "Reactive Current Command", iqcmd);
    }
    @Override public double getIpcmd() { return ipcmd; }
    @Override public double getIqcmd() { return iqcmd; }
    @Override public Repca1Model getPlantController() { return plant; }
    @Override public void setPlantController(Repca1Model controller) { plant = controller; }
    @Override public WindControlStack getWindControlStack() { return wind; }
    @Override public void setWindControlStack(WindControlStack stack) {
        wind = stack;
        if (wind != null) wind.setHoldInitialSpeedWithoutDriveTrain(true);
    }
    @Override public ActivePowerResponseLimits getActivePowerResponseLimits() { return response; }
    @Override public void setActivePowerResponseLimits(ActivePowerResponseLimits limits) {
        java.util.Objects.requireNonNull(limits);
        if (initialized && response != limits)
            throw new IllegalStateException("Configure response limits before initialization");
        response = limits;
    }
}
