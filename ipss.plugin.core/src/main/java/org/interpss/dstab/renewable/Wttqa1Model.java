package org.interpss.dstab.renewable;

/** WECC WTTQ_A torque/active-power-reference controller. */
public final class Wttqa1Model {
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
        initialPower = filteredPower = power;
        speedReference = speedForPower(power);
        torque = power / nonzero(speedReference);
        torqueIntegral = torque;
        pref = power;
    }

    public void step(double dt, double electricalPower, double generatorSpeed) {
        filteredPower = Repca1Model.lag(filteredPower, electricalPower, data.tp(), dt);
        speedReference = Repca1Model.lag(speedReference, speedForPower(filteredPower),
                data.twref(), dt);
        double error = data.tFlag() == 1
                ? (electricalPower - initialPower) / nonzero(generatorSpeed)
                : speedReference - generatorSpeed;
        torqueIntegral = Repca1Model.integrateWithAntiWindup(torqueIntegral, data.kip(), error,
                dt, data.kpp(), data.teMin(), data.teMax(), false);
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
    public double getSpeedReference() { return speedReference; }
}
