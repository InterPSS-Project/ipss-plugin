package org.interpss.dstab.control.exc.psse.scrx;

import java.lang.reflect.Field;
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
import com.interpss.dstab.mach.MachineIfdBase;

/** PSS/E/PowerWorld SCRX bus-fed or solid-fed excitation system. */
@AnController(input="mach.vt", output="this.regulator.y",
        refPoint="this.leadLag.u0+mach.vt-pss.vs-this.vuel+this.voel", display={})
public class ScrxExciter extends AnnotateExciter {
    private final ScrxData data;

    public double one = 1.0;
    public double ta, tb;
    public double k, te, efdmax, efdmin;
    public double initialInternalOutput;
    public double vuel;
    public double voel;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint-mach.vt+pss.vs+this.vuel-this.voel",
            parameter={"type.NoLimit", "this.one", "this.ta", "this.tb"},
            y0="this.regulator.u0", initOrderNumber=1)
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.k", "this.te", "this.efdmax", "this.efdmin"},
            y0="this.initialInternalOutput", initOrderNumber=2)
    public DelayControlBlock regulator;

    public ScrxExciter(String id, ScrxData data, Machine machine) {
        super(id, "SCRX", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public ScrxData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        tb = data.getTb();
        ta = data.getTaOverTb() * tb;
        k = Math.max(1.0, data.getK());
        te = Math.max(0.0, data.getTe());
        double source = sourceMultiplier(machine);
        if (source <= 1.0e-9) return false;
        initialInternalOutput = machine.getEfd() / source;
        double rawMax = Math.max(data.getEfdmax(), data.getEfdmin());
        double rawMin = Math.min(data.getEfdmax(), data.getEfdmin());
        efdmax = Math.max(rawMax, initialInternalOutput);
        efdmin = Math.min(rawMin, initialInternalOutput);
        return super.initStates(bus, machine);
    }

    @Override
    public double getOutput(Machine machine) {
        double output = super.getOutput(machine) * sourceMultiplier(machine);
        double ifd = machine.calculateIfd(MachineIfdBase.EXCITER);
        return applyFieldBoundary(output, data.getRcOverRfd(), ifd);
    }

    public void setVuel(double value) { vuel = value; }
    public void setVoel(double value) { voel = value; }

    public double getFirstIntegratorState() { return runtimeBlock("leadLag").getStateX(); }
    public double getSecondIntegratorState() { return runtimeBlock("regulator").getStateX(); }

    /** Published PSS/E SCRX states in model-library order and semantics. */
    @Override
    public Map<String, Double> getNamedStates() {
        return Map.of("First integrator", getFirstIntegratorState(),
                "Second integrator", getSecondIntegratorState());
    }

    private ICMLControlBlock runtimeBlock(String name) {
        for (BaseFieldAnWrapper<?> wrapper : getFieldWrapperList()) {
            if (wrapper.getFieldName().equals(name)
                    && wrapper.getField() instanceof ICMLControlBlock block) return block;
        }
        throw new IllegalStateException("SCRX CML block is not initialized: " + name);
    }

    private double sourceMultiplier(Machine machine) {
        return data.getCswitch() == 0 ? machine.getDStabBus().getVoltageMag() : 1.0;
    }

    public static double applyFieldBoundary(double requestedEfd, double rcOverRfd, double ifd) {
        return rcOverRfd > 0.0 && Double.isFinite(ifd)
                ? Math.max(requestedEfd, -rcOverRfd * ifd) : requestedEfd;
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
