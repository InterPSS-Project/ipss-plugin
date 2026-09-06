package org.interpss.dstab.control.exc.psse.exdc2a;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exdc2.Exdc2Exciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/**
 * PSLF/PowerWorld EXDC2A excitation system.
 *
 * <p>The model extends EXDC2 with the additional {@code Tf2} lag in the
 * regulator-output rate-feedback path.  The resulting feedback transfer is
 * {@code s*Kf / ((1+s*Tf1)*(1+s*Tf2))}.  Setting {@code Tf2=0} bypasses the
 * extra lag.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Exdc2aExciter extends Exdc2Exciter {
    private double tf2;

    public Exdc2aExciter(String id, Machine machine) {
        super(id, "EXDC2A", machine);
    }

    public double getTf2() { return tf2; }
    public void setTf2(double value) { tf2 = value; }

    @Override
    protected double feedbackInput(double internalField, double regulatorOutput) {
        return regulatorOutput;
    }

    @Override
    protected double feedbackLagTimeConstant() {
        return tf2;
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
