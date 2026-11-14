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
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;

/** PSS/E IEEEST stabilizer following the PowerWorld/WECC block ordering. */
@AnController(input="mach.speed", output="this.outputGate.y", refPoint="0.0", display={})
public class IeeestStabilizer extends AnnotateStabilizer implements StaggeredEulerParticipant,
        org.interpss.dstab.control.util.AcceptedVoltageMeasurement {
    @Override public java.util.function.DoubleSupplier bindCompiledParameter(String name)
            throws com.interpss.common.exp.InterpssException {
        if (getClass() != IeeestStabilizer.class) return super.bindCompiledParameter(name);
        return switch (name) {
            case "speedGain" -> () -> speedGain;
            case "speedRef" -> () -> speedRef;
            case "peGain" -> () -> peGain;
            case "peRef" -> () -> peRef;
            case "pmGain" -> () -> pmGain;
            case "pmRef" -> () -> pmRef;
            case "ks" -> () -> ks;
            case "one" -> () -> one;
            default -> bindPublicDoubleParameter(name);
        };
    }

    @Override protected com.interpss.dstab.controller.cml.runtime.CmlCompiledStage compileStageEvaluator() {
        if((!preparedDirectStage&&!Boolean.getBoolean("ipss.experiment.directIeeestStage")) || getClass()!=IeeestStabilizer.class)return null;
        try{return new IeeestDirectStage(this);}catch(IllegalArgumentException unsupported){return null;}
    }
    private boolean preparedDirectStage;
    /** Select typed stages for a solver-qualified standard graph before compilation. */
    public void setPreparedDirectStage(boolean enabled){preparedDirectStage=enabled;}
    @Override public void invalidateRuntimePlan(){preparedDirectStage=false;super.invalidateRuntimePlan();}
    private final IeeestData data;
    private double constantInputBias;
    private boolean initializingBias;
    private BaseDStabBus<?, ?> measurementBus;
    private final IeeestVoltageDerivative voltageDerivative = new IeeestVoltageDerivative();
    private StaggeredBlocks staggeredBlocks;
    private record StaggeredBlocks(SecondOrderLagBlock first, SecondOrderLeadLagBlock second,
            CMLControlBlock1stOrderAdapter lead1, CMLControlBlock1stOrderAdapter lead2,
            CMLControlBlock1stOrderAdapter terminal) {}

    public double speedRef, peRef, pmRef, vtRef, freqRef;
    public double speedGain, peGain, pmGain, voltageGain, frequencyGain;
    public double a1, a2, a3, a4, a5, a6;
    public double t1, t2, t3, t4, t5, t6, ks, washoutGain;
    public double one = 1.0, lsmax, lsmin, vcu, vcl;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.speedGain*mach.speed-this.speedGain*this.speedRef"
                    + "+this.busMeasurement.y"
                    + "+this.pmGain*mach.pm-this.pmGain*this.pmRef",
            parameter={"this.one", "this.a1", "this.a2"},
            y0="this.secondOrderLeadLag.u0", initOrderNumber=1)
    public SecondOrderLagBlock secondOrderLag;

    // The selected machine or bus measurement is algebraic and sampled at each
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

            if (data.mode() == 3 || data.mode() == 4) {
                return peGain * electricalPower(getMachine()) - peGain * peRef;
            }
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

    // IEEEST mode 3 measures terminal MW. The native mode-4 acceleration
    // uses air-gap power, the round-rotor swing coordinate at nominal frequency.
    private double electricalPower(Machine machine) {
        if (data.mode() == 3 && machine instanceof IeeeVcRoundRotorMachine roundRotor) {
            return roundRotor.getTerminalActivePower();
        }
        return machine.getPe();
    }


    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        staggeredBlocks = null;
        if (data.mode() < 1 || data.mode() > 6 || data.remoteBus() < 0
                || (data.remoteBus() != 0 && data.mode() != 2 && data.mode() != 5 && data.mode() != 6)) return false;
        measurementBus = data.remoteBus() == 0 ? bus
                : (BaseDStabBus<?, ?>) bus.getNetwork().getBus("Bus" + data.remoteBus());
        if (measurementBus == null || !measurementBus.isActive()) return false;
        speedRef = machine.getSpeed();
        peRef = electricalPower(machine);
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
        boolean initialized;
        try { initialized = super.initStates(bus, machine); }
        finally { initializingBias=false; }
        if (initialized) {
            var blocks = getNamedStateBlocks();
            staggeredBlocks = new StaggeredBlocks((SecondOrderLagBlock) blocks.get("secondOrderLag"),
                    (SecondOrderLeadLagBlock) blocks.get("secondOrderLeadLag"),
                    (CMLControlBlock1stOrderAdapter) blocks.get("leadLag1"),
                    (CMLControlBlock1stOrderAdapter) blocks.get("leadLag2"),
                    (CMLControlBlock1stOrderAdapter) blocks.get("washout"));
        }
        return initialized;
    }

    @Override public int staggeredStateCount() { return 7; }

    private StaggeredBlocks staggered(double[] values, int offset) {
        java.util.Objects.checkFromIndexSize(offset, staggeredStateCount(), values.length);
        if (staggeredBlocks == null) throw new IllegalStateException("Initialize IEEEST before accessing integration states");
        return staggeredBlocks;
    }

    @Override public void readStaggeredState(double[] values, int offset) {
        var b = staggered(values, offset);
        values[offset] = b.first.getStateX(); values[offset+1] = b.first.getRate();
        values[offset+2] = b.second.getFirstState(); values[offset+3] = b.second.getSecondState();
        values[offset+4] = b.lead1.getStateX(); values[offset+5] = b.lead2.getStateX();
        values[offset+6] = b.terminal.getStateX();
    }

    @Override public void evaluateStaggered(double stepSec, double[] rates, int offset) {
        var b = staggered(rates, offset);
        if (!Double.isFinite(stepSec) || stepSec < 0) throw new IllegalArgumentException("Nonnegative finite evaluation step required");
        if (!super.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, getMachine(), 0))
            throw new IllegalStateException("Cannot evaluate IEEEST integration boundary");
        rates[offset] = b.first.getOutputSlope(); rates[offset+1] = b.first.getRateSlope();
        rates[offset+2] = b.second.getFirstStateSlope(); rates[offset+3] = b.second.getSecondStateSlope();
        rates[offset+4] = b.lead1.getDX_dt(); rates[offset+5] = b.lead2.getDX_dt();
        rates[offset+6] = b.terminal.getDX_dt();
    }

    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        var b = staggered(values, offset);
        for (int i=0; i<7; i++) if (!Double.isFinite(values[offset+i]))
            throw new IllegalArgumentException("IEEEST integration state must be finite");
        b.first.setStateX(values[offset]); b.first.setRate(values[offset+1]);
        b.second.setStateX(values[offset+2]); b.second.setSecondState(values[offset+3]);
        b.lead1.setStateX(values[offset+4]); b.lead2.setStateX(values[offset+5]);
        b.terminal.setStateX(values[offset+6]);
    }

    @Override public boolean averageStaggeredRatesAtDiscontinuity() { return data.mode() != 6; }

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
