package org.interpss.plugin.aclf.config;

public enum ZeroZBranchProcessingMode {
    PROCESS("ZBR processing"),
    CONSOLIDATE("ZBR consolidation"),
    NONE("Do nothing");

    private final String displayText;

    ZeroZBranchProcessingMode(String displayText) {
        this.displayText = displayText;
    }

    public String displayText() {
        return displayText;
    }
}
