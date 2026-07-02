package org.interpss.plugin.contingency.aux_fmt.bean;

public class AuxContingency {
    private final String name;
    private final int lineNumber;

    public AuxContingency(String name, int lineNumber) {
        this.name = name;
        this.lineNumber = lineNumber;
    }

    public String getName() {
        return name;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
