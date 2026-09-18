package org.interpss.dstab.control.gov.wecc.wshygp;

import org.interpss.dstab.control.base.BaseControllerData;

/** Thirty constants of the WSHYGP compatibility governor record. */
public final class WshygpGovernorData extends BaseControllerData {
    private final double[] value = new double[30];

    public double get(int index) { return value[index]; }
    public void set(int index, double input) { value[index] = input; }

    @Override public void setValue(String name, int input) { setValue(name, (double) input); }
    @Override public void setValue(String name, double input) {
        if (name != null && name.matches("c(?:on)?\\d+")) {
            int index = Integer.parseInt(name.replaceAll("\\D", ""));
            if (index >= 0 && index < value.length) value[index] = input;
        }
    }
}
