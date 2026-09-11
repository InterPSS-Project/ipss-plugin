package org.interpss.dstab.relay;

import java.util.Map;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Native PSS/E LVS3BL five-stage undervoltage load relay. */
public final class Lvs3blRelayModel extends AbstractStagedLoadSheddingRelayModel {
    private static final double TIME_EPS = 1.0e-12;
    private final AclfBranch[] transferBranches;
    private final double[] transferBreakerTimes;
    private final double[] transferTimers = new double[2];
    private final boolean[] transferStarted = new boolean[2];
    private final boolean[] transferOperated = new boolean[2];

    public Lvs3blRelayModel(BaseDStabBus<?, ?> loadBus, String loadId,
            StagedLoadSheddingRelayData data, AclfBranch firstBranch, double firstTime,
            AclfBranch secondBranch, double secondTime) {
        super("LVS3BL", loadBus, loadId, data);
        transferBranches = new AclfBranch[] { firstBranch, secondBranch };
        transferBreakerTimes = new double[] { firstTime, secondTime };
    }

    @Override protected double monitoredValue() { return loadBus().getVoltageMag(); }
    @Override protected boolean violates(LoadSheddingStage stage, double value) {
        return value < stage.threshold();
    }
    @Override protected void stagePickedUp(int stage) {
        for (int i = 0; i < transferStarted.length; i++) {
            if (transferBranches[i] != null && !transferOperated[i]) transferStarted[i] = true;
        }
        advanceTransferTrips(0.0);
    }
    @Override protected boolean advanceTransferTrips(double dt) {
        boolean changed = false;
        for (int i = 0; i < transferStarted.length; i++) {
            if (!transferStarted[i] || transferOperated[i]) continue;
            transferTimers[i] += dt;
            if (transferTimers[i] + TIME_EPS >= transferBreakerTimes[i]) {
                transferBranches[i].setStatus(false);
                transferOperated[i] = true;
                changed = true;
            }
        }
        if (changed) {
            @SuppressWarnings("unchecked")
            BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) loadBus().getNetwork();
            network.formYMatrix4DStab();
            reapplyAfterNetworkRebuild(network);
            network.setYMatrixDirty(true);
        }
        return true;
    }
    @Override protected void resetTransferTrips() {
        java.util.Arrays.fill(transferTimers, 0.0);
        java.util.Arrays.fill(transferStarted, false);
        java.util.Arrays.fill(transferOperated, false);
    }
    @Override protected void addTransferNamedStates(Map<String, Double> states) {
        states.put("Transfer trip 1 timer", transferTimers[0]);
        states.put("Transfer trip 2 timer", transferTimers[1]);
    }
    public boolean isTransferOperated(int index) { return transferOperated[index]; }
}
