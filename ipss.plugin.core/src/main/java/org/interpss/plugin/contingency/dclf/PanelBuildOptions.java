package org.interpss.plugin.contingency.dclf;

/**
 * Options for building a DCLF contingency transfer panel.
 */
final class PanelBuildOptions {
    public static final double DEFAULT_DENOMINATOR_TOLERANCE = 1.0e-8;

    private final double denominatorTolerance;
    private final int monitorChunkSize;

    private PanelBuildOptions(Builder builder) {
        this.denominatorTolerance = builder.denominatorTolerance;
        this.monitorChunkSize = builder.monitorChunkSize;
    }

    public static PanelBuildOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getDenominatorTolerance() {
        return denominatorTolerance;
    }

    public int getMonitorChunkSize() {
        return monitorChunkSize;
    }

    public static final class Builder {
        private double denominatorTolerance = DEFAULT_DENOMINATOR_TOLERANCE;
        private int monitorChunkSize = 0;

        private Builder() {
        }

        public Builder denominatorTolerance(double denominatorTolerance) {
            this.denominatorTolerance = denominatorTolerance;
            return this;
        }

        public Builder monitorChunkSize(int monitorChunkSize) {
            if (monitorChunkSize < 0) {
                throw new IllegalArgumentException("monitorChunkSize cannot be negative");
            }
            this.monitorChunkSize = monitorChunkSize;
            return this;
        }

        public PanelBuildOptions build() {
            return new PanelBuildOptions(this);
        }
    }
}
