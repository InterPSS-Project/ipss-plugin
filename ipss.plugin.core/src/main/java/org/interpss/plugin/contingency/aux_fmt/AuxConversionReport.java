package org.interpss.plugin.contingency.aux_fmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuxConversionReport {
    private int contingencyCount;
    private int ctgElementCount;
    private int emittedBranchRecordCount;
    private int unsupportedElementCount;
    private int skippedElementCount;
    private final List<String> warnings = new ArrayList<>();

    public int getContingencyCount() {
        return contingencyCount;
    }

    public void setContingencyCount(int contingencyCount) {
        this.contingencyCount = contingencyCount;
    }

    public int getCtgElementCount() {
        return ctgElementCount;
    }

    public void setCtgElementCount(int ctgElementCount) {
        this.ctgElementCount = ctgElementCount;
    }

    public int getEmittedBranchRecordCount() {
        return emittedBranchRecordCount;
    }

    public void incrementEmittedBranchRecordCount() {
        emittedBranchRecordCount++;
    }

    public int getUnsupportedElementCount() {
        return unsupportedElementCount;
    }

    public void incrementUnsupportedElementCount() {
        unsupportedElementCount++;
    }

    public int getSkippedElementCount() {
        return skippedElementCount;
    }

    public void incrementSkippedElementCount() {
        skippedElementCount++;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
