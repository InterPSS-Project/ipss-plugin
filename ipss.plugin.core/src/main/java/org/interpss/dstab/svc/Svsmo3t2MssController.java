package org.interpss.dstab.svc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered SVSMO3T2 mechanically switched-shunt state machine. Only an accepted
 * endpoint advances demand, breaker or discharge histories. No network or
 * electrical-controller equations are evaluated here.
 */
public final class Svsmo3t2MssController {
    private final Svsmo3t2Data data;
    private final double[] susceptance = new double[8], openedAt = new double[8];
    private final boolean[] active = new boolean[8];
    private boolean initialized, enabled, sampled;
    private double clock, upperStart, lowerStart, breakerStart;
    private int pendingDirection;
    private long attempts, completed;

    public Svsmo3t2MssController(Svsmo3t2Data data) { this.data = java.util.Objects.requireNonNull(data); }

    /** Arrays describe the individually resolved source slots, not aggregate bus B. */
    public void initialize(double[] nominalSusceptance, boolean[] inService) {
        if (nominalSusceptance.length != 8 || inService.length != 8)
            throw new IllegalArgumentException("SVSMO3T2 requires eight physical MSS slots");
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            if (!Double.isFinite(nominalSusceptance[i]))
                throw new IllegalArgumentException("Finite individual MSS susceptance required");
            if (data.mssDevices().get(i).busNumber() == 0) {
                if (nominalSusceptance[i] != 0 || inService[i])
                    throw new IllegalArgumentException("An unassigned MSS slot cannot own a physical device");
            } else if (data.mssSwitchingFlag() == 1) {
                if (nominalSusceptance[i] == 0)
                    throw new IllegalArgumentException("Configured MSS must be capacitive or inductive");
                any = true;
            }
        }
        System.arraycopy(nominalSusceptance, 0, susceptance, 0, 8);
        System.arraycopy(inService, 0, active, 0, 8);
        for (int i = 0; i < 8; i++) openedAt[i] = active[i] ? -1 : -data.capacitorDischargeTime();
        clock = 0;
        upperStart = lowerStart = breakerStart = -1;
        pendingDirection = 0;
        sampled = false;
        attempts = completed = 0;
        enabled = data.mssSwitchingFlag() == 1 && any;
        initialized = true;
    }

    /**
     * Return the single completed slot, or -1. The caller applies its new physical
     * status before another endpoint. Repeated endpoint time is idempotent.
     * A breaker request is latched even if demand subsequently returns in band.
     */
    public int advance(double time, double current) {
        requireInitialized();
        if (!Double.isFinite(time) || !Double.isFinite(current) || time < 0 || time < clock)
            throw new IllegalArgumentException("Finite monotone MSS endpoint time/current required");
        if (sampled && time == clock) return -1;
        clock = time;
        sampled = true;
        if (!enabled) return -1;
        int direction = current > data.mssUpperCurrent() ? 1 : current < data.mssLowerCurrent() ? -1 : 0;
        int candidate = select(direction, false);
        if (direction == 1 && candidate >= 0) {
            if (upperStart < 0) upperStart = time;
            lowerStart = -1;
        } else if (direction == -1 && candidate >= 0) {
            if (lowerStart < 0) lowerStart = time;
            upperStart = -1;
        } else upperStart = lowerStart = -1;

        if (pendingDirection != 0) {
            if (time >= breakerStart + data.mssBreakerTime()) return complete();
            return -1;
        }
        double start = direction == 1 ? upperStart : direction == -1 ? lowerStart : -1;
        if (start < 0 || time < start + data.mssSwitchingTime()) return -1;
        pendingDirection = direction;
        breakerStart = start + data.mssSwitchingTime();
        if (time >= breakerStart + data.mssBreakerTime()) return complete();
        return -1;
    }

    /** Reusable event transaction storage; no per-endpoint allocation. */
    static final class Checkpoint {
        final double[] opened = new double[8];
        final boolean[] status = new boolean[8];
        boolean sampled;
        double clock, upper, lower, breaker;
        int direction;
        long attempts, completed;
    }

    void save(Checkpoint saved) {
        System.arraycopy(openedAt, 0, saved.opened, 0, 8);
        System.arraycopy(active, 0, saved.status, 0, 8);
        saved.sampled = sampled; saved.clock = clock;
        saved.upper = upperStart; saved.lower = lowerStart; saved.breaker = breakerStart;
        saved.direction = pendingDirection; saved.attempts = attempts; saved.completed = completed;
    }

    void restore(Checkpoint saved) {
        System.arraycopy(saved.opened, 0, openedAt, 0, 8);
        System.arraycopy(saved.status, 0, active, 0, 8);
        sampled = saved.sampled; clock = saved.clock;
        upperStart = saved.upper; lowerStart = saved.lower; breakerStart = saved.breaker;
        pendingDirection = saved.direction; attempts = saved.attempts; completed = saved.completed;
    }

    private int select(int direction, boolean readyToOperate) {
        if (direction == 0) return -1;
        // Unlike other plant-controller families, source slot order wins over
        // removal-versus-insertion preference. Cooldown is checked at operation.
        for (int i = 0; i < 8; i++) {
            double b = susceptance[i];
            if (b == 0) continue;
            if (readyToOperate && b > 0 && !active[i]
                    && clock - openedAt[i] < data.capacitorDischargeTime()) continue;
            if (direction > 0 ? b > 0 && !active[i] || b < 0 && active[i]
                    : b > 0 && active[i] || b < 0 && !active[i]) return i;
        }
        return -1;
    }

    private int complete() {
        int slot = select(pendingDirection, true);
        boolean retainLowerDemand = slot >= 0 && susceptance[slot] < 0 && pendingDirection < 0;
        attempts++;
        int changed = -1;
        // If no bank is ready, finish an unsuccessful attempt and require a
        // fresh demand delay. A cooling capacitor is never made eligible early.
        if (slot >= 0) {
            active[slot] = !active[slot];
            if (susceptance[slot] > 0) openedAt[slot] = active[slot] ? -1 : clock;
            completed++;
            changed = slot;
        }
        pendingDirection = 0;
        breakerStart = upperStart = -1;
        // The exact source reactor-insertion branch retains lower pickup. A
        // subsequent ready reactor can therefore operate at the next endpoint;
        // no-candidate or in-band evaluation clears this history normally.
        if (!retainLowerDemand) lowerStart = -1;
        return changed;
    }

    public boolean isEnabled() { requireInitialized(); return enabled; }
    public boolean isInService(int slot) { requireInitialized(); return active[slot]; }
    public double time() { requireInitialized(); return clock; }
    public int pendingDirection() { requireInitialized(); return pendingDirection; }
    public long attemptCount() { return attempts; }
    public long completionCount() { return completed; }
    public double upperDemandStart() { return upperStart; }
    public double lowerDemandStart() { return lowerStart; }
    public double breakerStart() { return breakerStart; }
    public double openedAt(int slot) { return openedAt[slot]; }

    public Map<String, Double> getNamedStates() {
        requireInitialized();
        if (!enabled) return Map.of();
        var result = new LinkedHashMap<String, Double>();
        result.put("time", clock);
        result.put("upperDemandStart", upperStart);
        result.put("lowerDemandStart", lowerStart);
        result.put("breakerStart", breakerStart);
        result.put("pendingDirection", (double) pendingDirection);
        result.put("attempts", (double) attempts);
        result.put("completions", (double) completed);
        for (int i = 0; i < 8; i++) if (susceptance[i] != 0) {
            result.put("slot." + (i + 1) + ".inService", active[i] ? 1. : 0.);
            result.put("slot." + (i + 1) + ".openedAt", openedAt[i]);
        }
        return Collections.unmodifiableMap(result);
    }

    private void requireInitialized() {
        if (!initialized) throw new IllegalStateException("Initialize physical MSS slots first");
    }
}
