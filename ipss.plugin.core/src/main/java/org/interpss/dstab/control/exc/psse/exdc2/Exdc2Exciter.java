package org.interpss.dstab.control.exc.psse.exdc2;

import java.lang.reflect.Field;
import java.util.Map;

import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E EXDC2 / PowerWorld EXDC2_PTI excitation system.
 *
 * <p>EXDC2 shares the DC regulator and field circuit with IEEEX1. Its defining
 * boundary behavior is terminal-voltage-scaled regulator limits and generator
 * speed multiplication at the EFD output.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public class Exdc2Exciter extends Ieeex1Exciter {
    private static final double EPS = 1.0e-12;

    public Exdc2Exciter(String id, Machine machine) {
        this(id, "EXDC2", machine);
    }

    protected Exdc2Exciter(String id, String name, Machine machine) {
        super(id, name, machine);
    }

    @Override
    protected double correctedTr(double value) {
        double minimum = minimumResolvedTimeConstant();
        if (value > 0.0 && value < 0.25 * minimum) return 0.0;
        if (value > 0.25 * minimum && value < 0.5 * minimum) return 0.5 * minimum;
        return value;
    }

    // EXDC2_PTI applies no PowerWorld minimum-time correction to TA or TB.
    @Override protected double correctedTa(double value) { return value; }
    @Override protected double correctedTb(double value) { return value; }

    @Override
    protected double regulatorLimitScale(Machine machine) {
        return Math.max(EPS, machine.getDStabBus().getVoltageMag());
    }

    @Override
    protected double initialInternalField(Machine machine) {
        double speed = machine.getSpeed();
        return Math.abs(speed) > EPS ? machine.getEfd() / speed : machine.getEfd();
    }

    @Override
    protected double machineOutput(double internalField, Machine machine) {
        return internalField * machine.getSpeed();
    }

    /** Published PSS/E EXDC2 states in model-library order and semantics. */
    @Override
    public Map<String, Double> getNamedStates() {
        return Map.of(
                "Sensed VT", getSensedVoltage(),
                "Lead lag output", getLeadLagState(),
                "Regulator output, VR", getRegulatorOutput(),
                "Exciter output, EFD", getExciterFieldState(),
                "Rate feedback integrator", getRateFeedbackIntegratorState());
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
