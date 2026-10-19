package org.interpss.dstab.renewable;

/** WECC WTAR_A simplified aerodynamic power model. Angles are in degrees. */
public final class Wtara1Model {
    private final Wtara1Data data;
    private double initialPower;
    private double pitch;
    private double mechanicalPower;

    public Wtara1Model(Wtara1Data data) {
        this.data = data;
    }

    public void initialize(double power) {
        requireFinite(power, "initial mechanical power");
        initialPower = power;
        pitch = data.theta0();
        mechanicalPower = power;
    }

    public void step(double pitchCommand) {
        requireFinite(pitchCommand, "pitch input");
        pitch = pitchCommand;
        mechanicalPower = initialPower - data.ka() * pitch * (pitch - data.theta0());
    }

    public Wtara1Data getData() { return data; }
    /** Initialized physical mechanical-power input; not an integrated state. */
    public double getInitialPower() { return initialPower; }
    public double getPitch() { return pitch; }
    public double getMechanicalPower() { return mechanicalPower; }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("WTARA1 " + quantity + " must be finite");
    }
}
