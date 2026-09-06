package org.interpss.dstab.control.exc.psse.esdc2a;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** PSS/E ESDC2A with voltage-dependent regulator limits. */
@AnController(input="mach.vt", output="this.fieldIntegrator.y",
        refPoint="this.leadLag.u-pss.vs+this.transducer.y+this.washout.y",
        display={})
public class Esdc2aExciter extends AnnotateExciter {
    private final Esdc2aData data;
    private final boolean voltageDependentLimits;
    public double one = 1.0;
    public double spdmlt;

    public double tr;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"},
            y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    public double tc, tb;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NoLimit", "this.one", "this.tc", "this.tb"},
            y0="this.regulator.u0")
    public FilterControlBlock leadLag;

    public double ka, ta, vrmaxVt, vrminVt;
    @AnFunctionField(input={"mach.vt"})
    public ICMLFunction regulatorLimitScale = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            return voltageDependentLimits ? values[0] : 1.0;
        }
    };

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.ka", "this.ta",
                    "this.vrmaxVt*this.regulatorLimitScale.y",
                    "this.vrminVt*this.regulatorLimitScale.y"},
            y0="this.fieldIntegrator.u0+this.ke*this.fieldIntegrator.y"
                    + "+this.saturation.y*this.fieldIntegrator.y")
    public DelayControlBlock regulator;

    public double te, integratorGain, ke;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.regulator.y-this.ke*this.fieldIntegrator.y"
                    + "-this.saturation.y*this.fieldIntegrator.y",
            parameter={"type.NoLimit", "this.integratorGain"}, y0="mach.efd")
    public IntegrationControlBlock fieldIntegrator;

    public double e1, se1, e2, se2;
    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction saturation = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            double efd = values[0];
            if (e1 <= 0.0 || e2 <= 0.0 || se1 <= 0.0 || se2 <= 0.0) return 0.0;
            double ratio = Math.sqrt(e2 * se2 / (e1 * se1));
            if (!Double.isFinite(ratio) || Math.abs(1.0 - ratio) < 1.0e-12) return 0.0;
            double a = (e2 - e1 * ratio) / (1.0 - ratio);
            if (efd <= a || efd <= 0.0) return 0.0;
            double denominator = (e1 - a) * (e1 - a);
            if (denominator <= 0.0) return 0.0;
            double b = e1 * se1 / denominator;
            return b * (efd - a) * (efd - a) / efd;
        }
    };

    public double kf, tf, washoutGain;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.fieldIntegrator.y",
            parameter={"type.NoLimit", "this.washoutGain", "this.tf"}, feedback=true)
    public WashoutControlBlock washout;

    public Esdc2aExciter(String id, Esdc2aData data, Machine machine) {
        this(id, "ESDC2A", data, machine, true);
    }

    protected Esdc2aExciter(String id, String name, Esdc2aData data,
            Machine machine, boolean voltageDependentLimits) {
        super(id, name, "PSS/E");
        this.data = data;
        this.voltageDependentLimits = voltageDependentLimits;
        this._data = data;
        setMachine(machine);
    }

    public Esdc2aData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        tr=data.getTr(); ka=data.getKa(); ta=data.getTa(); tc=data.getTc(); tb=data.getTb();
        vrmaxVt=data.getVrmax() == 0.0 ? 999.0 : data.getVrmax();
        vrminVt=data.getVrmin(); ke=data.getKe(); te=data.getTe();
        kf=data.getKf(); tf=data.getTf(); spdmlt=data.getSpdmlt();
        e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2(); se2=data.getSe2();
        if (te <= 0.0 || tf <= 0.0) return false;
        integratorGain=1.0/te;
        washoutGain=kf/tf;
        return super.initStates(bus, machine);
    }

    @Override
    public double getOutput(Machine machine) {
        double efd = super.getOutput(machine);
        return spdmlt != 0.0 ? efd * machine.getSpeed() : efd;
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
