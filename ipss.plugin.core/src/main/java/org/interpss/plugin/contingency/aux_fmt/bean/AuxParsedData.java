package org.interpss.plugin.contingency.aux_fmt.bean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuxParsedData {
    private final List<AuxContingency> contingencies;
    private final List<AuxCtgElement> ctgElements;

    public AuxParsedData(List<AuxContingency> contingencies, List<AuxCtgElement> ctgElements) {
        this.contingencies = new ArrayList<>(contingencies);
        this.ctgElements = new ArrayList<>(ctgElements);
    }

    public List<AuxContingency> getContingencies() {
        return Collections.unmodifiableList(contingencies);
    }

    public List<AuxCtgElement> getCtgElements() {
        return Collections.unmodifiableList(ctgElements);
    }
}
