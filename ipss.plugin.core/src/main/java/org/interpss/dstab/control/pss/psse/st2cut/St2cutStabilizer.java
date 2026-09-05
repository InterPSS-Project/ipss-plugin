package org.interpss.dstab.control.pss.psse.st2cut;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** PSS/E ST2CUT stabilizer using the WECC/ANDES block ordering. */
@AnController(input="mach.speed", output="this.leadLag3.y", refPoint="0.0", display={})
public class St2cutStabilizer extends AnnotateStabilizer {
    private final St2cutData data;
    public double speedRef, peRef, accelRef;
    public double k1Speed, k1Power, k1Accel, k2Speed, k2Power, k2Accel;
    public double t1, t2;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.speed-this.speedRef",
            parameter={"type.NoLimit", "this.k1Speed", "this.t1"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input1Speed;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.pe-this.peRef",
            parameter={"type.NoLimit", "this.k1Power", "this.t1"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input1Power;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="mach.pm-mach.pe-this.accelRef",
            parameter={"type.NoLimit", "this.k1Accel", "this.t1"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input1Accel;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.speed-this.speedRef",
            parameter={"type.NoLimit", "this.k2Speed", "this.t2"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input2Speed;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.pe-this.peRef",
            parameter={"type.NoLimit", "this.k2Power", "this.t2"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input2Power;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="mach.pm-mach.pe-this.accelRef",
            parameter={"type.NoLimit", "this.k2Accel", "this.t2"}, y0="0.0", initOrderNumber=1)
    public DelayControlBlock input2Accel;

    public double t3, t4, washoutK;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.input1Speed.y+this.input1Power.y+this.input1Accel.y"
                    + "+this.input2Speed.y+this.input2Power.y+this.input2Accel.y",
            parameter={"type.NoLimit", "this.washoutK", "this.t4"},
            y0="this.leadLag1.u0", initOrderNumber=2)
    public WashoutControlBlock washout;

    public double one = 1.0, t5, t6;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.washout.y",
            parameter={"type.NoLimit", "this.one", "this.t5", "this.t6"},
            y0="this.leadLag2.u0", initOrderNumber=3)
    public FilterControlBlock leadLag1;
    public double t7, t8;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag1.y",
            parameter={"type.NoLimit", "this.one", "this.t7", "this.t8"},
            y0="this.leadLag3.u0", initOrderNumber=4)
    public FilterControlBlock leadLag2;
    public double t9, t10, vsmax, vsmin;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag2.y",
            parameter={"type.Limit", "this.one", "this.t9", "this.t10",
                    "this.vsmax", "this.vsmin"}, y0="pss.vs", initOrderNumber=5)
    public FilterControlBlock leadLag3;

    public St2cutStabilizer(String id, St2cutData data, Machine machine) {
        super(id, "ST2CUT", "PSS/E");
        this.data = data;
        setMachine(machine);
    }

    public St2cutData getData() { return data; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        speedRef = machine.getSpeed();
        peRef = machine.getPe();
        accelRef = machine.getPm() - machine.getPe();
        k1Speed = data.mode1() == 1 ? data.k1() : 0.0;
        k1Power = data.mode1() == 3 ? data.k1() : 0.0;
        k1Accel = data.mode1() == 4 ? data.k1() : 0.0;
        k2Speed = data.mode2() == 1 ? data.k2() : 0.0;
        k2Power = data.mode2() == 3 ? data.k2() : 0.0;
        k2Accel = data.mode2() == 4 ? data.k2() : 0.0;
        t1=data.t1(); t2=data.t2(); t3=data.t3(); t4=data.t4();
        // CML's washout parameterization is K*T*s/(1+T*s), while
        // ST2CUT specifies T3*s/(1+T4*s).
        washoutK = t4 > 0.0 ? t3 / t4 : 0.0;
        t5=data.t5(); t6=data.t6(); t7=data.t7(); t8=data.t8();
        t9=data.t9(); t10=data.t10(); vsmax=data.vsmax(); vsmin=data.vsmin();
        return super.initStates(bus, machine);
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
