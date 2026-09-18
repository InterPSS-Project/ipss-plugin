package org.interpss.dstab.control.exc.psse.ieeex2;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** PSS/E IEEEX2 excitation system with the published second feedback lag. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Ieeex2Exciter extends Ieeex1Exciter {
    private double tf2;

    public Ieeex2Exciter(String id, Machine machine) {
        super(id, "IEEEX2", machine);
    }

    public double getTf2() {
        return tf2;
    }

    public void setTf2(double value) {
        tf2 = value;
    }

    @Override
    protected double feedbackInput(double internalField, double regulatorOutput) {
        return regulatorOutput;
    }

    @Override
    protected double feedbackLagTimeConstant() {
        return tf2;
    }

    @Override
    protected boolean feedbackLagAfterWashout() {
        return true;
    }

    /** Published PSS/E state order K through K+5. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Sensed VT", getSensedVoltage());
        states.put("Lead lag", getLeadLagState());
        states.put("Regulator output, VR", getRegulatorOutput());
        states.put("Exciter output, EFD", getExciterFieldState());
        states.put("First feedback integrator", getRateFeedbackIntegratorState());
        states.put("Second feedback integrator", getRateFeedbackLagOutput());
        return Map.copyOf(states);
    }

    @Override public AnController getAnController() {
        return getClass().getAnnotation(AnController.class);
    }
    @Override public Field getField(String name) throws Exception {
        return getClass().getField(name);
    }
    @Override public Object getFieldObject(Field field) throws Exception {
        return field.get(this);
    }
}
