package org.interpss.dstab.control.exc.psse.esac5a;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5/PSS/E ESAC5A simplified rotating AC excitation system. */
@AnController(input="mach.vt", output="this.fieldIntegrator.y",
        refPoint="this.regulator.u0+this.transducer.y+this.washout.y-pss.vs",
        display={})
public class Esac5aExciter extends AnnotateExciter {
    private final Esac5aData data;
    public double one = 1.0;
    public double tr, ka, ta, vrmax, vrmin, ke, te, integratorGain;
    public double kf, tf1, tf2, tf3, spdmlt;
    public double e1, se1, e2, se2;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"},
            y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
            y0="this.fieldIntegrator.u0+this.ke*this.fieldIntegrator.y"
                    + "+this.saturation.y*this.fieldIntegrator.y")
    public DelayControlBlock regulator;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.regulator.y",
            parameter={"type.NoLimit", "this.one", "this.tf3", "this.tf2"},
            feedback=true)
    public FilterControlBlock feedbackLeadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.feedbackLeadLag.y",
            parameter={"type.NoLimit", "this.kf", "this.tf1"}, feedback=true)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.regulator.y-this.ke*this.fieldIntegrator.y"
                    + "-this.saturation.y*this.fieldIntegrator.y",
            parameter={"type.NoLimit", "this.integratorGain"}, y0="mach.efd")
    public IntegrationControlBlock fieldIntegrator;

    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction saturation = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            double ve = values[0];
            if (e1 <= 0.0 || e2 <= 0.0 || se1 <= 0.0 || se2 <= 0.0) return 0.0;
            double ratio = Math.sqrt(e2 * se2 / (e1 * se1));
            if (!Double.isFinite(ratio) || Math.abs(1.0 - ratio) < 1.0e-12) return 0.0;
            double a = (e2 - e1 * ratio) / (1.0 - ratio);
            if (ve <= a || ve <= 0.0) return 0.0;
            double denominator = (e1 - a) * (e1 - a);
            if (denominator <= 0.0) return 0.0;
            return e1 * se1 * (ve - a) * (ve - a) / (denominator * ve);
        }
    };

    public Esac5aExciter(String id, Esac5aData data, Machine machine) {
        super(id, "ESAC5A", "PSS/E");
        this.data = data;
        this._data = data;
        setMachine(machine);
    }

    public Esac5aData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        tr=data.getTr(); ka=data.getKa(); ta=data.getTa(); ke=data.getKe(); te=data.getTe();
        kf=data.getKf(); tf1=data.getTf1(); tf2=data.getTf2(); tf3=data.getTf3();
        spdmlt=data.getSpdmlt(); e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2(); se2=data.getSe2();
        if (te <= 0.0 || tf1 < 0.0 || tf2 < 0.0 || tf3 < 0.0) return false;
        integratorGain = 1.0 / te;
        double initialVr = machine.getEfd() * (ke + saturation.eval(new double[] {machine.getEfd()}));
        vrmax = Math.max(Math.max(data.getVrmax(), data.getVrmin()), initialVr);
        vrmin = Math.min(Math.min(data.getVrmax(), data.getVrmin()), initialVr);
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
