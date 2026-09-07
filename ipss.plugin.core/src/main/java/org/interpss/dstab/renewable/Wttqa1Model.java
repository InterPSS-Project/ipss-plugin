package org.interpss.dstab.renewable;

/** WECC WTTQ_A torque/active-power-reference controller. */
public final class Wttqa1Model {
    /** Suppress only algebraic partitioning roundoff at an initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;
    private final Wttqa1Data data;
    private double initialPower;
    private double filteredPower;
    private double speedReference;
    private double torqueIntegral;
    private double torque;
    private double pref;

    public Wttqa1Model(Wttqa1Data data) {
        this.data = data;
    }

    public void initialize(double power) {
        initialize(power, speedForPower(power));
    }

    /** Initialize torque and PI bias at the connected drive train's speed. */
    public void initialize(double power, double generatorSpeed) {
        if (!Double.isFinite(power) || !Double.isFinite(generatorSpeed)
                || generatorSpeed <= 0.0) {
            throw new IllegalArgumentException(
                    "WTTQA1 initial power and generator speed must be finite and positive");
        }
        initialPower = filteredPower = power;
        speedReference = speedForPower(power);
        double initialError = data.tFlag() == 1 ? 0.0
                : speedReference - generatorSpeed;
        torque = power / nonzero(generatorSpeed);
        torqueIntegral = torque - data.kpp() * initialError;
        pref = power;
    }

    public void step(double dt, double electricalPower, double generatorSpeed) {
        step(dt, electricalPower, generatorSpeed, false);
    }

    public void step(double dt, double electricalPower, double generatorSpeed,
            boolean voltageDip) {
        step(dt, electricalPower, generatorSpeed, initialPower, voltageDip);
    }

    /**
     * Advance the controller with the current plant-level active-power
     * reference. WECC connects REPCA1's active output to WTTQA1 {@code Pref0};
     * it is therefore an input to the power-error branch rather than an
     * increment added after the torque controller.
     */
    public void step(double dt, double electricalPower, double generatorSpeed,
            double powerReference, boolean voltageDip) {
        if (!Double.isFinite(powerReference)) {
            throw new IllegalArgumentException("WTTQA1 Pref0 must be finite");
        }
        filteredPower = Repca1Model.lag(filteredPower, electricalPower, data.tp(), dt);
        speedReference = Repca1Model.lag(speedReference, speedForPower(filteredPower),
                data.twref(), dt);
        // WECC Figure 3-6 selects (Pref0-filtered Pe)/wg for TFLAG=1 and
        // (wref-wg) for TFLAG=0. This sign is also the physically negative-
        // feedback form of the active-power loop.
        double error = data.tFlag() == 1
                ? (powerReference - filteredPower) / nonzero(generatorSpeed)
                : speedReference - generatorSpeed;
        if (Math.abs(error) <= EQUILIBRIUM_RESIDUAL) error = 0.0;
        torqueIntegral = Repca1Model.integrateWithAntiWindup(torqueIntegral, data.kip(), error,
                dt, data.kpp(), data.teMin(), data.teMax(), voltageDip);
        torque = Repca1Model.limit(data.kpp() * error + torqueIntegral,
                data.teMin(), data.teMax());
        pref = torque * generatorSpeed;
    }

    public double speedForPower(double power) {
        double[] p = {data.p1(), data.p2(), data.p3(), data.p4()};
        double[] s = {data.sp1(), data.sp2(), data.sp3(), data.sp4()};
        if (power <= p[0]) return s[0];
        for (int i = 1; i < p.length; i++) {
            if (power <= p[i]) {
                return s[i - 1] + (power - p[i - 1]) * (s[i] - s[i - 1])
                        / (p[i] - p[i - 1]);
            }
        }
        return s[s.length - 1];
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    public Wttqa1Data getData() { return data; }
    public double getPref() { return pref; }
    public double getTorque() { return torque; }
    public double getFilteredPower() { return filteredPower; }
    public double getSpeedReference() { return speedReference; }
    public double getTorqueIntegral() { return torqueIntegral; }
    public double getInitialPower() { return initialPower; }
}
