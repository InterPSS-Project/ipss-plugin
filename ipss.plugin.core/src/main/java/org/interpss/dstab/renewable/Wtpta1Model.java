package org.interpss.dstab.renewable;

/** WECC WTPT_A pitch controller with PI, angle, and angle-rate limits. */
public final class Wtpta1Model {
    private static final double EPS = 1.0e-9;

    private final Wtpta1Data data;
    private double speedReference;
    private double speedIntegral;
    private double compensationIntegral;
    private double pitch;
    private PitchState predictorStart;
    private PitchDerivative predictorDerivative;

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

    /**
     * Advances one modified-Euler stage for the two PI paths and pitch servo.
     * The complete-step API remains unchanged until the renewable-stack
     * coordinator can switch every dependent model to staged execution at once.
     */
    public void step(double dt, double pOrder, double pReference, double speed,
            int flag) {
        if (!Double.isFinite(dt) || dt < 0.0 || !Double.isFinite(pOrder)
                || !Double.isFinite(pReference) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException(
                    "WTPTA1 staged inputs must be finite and dt must be non-negative");
        }
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("WTPTA1 integration flag must be 0 or 1");
        }
        if (dt <= 0.0) return;
        if (flag == 0) {
            predictorStart = state();
            predictorDerivative = derivatives(predictorStart, dt, pOrder,
                    pReference, speed);
            apply(add(predictorStart, predictorDerivative, dt));
        } else {
            if (predictorStart == null || predictorDerivative == null) {
                throw new IllegalStateException("WTPTA1 corrector called without predictor");
            }
            PitchDerivative corrected = derivatives(state(), dt, pOrder,
                    pReference, speed);
            apply(correct(predictorStart, predictorDerivative, corrected, dt));
            predictorStart = null;
            predictorDerivative = null;
        }
    }

    private PitchDerivative derivatives(PitchState state, double dt,
            double pOrder, double pReference, double speed) {
        double powerError = pOrder - pReference;
        double speedError = data.kcc() * powerError + speed - speedReference;
        double compensationRate = integralRate(state.compensationIntegral(),
                data.kic(), powerError, data.kpc());
        double speedRate = integralRate(state.speedIntegral(), data.kiw(),
                speedError, data.kpw());
        double compensation = Repca1Model.limit(
                data.kpc() * powerError + state.compensationIntegral(),
                data.thetaMin(), data.thetaMax());
        double speedPath = Repca1Model.limit(
                data.kpw() * speedError + state.speedIntegral(),
                data.thetaMin(), data.thetaMax());
        double target = Repca1Model.limit(compensation + speedPath,
                data.thetaMin(), data.thetaMax());
        double pitchRate = data.tp() <= EPS
                ? (target - state.pitch()) / dt : (target - state.pitch()) / data.tp();
        pitchRate = Repca1Model.limit(pitchRate, data.dThetaMin(), data.dThetaMax());
        if ((state.pitch() >= data.thetaMax() && pitchRate > 0.0)
                || (state.pitch() <= data.thetaMin() && pitchRate < 0.0)) {
            pitchRate = 0.0;
        }
        return new PitchDerivative(speedRate, compensationRate, pitchRate);
    }

    private double integralRate(double integral, double gain, double error,
            double proportionalGain) {
        double output = proportionalGain * error + integral;
        if ((output >= data.thetaMax() && error > 0.0)
                || (output <= data.thetaMin() && error < 0.0)) return 0.0;
        return gain * error;
    }

    private PitchState state() {
        return new PitchState(speedIntegral, compensationIntegral, pitch);
    }

    private void apply(PitchState state) {
        speedIntegral = state.speedIntegral();
        compensationIntegral = state.compensationIntegral();
        pitch = Repca1Model.limit(state.pitch(), data.thetaMin(), data.thetaMax());
    }

    private static PitchState add(PitchState state, PitchDerivative derivative,
            double dt) {
        return new PitchState(
                state.speedIntegral() + dt * derivative.speedIntegral(),
                state.compensationIntegral() + dt * derivative.compensationIntegral(),
                state.pitch() + dt * derivative.pitch());
    }

    private static PitchState correct(PitchState start, PitchDerivative first,
            PitchDerivative second, double dt) {
        return new PitchState(
                start.speedIntegral() + .5 * dt
                        * (first.speedIntegral() + second.speedIntegral()),
                start.compensationIntegral() + .5 * dt
                        * (first.compensationIntegral() + second.compensationIntegral()),
                start.pitch() + .5 * dt * (first.pitch() + second.pitch()));
    }

    public Wtpta1Data getData() { return data; }
    public double getPitch() { return pitch; }
    public double getSpeedReference() { return speedReference; }
    public double getSpeedIntegral() { return speedIntegral; }
    public double getCompensationIntegral() { return compensationIntegral; }

    private record PitchState(double speedIntegral, double compensationIntegral,
            double pitch) { }

    private record PitchDerivative(double speedIntegral, double compensationIntegral,
            double pitch) { }
}
