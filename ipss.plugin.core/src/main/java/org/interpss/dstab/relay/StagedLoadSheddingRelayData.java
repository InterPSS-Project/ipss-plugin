package org.interpss.dstab.relay;

import java.util.List;

/** Shared exact five-stage data carried by PSS/E LDS3BL and LVS3BL. */
public record StagedLoadSheddingRelayData(List<LoadSheddingStage> stages,
        boolean shedShunt) {
    public StagedLoadSheddingRelayData {
        if (stages == null || stages.size() != 5 || stages.stream().anyMatch(s -> s == null)) {
            throw new IllegalArgumentException("exactly five load-shedding stages are required");
        }
        stages = List.copyOf(stages);
    }
}
