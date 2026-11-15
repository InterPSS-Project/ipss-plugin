package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

/** Primitive accepted input-path history, separate from trial differential storage. */
final class Pss2bAcceptedPathHistory {
    private double first, second, previousFirst, previousSecond, time;
    private boolean stepPending;

    void initialize(double first, double second) {
        requireFinite(first); requireFinite(second);
        this.first = previousFirst = first;
        this.second = previousSecond = second;
        time = 0.; stepPending = false;
    }

    void copyFrom(Pss2bAcceptedPathHistory source) {
        first = source.first;
        second = source.second;
        previousFirst = source.previousFirst;
        previousSecond = source.previousSecond;
        time = source.time;
        stepPending = source.stepPending;
    }

    void beginStep(double step) {
        requireFinite(step);
        if (step < 0.) throw new IllegalArgumentException("Nonnegative input-path interval required");
        if (step > 0.) stepPending = true;
    }

    boolean needsAcceptance(double endpoint, double step) {
        requireFinite(endpoint); requireFinite(step);
        if (step < 0. || endpoint < time)
            throw new IllegalArgumentException("Input-path endpoint cannot move backwards");
        return step > 0. && endpoint > time;
    }

    void accept(double endpoint, double step, double first, double second) {
        if (!needsAcceptance(endpoint, step)) return;
        requireFinite(first); requireFinite(second);
        previousFirst = this.first; previousSecond = this.second;
        this.first = first; this.second = second;
        time = endpoint; stepPending = false;
    }

    /** A settled event changes the next interval's input, not the published sample. */
    void synchronize(double first, double second) {
        requireFinite(first); requireFinite(second);
        this.first = first; this.second = second;
    }

    double firstOutput() { return stepPending ? first : previousFirst; }
    double secondOutput() { return stepPending ? second : previousSecond; }
    double firstAccepted() { return first; }
    double secondAccepted() { return second; }
    double firstPrevious() { return previousFirst; }
    double secondPrevious() { return previousSecond; }
    double acceptedTime() { return time; }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite input-path history required");
    }
}
