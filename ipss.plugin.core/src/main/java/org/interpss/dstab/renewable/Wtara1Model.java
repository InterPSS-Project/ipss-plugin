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
        initialPower = power;
        pitch = data.theta0();
        mechanicalPower = power;
    }

    public void step(double pitchCommand) {
        pitch = pitchCommand;
        mechanicalPower = initialPower - data.ka() * pitch * (pitch - data.theta0());
    }

    public Wtara1Data getData() { return data; }
    public double getPitch() { return pitch; }
    public double getMechanicalPower() { return mechanicalPower; }
}
