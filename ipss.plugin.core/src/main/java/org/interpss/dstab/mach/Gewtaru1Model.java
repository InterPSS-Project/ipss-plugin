package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Aerodynamic conversion from wind speed, shaft speed, and blade pitch. */
public final class Gewtaru1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private static final double MIN_CP = -0.05;
    private static final double[][] CP = {
        {-4.1909e-1, 2.1808e-1, -1.2406e-2, -1.3365e-4, 1.1524e-5},
        {-6.7606e-2, 6.0405e-2, -1.3934e-2, 1.0683e-3, -2.3895e-5},
        {1.5727e-2, -1.0996e-2, 2.1495e-3, -1.4855e-4, 2.7937e-6},
        {-8.6018e-4, 5.7051e-4, -1.0479e-4, 5.9924e-6, -8.9194e-8},
        {1.4787e-5, -9.4839e-6, 1.6167e-6, -7.1535e-8, 4.9686e-10}
    };

    private final Gewtaru1Data data;
    private double turbineRatedMw;
    private double adjustment;
    private double initialPitch;
    private double windVelocity;
    private double pitch;
    private double lambda;
    private double cp;
    private double torque;
    private double oldTorque;
    private double predictorDerivative;
    private boolean predictorActive;
    private boolean initialized;

    public Gewtaru1Model(Gewtaru1Data data) { this.data = data; }

    public void initialize(double initialPower, double turbineSpeed,
            double turbineRatedMw) {
        if (!finite(initialPower, turbineSpeed, turbineRatedMw)
                || turbineSpeed <= 0.0 || turbineRatedMw <= 0.0) {
            throw new IllegalArgumentException("invalid GEWTARU1 initial boundary");
        }
        this.turbineRatedMw = turbineRatedMw;
        initialPitch = pitch = data.pitchMinimum();
        windVelocity = initialWindVelocity(initialPower, turbineSpeed);
        double unadjusted = unadjustedPower(turbineSpeed, windVelocity, pitch);
        adjustment = Math.abs(unadjusted) <= EPS ? 1.0 : initialPower / unadjusted;
        torque = initialPower / turbineSpeed;
        initialized = true;
    }

    public void step(double dt, double turbineSpeed, int flag) {
        if (!initialized || !finite(dt, turbineSpeed) || dt < 0.0 || turbineSpeed <= 0.0) {
            throw new IllegalArgumentException("invalid GEWTARU1 step boundary");
        }
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("flag must be 0 or 1");
        double target = aerodynamicPower(turbineSpeed, windVelocity, pitch) / turbineSpeed;
        if (data.conversionTime() <= EPS || dt == 0.0) {
            torque = target;
            return;
        }
        if (flag == 0) {
            oldTorque = torque;
            predictorDerivative = (target - oldTorque) / data.conversionTime();
            torque = oldTorque + dt * predictorDerivative;
            predictorActive = true;
        } else {
            if (!predictorActive) throw new IllegalStateException("corrector without predictor");
            double corrected = (target - torque) / data.conversionTime();
            torque = oldTorque + 0.5 * dt * (predictorDerivative + corrected);
            predictorActive = false;
        }
        if (!Double.isFinite(torque)) throw new IllegalStateException("GEWTARU1 state is not finite");
    }

    public double powerCoefficient(double pitchDegrees, double tipSpeedRatio) {
        double boundedPitch = clamp(pitchDegrees, data.pitchMinimum(), data.pitchMaximum());
        double boundedLambda = clamp(tipSpeedRatio, data.lambdaMinimum(), data.lambdaMaximum());
        double value = 0.0;
        for (int i = CP.length - 1; i >= 0; i--) {
            double row = 0.0;
            for (int j = CP[i].length - 1; j >= 0; j--) row = row * boundedLambda + CP[i][j];
            value = value * boundedPitch + row;
        }
        return Math.max(MIN_CP, value);
    }

    public double aerodynamicPower(double turbineSpeed, double windSpeed,
            double pitchDegrees) {
        if (turbineRatedMw <= 0.0) throw new IllegalStateException("GEWTARU1 is not initialized");
        if (!finite(turbineSpeed, windSpeed, pitchDegrees) || windSpeed <= 0.0) {
            throw new IllegalArgumentException("invalid GEWTARU1 algebraic input");
        }
        lambda = tipSpeedRatio(turbineSpeed, windSpeed);
        cp = powerCoefficient(pitchDegrees, lambda);
        double watts = 0.5 * data.airDensity() * Math.PI * data.bladeRadius()
                * data.bladeRadius() * windSpeed * windSpeed * windSpeed * cp;
        return adjustment * watts / (1.0e6 * turbineRatedMw);
    }

    private double unadjustedPower(double turbineSpeed, double windSpeed,
            double pitchDegrees) {
        double saved = adjustment;
        adjustment = 1.0;
        double result = aerodynamicPower(turbineSpeed, windSpeed, pitchDegrees);
        adjustment = saved;
        return result;
    }

    private double initialWindVelocity(double initialPower, double turbineSpeed) {
        double tipSpeed = bladeTipSpeed(turbineSpeed);
        double low = tipSpeed / data.lambdaMaximum();
        double high = data.lambdaMinimum() <= EPS ? Math.max(40.0, low) : tipSpeed / data.lambdaMinimum();
        double best = low;
        double bestError = Double.POSITIVE_INFINITY;
        for (int index = 0; index <= 2000; index++) {
            double candidate = low + (high - low) * index / 2000.0;
            double error = Math.abs(unadjustedPower(turbineSpeed, candidate, initialPitch)
                    - initialPower);
            if (error < bestError) { bestError = error; best = candidate; }
        }
        return best;
    }

    private double tipSpeedRatio(double turbineSpeed, double windSpeed) {
        return clamp(bladeTipSpeed(turbineSpeed) / windSpeed,
                data.lambdaMinimum(), data.lambdaMaximum());
    }

    private double bladeTipSpeed(double turbineSpeed) {
        return 2.0 * Math.PI * data.bladeRadius() * data.synchronousRpm()
                * turbineSpeed / (60.0 * data.gearboxRatio());
    }

    public void setWindVelocity(double value) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException("wind speed must be positive");
        windVelocity = value;
    }
    public void setPitch(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("pitch must be finite");
        pitch = clamp(value, data.pitchMinimum(), data.pitchMaximum());
    }
    public Gewtaru1Data getData() { return data; }
    public double getAdjustment() { return adjustment; }
    public double getInitialPitch() { return initialPitch; }
    public double getWindVelocity() { return windVelocity; }
    public double getLambda() { return lambda; }
    public double getPowerCoefficient() { return cp; }
    public double getTorque() { return torque; }
    public double getMechanicalPower(double turbineSpeed) { return torque * turbineSpeed; }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Conversion smoothing lag", torque);
        states.put("Tip speed ratio", lambda);
        states.put("Power coefficient", cp);
        states.put("Wind velocity", windVelocity);
        states.put("Pitch", pitch);
        return Map.copyOf(states);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
