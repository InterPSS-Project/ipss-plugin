package org.interpss.plugin.contingency.dclf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

/**
 * Immutable study definition used by cached DCLF branch-contingency tests.
 */
final class DclfContingencyStudySpec {
    private final AclfNetwork aclfNetwork;
    private final List<DclfBranchOutage> contingencies;
    private final Set<String> monitoredBranchIds;
    private final DclfMethod method;
    private final double overloadThreshold;
    private final double shiftThresholdMw;

    private DclfContingencyStudySpec(Builder builder) {
        this.aclfNetwork = builder.aclfNetwork;
        this.contingencies = Collections.unmodifiableList(new ArrayList<>(builder.contingencies));
        this.monitoredBranchIds = builder.monitoredBranchIds == null
                ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(builder.monitoredBranchIds));
        this.method = builder.method;
        this.overloadThreshold = builder.overloadThreshold;
        this.shiftThresholdMw = builder.shiftThresholdMw;
    }

    public static Builder builder(AclfNetwork aclfNetwork) {
        return new Builder(aclfNetwork);
    }

    public AclfNetwork getAclfNetwork() {
        return aclfNetwork;
    }

    public List<DclfBranchOutage> getContingencies() {
        return contingencies;
    }

    public Set<String> getMonitoredBranchIds() {
        return monitoredBranchIds;
    }

    public DclfMethod getMethod() {
        return method;
    }

    public double getOverloadThreshold() {
        return overloadThreshold;
    }

    public double getShiftThresholdMw() {
        return shiftThresholdMw;
    }

    public static final class Builder {
        private final AclfNetwork aclfNetwork;
        private List<DclfBranchOutage> contingencies = Collections.emptyList();
        private Set<String> monitoredBranchIds;
        private DclfMethod method = DclfMethod.STD;
        private double overloadThreshold = 100.0;
        private double shiftThresholdMw = 1.0;

        private Builder(AclfNetwork aclfNetwork) {
            if (aclfNetwork == null) {
                throw new IllegalArgumentException("aclfNetwork cannot be null");
            }
            this.aclfNetwork = aclfNetwork;
        }

        public Builder contingencies(List<DclfBranchOutage> contingencies) {
            if (contingencies == null) {
                throw new IllegalArgumentException("contingencies cannot be null");
            }
            this.contingencies = contingencies;
            return this;
        }

        public Builder monitoredBranchIds(Set<String> monitoredBranchIds) {
            this.monitoredBranchIds = monitoredBranchIds;
            return this;
        }

        public Builder method(DclfMethod method) {
            if (method == null) {
                throw new IllegalArgumentException("method cannot be null");
            }
            this.method = method;
            return this;
        }

        public Builder overloadThreshold(double overloadThreshold) {
            this.overloadThreshold = overloadThreshold;
            return this;
        }

        public Builder shiftThresholdMw(double shiftThresholdMw) {
            this.shiftThresholdMw = shiftThresholdMw;
            return this;
        }

        public DclfContingencyStudySpec build() {
            return new DclfContingencyStudySpec(this);
        }
    }
}
