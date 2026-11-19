package org.interpss.dstab.dynLoad.impl;

/** Scalar discrete tap history; evaluation and recording do not advance it. */
final class CompositeTapControl {
    private final double minimum, maximum, step, controlDelay, tapDelay;
    private double tap, control, time, due = Double.POSITIVE_INFINITY;

    CompositeTapControl(double minimum, double maximum, double step, double controlDelay, double tapDelay) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum <= 0 || maximum < minimum
                || !Double.isFinite(step) || step <= 0 || !Double.isFinite(controlDelay) || controlDelay < 0
                || !Double.isFinite(tapDelay) || tapDelay < 0)
            throw new IllegalArgumentException("Finite positive tap bounds/step and nonnegative delays required");
        this.minimum=minimum; this.maximum=maximum; this.step=step;
        this.controlDelay=controlDelay; this.tapDelay=tapDelay;
    }
    void initialize(double initialTap) {
        if (!Double.isFinite(initialTap) || initialTap <= 0) throw new IllegalArgumentException("Invalid initial tap");
        tap=initialTap; control=0; time=0; due=Double.POSITIVE_INFINITY;
    }
    void copyFrom(CompositeTapControl other) {
        tap=other.tap; control=other.control; time=other.time; due=other.due;
    }
    void advance(double dt, double voltage, double vmin, double vmax) {
        if (!Double.isFinite(dt) || dt < 0 || !Double.isFinite(voltage) || voltage < 0
                || !Double.isFinite(vmin) || !Double.isFinite(vmax) || vmin > vmax)
            throw new IllegalArgumentException("Invalid tap-control interval or voltage band");
        if (dt == 0) return;
        time+=dt;
        int direction=voltage < vmin ? 1 : voltage > vmax ? -1 : 0;
        double increment=controlDelay == 0 ? 2 : dt/controlDelay;
        if (direction == 0) {
            control=Math.copySign(Math.max(0, Math.abs(control)-increment), control);
            // A pending action cannot move the tap after voltage returns inside the band.
            due=Double.POSITIVE_INFINITY;
            return;
        }
        if (direction > 0 && tap >= maximum || direction < 0 && tap <= minimum) {
            control=0; due=Double.POSITIVE_INFINITY; return;
        }
        control=Math.max(-1, Math.min(1, control+direction*increment));
        if (!Double.isFinite(due) && Math.abs(control) >= 1-1e-12) due=time+tapDelay;
        if (time+1e-12 >= due) {
            tap=Math.max(minimum, Math.min(maximum, tap+direction*step));
            due=Double.POSITIVE_INFINITY;
        }
    }
    double tap() { return tap; }
    double control() { return control; }
    double time() { return time; }
    double due() { return due; }
}
