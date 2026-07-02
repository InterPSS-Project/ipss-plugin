package org.interpss.plugin.contingency.aux_fmt;

public class AuxConversionOptions {
    public enum BusIdMode {
        PREFIX_BUS,
        PRESERVE
    }

    public enum UnsupportedElementPolicy {
        WARN,
        FAIL
    }

    private final BusIdMode busIdMode;
    private final UnsupportedElementPolicy unsupportedElementPolicy;
    private final String defaultCircuitId;

    public AuxConversionOptions(
            BusIdMode busIdMode,
            UnsupportedElementPolicy unsupportedElementPolicy,
            String defaultCircuitId) {
        this.busIdMode = busIdMode;
        this.unsupportedElementPolicy = unsupportedElementPolicy;
        this.defaultCircuitId = defaultCircuitId;
    }

    public static AuxConversionOptions defaultOptions() {
        return new AuxConversionOptions(BusIdMode.PREFIX_BUS, UnsupportedElementPolicy.WARN, "1");
    }

    public BusIdMode getBusIdMode() {
        return busIdMode;
    }

    public UnsupportedElementPolicy getUnsupportedElementPolicy() {
        return unsupportedElementPolicy;
    }

    public String getDefaultCircuitId() {
        return defaultCircuitId;
    }

    public AuxConversionOptions withBusIdMode(BusIdMode mode) {
        return new AuxConversionOptions(mode, unsupportedElementPolicy, defaultCircuitId);
    }

    public AuxConversionOptions withUnsupportedElementPolicy(UnsupportedElementPolicy policy) {
        return new AuxConversionOptions(busIdMode, policy, defaultCircuitId);
    }

    public AuxConversionOptions withDefaultCircuitId(String circuitId) {
        return new AuxConversionOptions(busIdMode, unsupportedElementPolicy, circuitId);
    }
}
