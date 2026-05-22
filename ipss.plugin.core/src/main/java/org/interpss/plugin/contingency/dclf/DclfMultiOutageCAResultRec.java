package org.interpss.plugin.contingency.dclf;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.contingency.dclf.DclfMultiOutage;

/**
 * Contingency-analysis result record for a multi-branch DCLF outage.
 */
public final class DclfMultiOutageCAResultRec {
    public final DclfMultiOutage contingency;
    public final AclfBranch aclfBranch;
    public final double preFlowMW;
    public final double shiftedFlowMW;

    public DclfMultiOutageCAResultRec(
            DclfMultiOutage contingency,
            AclfBranch aclfBranch,
            double preFlowMW,
            double shiftedFlowMW) {
        if (contingency == null) {
            throw new IllegalArgumentException("contingency cannot be null");
        }
        if (aclfBranch == null) {
            throw new IllegalArgumentException("aclfBranch cannot be null");
        }
        this.contingency = contingency;
        this.aclfBranch = aclfBranch;
        this.preFlowMW = preFlowMW;
        this.shiftedFlowMW = shiftedFlowMW;
    }

    public double getPostFlowMW() {
        return preFlowMW + shiftedFlowMW;
    }

    public double calLoadingPercent() {
        double ratingMva = aclfBranch.getRatingMvaB();
        return ratingMva > 0.0 ? 100.0 * Math.abs(getPostFlowMW()) / ratingMva : 0.0;
    }

    @Override
    public String toString() {
        return "ContigencyId:" + contingency.getId()
                + ", monitoredBranchId:" + aclfBranch.getId()
                + ", preFlowMW:" + preFlowMW
                + ", shiftedFlowMW:" + shiftedFlowMW;
    }
}
