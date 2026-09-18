package org.interpss.dstab.control.pss.psse.ieeest;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** PSS/E IEEEST stabilizer following the PowerWorld/WECC block ordering. */
@AnController(input="mach.speed", output="this.outputGate.y", refPoint="0.0", display={})
public class IeeestStabilizer extends AnnotateStabilizer {
    private final IeeestData data;

    public double speedRef, peRef, pmRef, vtRef;
    public double speedGain, peGain, pmGain, voltageGain;
    public double a1, a2, a3, a4, a5, a6;
    public double t1, t2, t3, t4, t5, t6, ks, washoutGain;
    public double one = 1.0, lsmax, lsmin, vcu, vcl;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.speedGain*mach.speed-this.speedGain*this.speedRef"
                    + "+this.peGain*mach.pe-this.peGain*this.peRef"
                    + "+this.pmGain*mach.pm-this.pmGain*this.pmRef"
                    + "+this.voltageGain*mach.vt-this.voltageGain*this.vtRef",
            parameter={"this.one", "this.a1", "this.a2"},
            y0="this.secondOrderLeadLag.u0", initOrderNumber=1)
    public SecondOrderLagBlock secondOrderLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.secondOrderLag.y",
            parameter={"this.a3", "this.a4", "this.a5", "this.a6"},
            y0="this.leadLag1.u0", initOrderNumber=2)
    public SecondOrderLeadLagBlock secondOrderLeadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.secondOrderLeadLag.y",
            parameter={"type.NoLimit", "this.one", "this.t1", "this.t2"},
            y0="this.leadLag2.u0", initOrderNumber=3)
    public FilterControlBlock leadLag1;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag1.y",
            parameter={"type.NoLimit", "this.one", "this.t3", "this.t4"},
            y0="this.washout.u0", initOrderNumber=4)
    public FilterControlBlock leadLag2;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.ks*this.leadLag2.y",
            parameter={"type.NoLimit", "this.washoutGain", "this.t6"},
            y0="this.outputGate.u0", initOrderNumber=5)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.washout.y",
            y0="pss.vs", initOrderNumber=6)
    public ICMLStaticBlock outputGate = new GainBlock() {
        @Override
        public boolean initStateY0(double y0) {
            super.k = 1.0;
            return super.initStateY0(y0);
        }

        @Override
        public double getY() {
            double limited = Math.max(lsmin, Math.min(lsmax, super.getY()));
            double vt = getMachine().getDStabBus().getVoltageMag();
            boolean below = vcl != 0.0 && vt <= vcl;
            boolean above = vcu != 0.0 && vt >= vcu;
            return below || above ? 0.0 : limited;
        }
    };

    public IeeestStabilizer(String id, IeeestData data, Machine machine) {
        super(id, "IEEEST", "PSS/E");
        this.data = data;
        setMachine(machine);
    }

    public IeeestData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        speedRef = machine.getSpeed();
        peRef = machine.getPe();
        pmRef = machine.getPm();
        vtRef = bus.getVoltageMag();
        speedGain = data.mode() == 1 ? 1.0 : 0.0;
        peGain = data.mode() == 3 ? 1.0 : 0.0;
        pmGain = data.mode() == 4 ? 1.0 : 0.0;
        voltageGain = data.mode() == 5 ? 1.0 : 0.0;
        a1=data.a1(); a2=data.a2(); a3=data.a3(); a4=data.a4();
        a5=data.a5(); a6=data.a6();
        t1=data.t1(); t2=data.t2(); t3=data.t3(); t4=data.t4();
        t5=data.t5(); t6=data.t6(); ks=data.ks();
        // CML Washout is K*T6*s/(1+T6*s); IEEEST specifies T5*s/(1+T6*s).
        washoutGain = t6 > 0.0 ? t5 / t6 : 0.0;
        lsmax=data.lsmax(); lsmin=data.lsmin(); vcu=data.vcu(); vcl=data.vcl();
        return super.initStates(bus, machine);
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
