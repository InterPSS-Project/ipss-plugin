package org.interpss.dstab.relay;

import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.device.DynamicDevice;

/** Native PSS/E LDS3BL five-stage underfrequency load relay. */
public final class Lds3blRelayModel extends AbstractStagedLoadSheddingRelayModel {
    private static final double TIME_EPS = 1.0e-12;
    private final BaseDStabBus<?, ?> transferBus;
    private final String transferGeneratorId;
    private final double transferBreakerTime;
    private boolean transferStarted;
    private boolean transferOperated;
    private double transferTimer;

    public Lds3blRelayModel(BaseDStabBus<?, ?> loadBus, String loadId,
            StagedLoadSheddingRelayData data, BaseDStabBus<?, ?> transferBus,
            String transferGeneratorId, double transferBreakerTime) {
        super("LDS3BL", loadBus, loadId, data);
        this.transferBus = transferBus;
        this.transferGeneratorId = transferGeneratorId;
        this.transferBreakerTime = transferBreakerTime;
    }

    @Override protected double monitoredValue() {
        return loadBus().getFreq() * loadBus().getNetwork().getFrequency();
    }
    @Override protected boolean violates(LoadSheddingStage stage, double value) {
        return value < stage.threshold();
    }
    @Override protected void stagePickedUp(int stage) {
        if (transferBus != null && !transferOperated) {
            transferStarted = true;
            if (transferBreakerTime <= TIME_EPS) advanceTransferTrips(0.0);
        }
    }
    @Override protected boolean advanceTransferTrips(double dt) {
        if (!transferStarted || transferOperated) return true;
        transferTimer += dt;
        if (transferTimer + TIME_EPS < transferBreakerTime) return true;
        if ("-1".equals(transferGeneratorId)) {
            for (Object generator : transferBus.getContributeGenList()) trip((DStabGen) generator);
        } else {
            DStabGen generator = (DStabGen) transferBus.getContributeGen(transferGeneratorId);
            if (generator == null) return false;
            trip(generator);
        }
        @SuppressWarnings("unchecked")
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) transferBus.getNetwork();
        transferBus.resetSeqEquivLoad();
        network.formYMatrix4DStab();
        reapplyAfterNetworkRebuild(network);
        network.setYMatrixDirty(true);
        transferOperated = true;
        return true;
    }
    private static void trip(DStabGen generator) {
        generator.setStatus(false);
        DynamicDevice dynamic = generator.getDynamicGenDevice();
        if (dynamic != null) dynamic.setStatus(false);
    }
    @Override protected void resetTransferTrips() {
        transferStarted = false;
        transferOperated = false;
        transferTimer = 0.0;
    }
    @Override protected void addTransferNamedStates(Map<String, Double> states) {
        states.put("Transfer trip timer", transferTimer);
    }
    public boolean isTransferOperated() { return transferOperated; }
    public double getTransferTimer() { return transferTimer; }
}
