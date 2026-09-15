package org.interpss.dstab.control.exc.psse.sexs;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BaseFieldAnWrapper;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** Six-parameter simplified excitation system. */
@AnController(input="mach.vt", output="this.regulator.y",
        refPoint="this.leadLag.u0+mach.vt-pss.vs-this.vuel-this.voel", display={})
public class SexsExciter extends AnnotateExciter {
    private final SexsData data;

    public double one = 1.0;
    public double ta;
    public double tb;
    public double k;
    public double te;
    public double emin;
    public double emax;
    public double initialEfd;
    public double vuel;
    public double voel;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint-mach.vt+pss.vs+this.vuel+this.voel",
            parameter={"type.NoLimit", "this.one", "this.ta", "this.tb"},
            y0="this.regulator.u0", initOrderNumber=1)
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.k", "this.te", "this.emax", "this.emin"},
            y0="this.initialEfd", initOrderNumber=2)
    public DelayControlBlock regulator;

    public SexsExciter(String id, SexsData data, Machine machine) {
        super(id, "SEXS", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public SexsData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        tb = data.getTb();
        ta = data.getTaOverTb() * tb;
        k = data.getK();
        te = data.getTe();
        initialEfd = machine.getEfd();
        emin = Math.min(data.getEmin(), initialEfd);
        emax = Math.max(data.getEmax(), initialEfd);
        return super.initStates(bus, machine);
    }

    public void setVuel(double value) { vuel = value; }
    public void setVoel(double value) { voel = value; }

    public double getFirstIntegratorState() { return runtimeBlock("leadLag").getStateX(); }
    public double getSecondIntegratorState() { return runtimeBlock("regulator").getStateX(); }

    /** Published states in model-library order. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("First integrator", getFirstIntegratorState());
        states.put("Second integrator", getSecondIntegratorState());
        return Collections.unmodifiableMap(states);
    }

    private ICMLControlBlock runtimeBlock(String name) {
        for (BaseFieldAnWrapper<?> wrapper : getFieldWrapperList()) {
            if (wrapper.getFieldName().equals(name)
                    && wrapper.getField() instanceof ICMLControlBlock block) return block;
        }
        throw new IllegalStateException("SEXS CML block is not initialized: " + name);
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
