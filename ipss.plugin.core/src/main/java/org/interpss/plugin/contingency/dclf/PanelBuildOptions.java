package org.interpss.plugin.contingency.dclf;

/**
 * Options for building a DCLF contingency transfer panel.
 */
public final class PanelBuildOptions {
    public static final double DEFAULT_DENOMINATOR_TOLERANCE = 1.0e-8;

    private final double denominatorTolerance;

    private PanelBuildOptions(Builder builder) {
        this.denominatorTolerance = builder.denominatorTolerance;
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

    public static final class Builder {
        private double denominatorTolerance = DEFAULT_DENOMINATOR_TOLERANCE;

        private Builder() {
        }

        public Builder denominatorTolerance(double denominatorTolerance) {
            this.denominatorTolerance = denominatorTolerance;
            return this;
        }

        public PanelBuildOptions build() {
            return new PanelBuildOptions(this);
        }
    }
}
