package org.interpss.dstab.mach;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.mach.impl.Eq1MachineImpl;

/** PSS/E GENTRA transient-level salient-pole generator. */
public final class GentraMachine extends Eq1MachineImpl
        implements ICMLStateProvider, IeeeVoltageCompensatedMachine {
    private final GentraData data;
    private IeeeVcData ieeeVcData;

    public GentraMachine(GentraData data) {
        super();
        this.data = data;
    }

    public GentraData getGentraData() {
        return data;
    }

    @Override
    public IeeeVcData getIeeeVcData() {
        return ieeeVcData;
    }

    @Override
    public void setIeeeVcData(IeeeVcData data) {
        ieeeVcData = data;
    }

    /** Algebraic q-axis transient voltage reported as GENTRA VAR L. */
    public double getEdp() {
        return (getXq() - getXd1()) * getIdq().q;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", getEq1());
        states.put("Speed Deviation", getSpeed() - 1.0);
        states.put("Angle", getAngle());
        return Map.copyOf(states);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        Hashtable<String, Object> states = super.getStates(ref);
        states.putAll(getNamedStates());
        states.put("GENTRA E'd", getEdp());
        return states;
    }
}
