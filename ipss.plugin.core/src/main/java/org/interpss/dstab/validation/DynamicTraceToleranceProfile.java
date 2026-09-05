package org.interpss.dstab.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Named tolerance profile with signal-specific overrides. */
public final class DynamicTraceToleranceProfile {
    private final String name;
    private final DynamicTraceTolerance defaultTolerance;
    private final Map<String, DynamicTraceTolerance> bySignal;

    public DynamicTraceToleranceProfile(String name, DynamicTraceTolerance defaultTolerance,
            Map<String, DynamicTraceTolerance> bySignal) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name is required");
        if (defaultTolerance == null) throw new IllegalArgumentException("Default tolerance is required");
        this.name = name.trim();
        this.defaultTolerance = defaultTolerance;
        this.bySignal = Map.copyOf(bySignal == null ? Map.of() : bySignal);
    }

    public DynamicTraceTolerance forSignal(String signal) {
        return bySignal.getOrDefault(signal, defaultTolerance);
    }

    public String name() {
        return name;
    }

    public static DynamicTraceToleranceProfile engineering() {
        Map<String, DynamicTraceTolerance> tolerances = new LinkedHashMap<>();
        tolerances.put("BUS_VOLTAGE", new DynamicTraceTolerance(1.0e-3, 1.0e-3));
        tolerances.put("SPEED", new DynamicTraceTolerance(5.0e-5, 5.0e-5));
        tolerances.put("ANGLE_DEG", new DynamicTraceTolerance(0.15, 1.0e-3));
        tolerances.put("P", new DynamicTraceTolerance(2.0e-3, 2.0e-3));
        tolerances.put("Q", new DynamicTraceTolerance(2.0e-3, 2.0e-3));
        tolerances.put("IP", new DynamicTraceTolerance(2.0e-3, 2.0e-3));
        tolerances.put("IQ", new DynamicTraceTolerance(2.0e-3, 2.0e-3));
        tolerances.put("EFD", new DynamicTraceTolerance(3.0e-2, 1.0e-2));
        tolerances.put("PM", new DynamicTraceTolerance(3.0e-2, 1.0e-2));
        return new DynamicTraceToleranceProfile("engineering",
                new DynamicTraceTolerance(1.0e-3, 1.0e-3), tolerances);
    }

    public static DynamicTraceToleranceProfile strict(double tolerance) {
        return new DynamicTraceToleranceProfile("strict",
                new DynamicTraceTolerance(tolerance, tolerance), Map.of());
    }
}
