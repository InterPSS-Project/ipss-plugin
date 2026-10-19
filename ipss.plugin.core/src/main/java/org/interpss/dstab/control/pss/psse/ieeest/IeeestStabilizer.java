package org.interpss.dstab.control.pss.psse.ieeest;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** PSS/E IEEEST stabilizer following the PowerWorld/WECC block ordering. */
@AnController(input="mach.speed", output="this.outputGate.y", refPoint="0.0", display={})
public class IeeestStabilizer extends AnnotateStabilizer {
    private final IeeestData data;
    private double constantInputBias;
    private boolean initializingBias;
    private BaseDStabBus<?, ?> measurementBus;
    private final IeeestVoltageDerivative voltageDerivative = new IeeestVoltageDerivative();

    public double speedRef, peRef, pmRef, vtRef, freqRef;
    public double speedGain, peGain, pmGain, voltageGain, frequencyGain;
    public double a1, a2, a3, a4, a5, a6;
    public double t1, t2, t3, t4, t5, t6, ks, washoutGain;
    public double one = 1.0, lsmax, lsmin, vcu, vcl;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.speedGain*mach.speed-this.speedGain*this.speedRef"
                    + "+this.peGain*mach.pe-this.peGain*this.peRef"
                    + "+this.pmGain*mach.pm-this.pmGain*this.pmRef"
                    + "+this.busMeasurement.y",
            parameter={"this.one", "this.a1", "this.a2"},
            y0="this.secondOrderLeadLag.u0", initOrderNumber=1)
    public SecondOrderLagBlock secondOrderLag;

    // A measurement is algebraic: it must sample the selected bus at each
    // integration stage, without introducing another filter or stored state.
    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.one",
            y0="0.0", initOrderNumber=-1)
    public ICMLStaticBlock busMeasurement = new GainBlock() {
        @Override public boolean initStateY0(double y0) {
            super.k = 1.0;
            return super.initStateY0(y0);
        }
        @Override public double getY() {
            if (data.mode() == 2) return measurementBus.getFreq() - freqRef;
            if (data.mode() == 5) return measurementBus.getVoltageMag() - vtRef;
            if (data.mode() == 6) return voltageDerivative.getDerivative();
            return 0.0;
        }
    };

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
            parameter={"this.washoutGain", "this.t6", "this.t5"},
            y0="this.outputGate.u0", initOrderNumber=5)
    public IeeestTerminalBlock washout;

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
            double unlimited = super.getY() + (initializingBias ? 0.0 : constantInputBias);
            double limited = Math.max(lsmin, Math.min(lsmax, unlimited));
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
        if (data.mode() < 1 || data.mode() > 6 || data.remoteBus() < 0
                || (data.remoteBus() != 0 && data.mode() != 2 && data.mode() != 5 && data.mode() != 6)) return false;
        measurementBus = data.remoteBus() == 0 ? bus
                : (BaseDStabBus<?, ?>) bus.getNetwork().getBus("Bus" + data.remoteBus());
        if (measurementBus == null || !measurementBus.isActive()) return false;
        speedRef = machine.getSpeed();
        peRef = machine.getPe();
        pmRef = machine.getPm();
        vtRef = measurementBus.getVoltageMag();
        freqRef = measurementBus.getFreq();
        voltageDerivative.initialize(vtRef);
        frequencyGain = data.mode() == 2 ? 1.0 : 0.0;
        speedGain = data.mode() == 1 ? 1.0 : 0.0;
        // Accelerating power is Pm-Pe on the machine base, not Pm alone.
        peGain = data.mode() == 3 ? 1.0 : data.mode() == 4 ? -1.0 : 0.0;
        pmGain = data.mode() == 4 ? 1.0 : 0.0;
        voltageGain = data.mode() == 5 ? 1.0 : 0.0;
        a1=data.a1(); a2=data.a2(); a3=data.a3(); a4=data.a4();
        a5=data.a5(); a6=data.a6();
        t1=data.t1(); t2=data.t2(); t3=data.t3(); t4=data.t4();
        t5=data.t5(); t6=data.t6(); ks=data.ks();
        // CML Washout is K*T6*s/(1+T6*s); IEEEST specifies T5*s/(1+T6*s).
        washoutGain = t6 > 0.0 ? t5 / t6 : 0.0;
        lsmax=data.lsmax(); lsmin=data.lsmin(); vcu=data.vcu(); vcl=data.vcl();
        // The filter chain stores deviations from this physical input. A
        // washout removes its DC term; the T5=0 lag must retain it instead.
        double physicalInput=speedGain*(speedRef-1.0)+peGain*peRef+pmGain*pmRef
                +voltageGain*vtRef+frequencyGain*(freqRef-1.0);
        constantInputBias=t5==0.0 && t6>0.0 ? ks*physicalInput : 0.0;
        initializingBias=true;
        try { return super.initStates(bus, machine); }
        finally { initializingBias=false; }
    }

    /** Called only after a settled zero-time network event; no filter advance. */
    public void synchronizeVoltageDerivativeMemory() {
        if (data.mode() == 6) voltageDerivative.synchronizeVoltage(measurementBus.getVoltageMag());
    }

    /** Called after the corrector network converges, before endpoint monitoring. */
    public void acceptVoltageDerivativeEndpoint(double time, double step) {
        if (data.mode() == 6) voltageDerivative.accept(time, measurementBus.getVoltageMag(), step);
    }

    public double getVoltageDerivativeMemory() { return voltageDerivative.getVoltage(); }
    public double getVoltageDerivative() { return voltageDerivative.getDerivative(); }

    /** Physical terminal-filter signal before the static output limits and voltage cutout. */
    public double getUnclippedOutput() {
        if(getNamedStateBlocks().isEmpty())throw new IllegalStateException("IEEEST blocks are not initialized");
        try {return getFieldVaule("this.washout.y")+(initializingBias?0.0:constantInputBias);}
        catch(Exception failure){throw new IllegalStateException("Cannot observe the IEEEST terminal signal",failure);}
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
