package org.interpss.dstab.renewable;

import java.util.ArrayList;
import java.util.List;

/**
 * WTGIBFFRA's one input ICON and 45 CONs, on machine MVA base.
 * Native six-ICON runtime allocation does not add five input fields.
 */
public record WtgibffraData(int frequencyBus, double deadband, List<Band> bands,
        double frequencyFilterTime, double cooldownTime) {
    public record Band(double power, double boost, double deficit, double riseTime,
            double peakTime, double fallTime, double recoveryTime) {
        public Band {
            for (double value : new double[]{power, boost, deficit, riseTime, peakTime, fallTime, recoveryTime})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("WTGIBFFRA band values must be finite");
        }

        /** WECC energy balance, with nonnegative physical duration. */
        public double energyBalancedRecoveryTime() {
            if (boost <= 0 || deficit <= 0 || riseTime <= 0 || peakTime < 0 || fallTime <= 0)
                throw new IllegalStateException("WTGIBFFRA band has no valid energy-balanced response");
            double duration = boost / deficit * (riseTime + 2 * peakTime + fallTime) - fallTime;
            if (!Double.isFinite(duration)) throw new IllegalArgumentException("WTGIBFFRA recovery calculation exceeds finite range");
            return Math.max(0, duration);
        }

        public double effectiveRecoveryTime() {
            return Math.max(recoveryTime, energyBalancedRecoveryTime());
        }
    }

    public WtgibffraData {
        bands = List.copyOf(bands);
        if (bands.size() != 6) throw new IllegalArgumentException("WTGIBFFRA requires six power bands");
        if (!Double.isFinite(deadband) || !Double.isFinite(frequencyFilterTime) || !Double.isFinite(cooldownTime)
                || deadband < 0 || frequencyFilterTime < 0)
            throw new IllegalArgumentException("WTGIBFFRA requires finite parameters and nonnegative deadband/filter time");
    }

    /** The native contract deactivates invalid ordering or entirely unavailable parameter columns. */
    public boolean enabled() {
        for (int i = 1; i < 6; i++) if (bands.get(i).power() <= bands.get(i - 1).power()) return false;
        return bands.stream().anyMatch(b -> b.boost() > 0)
                && bands.stream().anyMatch(b -> b.deficit() > 0)
                && bands.stream().anyMatch(b -> b.riseTime() > 0)
                && bands.stream().anyMatch(b -> b.fallTime() > 0);
    }

    /** Highest power threshold not above the incident positive generator power; no interpolation. */
    public int selectBand(double power) {
        if (!Double.isFinite(power)) throw new IllegalArgumentException("WTGIBFFRA power must be finite");
        if (!enabled() || power <= 0) return -1;
        for (int i = 5; i >= 0; i--) if (power >= bands.get(i).power()) return i;
        return -1;
    }

    public static WtgibffraData fromNativeParameters(int frequencyBus, double[] constants) {
        if (constants.length != 45) throw new IllegalArgumentException("WTGIBFFRA requires exactly 45 CONs");
        var bands = new ArrayList<Band>();
        for (int i = 0; i < 6; i++) bands.add(new Band(constants[1+i], constants[7+i], constants[13+i],
                constants[19+i], constants[25+i], constants[31+i], constants[37+i]));
        return new WtgibffraData(frequencyBus, constants[0], bands, constants[43], constants[44]);
    }

    public double[] toNativeConstants() {
        double[] values = new double[45]; values[0] = deadband;
        for (int i = 0; i < 6; i++) {
            Band b = bands.get(i);
            values[1+i] = b.power(); values[7+i] = b.boost(); values[13+i] = b.deficit();
            values[19+i] = b.riseTime(); values[25+i] = b.peakTime();
            values[31+i] = b.fallTime(); values[37+i] = b.recoveryTime();
        }
        values[43] = frequencyFilterTime; values[44] = cooldownTime;
        return values;
    }
}
