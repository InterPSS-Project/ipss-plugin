package org.interpss.dstab.renewable;

/** WECC WTPT_A pitch controller with PI, angle, and angle-rate limits. */
public final class Wtpta1Model {
    private static final double EPS = 1.0e-9;

    private final Wtpta1Data data;
    private double speedReference;
    private double speedIntegral;
    private double compensationIntegral;
    private double pitch;

    public Wtpta1Model(Wtpta1Data data) {
        this.data = data;
    }

    public void initialize(double theta0, double speed) {
        speedReference = speed;
        speedIntegral = Repca1Model.limit(theta0, data.thetaMin(), data.thetaMax());
        compensationIntegral = 0.0;
        pitch = speedIntegral;
    }

    public void step(double dt, double pOrder, double pReference, double speed) {
        double powerError = pOrder - pReference;
        double compensationError = powerError;
        double speedError = data.kcc() * powerError + speed - speedReference;
        compensationIntegral = Repca1Model.integrateWithAntiWindup(compensationIntegral,
                data.kic(), compensationError, dt, data.kpc(),
                data.thetaMin(), data.thetaMax(), false);
        speedIntegral = Repca1Model.integrateWithAntiWindup(speedIntegral,
                data.kiw(), speedError, dt, data.kpw(),
                data.thetaMin(), data.thetaMax(), false);
        double compensation = Repca1Model.limit(
                data.kpc() * compensationError + compensationIntegral,
                data.thetaMin(), data.thetaMax());
        double speedPath = Repca1Model.limit(data.kpw() * speedError + speedIntegral,
                data.thetaMin(), data.thetaMax());
        double target = Repca1Model.limit(compensation + speedPath,
                data.thetaMin(), data.thetaMax());
        double derivative = data.tp() <= EPS ? (target - pitch) / Math.max(dt, EPS)
                : (target - pitch) / data.tp();
        derivative = Repca1Model.limit(derivative, data.dThetaMin(), data.dThetaMax());
        pitch = Repca1Model.limit(pitch + dt * derivative, data.thetaMin(), data.thetaMax());
    }

    public Wtpta1Data getData() { return data; }
    public double getPitch() { return pitch; }
    public double getSpeedReference() { return speedReference; }
}
