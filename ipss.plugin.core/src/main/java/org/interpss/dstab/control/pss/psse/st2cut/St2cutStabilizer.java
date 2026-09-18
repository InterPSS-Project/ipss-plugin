package org.interpss.dstab.control.pss.psse.st2cut;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** PSS/E ST2CUT stabilizer in the published six-state block ordering. */
@AnController(input="mach.speed", output="this.outputGate.y", refPoint="0.0", display={})
public class St2cutStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private final St2cutData data;

    public double input1Signal;
    public double input2Signal;
    public double k1, k2, t1, t2;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input1Signal",
            parameter={"this.k1", "this.t1"},
            y0="this.washout.u0", initOrderNumber=1)
    public St2cutTransducerBlock input1Transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input2Signal",
            parameter={"this.k2", "this.t2"},
            y0="this.washout.u0-this.input1Transducer.y", initOrderNumber=1)
    public St2cutTransducerBlock input2Transducer;

    public double t3, t4;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.input1Transducer.y+this.input2Transducer.y",
            parameter={"this.t3", "this.t4"},
            y0="this.leadLag1.u0", initOrderNumber=2)
    public St2cutWashoutBlock washout;

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

    public double t9, t10;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag2.y",
            parameter={"type.NoLimit", "this.one", "this.t9", "this.t10"},
            y0="this.outputGate.u0", initOrderNumber=5)
    public FilterControlBlock leadLag3;

    public double vsmax, vsmin, vcu, vcl, initialTerminalVoltage;
    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.leadLag3.y",
            y0="pss.vs", initOrderNumber=6)
    public ICMLStaticBlock outputGate = new GainBlock() {
        @Override
        public boolean initStateY0(double y0) {
            super.k = 1.0;
            return super.initStateY0(y0);
        }

        @Override
        public double getY() {
            double limited = Math.max(vsmin, Math.min(vsmax, super.getY()));
            double voltage = getMachine().getDStabBus().getVoltageMag();
            boolean below = vcl != 0.0 && voltage < initialTerminalVoltage + vcl;
            boolean above = vcu != 0.0 && voltage > initialTerminalVoltage + vcu;
            return below || above ? 0.0 : limited;
        }
    };

    private BaseDStabBus<?, ?> input1Bus;
    private BaseDStabBus<?, ?> input2Bus;
    private double input1Reference;
    private double input2Reference;
    private double input1PreviousVoltage;
    private double input2PreviousVoltage;

    public St2cutStabilizer(String id, St2cutData data, Machine machine) {
        super(id, "ST2CUT", "PSS/E");
        this.data = data;
        setMachine(machine);
    }

    public St2cutData getData() { return data; }

    public void setInputSignalBuses(BaseDStabBus<?, ?> first, BaseDStabBus<?, ?> second) {
        input1Bus = first;
        input2Bus = second;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0) {
            throw new IllegalArgumentException("ST2CUT integration step must be finite and non-negative");
        }
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        if (input1Bus == null) input1Bus = bus;
        if (input2Bus == null) input2Bus = bus;
        input1PreviousVoltage = input1Bus.getVoltageMag();
        input2PreviousVoltage = input2Bus.getVoltageMag();
        input1Reference = selectedInput(data.mode1(), input1Bus, machine);
        input2Reference = selectedInput(data.mode2(), input2Bus, machine);
        input1Signal = deviationInput(data.mode1(), input1Reference,
                input1Bus, input1PreviousVoltage, machine, 0.0);
        input2Signal = deviationInput(data.mode2(), input2Reference,
                input2Bus, input2PreviousVoltage, machine, 0.0);

        k1=data.k1(); k2=data.k2(); t1=data.t1(); t2=data.t2();
        t3=data.t3(); t4=data.t4();
        t5=data.t5(); t6=data.t6(); t7=data.t7(); t8=data.t8();
        t9=data.t9(); t10=data.t10(); vsmax=data.vsmax(); vsmin=data.vsmin();
        vcu=data.vcu(); vcl=data.vcl(); initialTerminalVoltage=bus.getVoltageMag();
        return super.initStates(bus, machine);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        input1Signal = deviationInput(data.mode1(), input1Reference,
                input1Bus, input1PreviousVoltage, machine, dt);
        input2Signal = deviationInput(data.mode2(), input2Reference,
                input2Bus, input2PreviousVoltage, machine, dt);
        boolean result = super.nextStep(dt, method, machine, flag);
        if (flag != 0) {
            input1PreviousVoltage = input1Bus.getVoltageMag();
            input2PreviousVoltage = input2Bus.getVoltageMag();
        }
        return result;
    }

    private static double deviationInput(int code, double reference,
            BaseDStabBus<?, ?> bus, double previousVoltage, Machine machine, double dt) {
        if (code == 0) return 0.0;
        if (code == 6) {
            return dt > 0.0 ? (bus.getVoltageMag() - previousVoltage) / dt : 0.0;
        }
        return selectedInput(code, bus, machine) - reference;
    }

    private static double selectedInput(int code, BaseDStabBus<?, ?> bus, Machine machine) {
        return switch (code) {
            case 0, 6 -> 0.0;
            case 1 -> machine.getSpeed();
            case 2 -> bus.getFreq();
            case 3 -> machine.getPe();
            case 4 -> machine.getPm() - machine.getPe();
            case 5 -> bus.getVoltageMag();
            default -> throw new IllegalArgumentException("Unsupported ST2CUT input code: " + code);
        };
    }

    /** Stable user-facing names for the six published dynamic memories. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, ICMLControlBlock> blocks = getNamedStateBlocks();
        if (blocks.isEmpty()) return Map.of();
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("First signal transducer", blocks.get("input1Transducer").getStateX());
        states.put("Second signal transducer", blocks.get("input2Transducer").getStateX());
        states.put("Washout", blocks.get("washout").getStateX());
        states.put("First lead-lag", blocks.get("leadLag1").getStateX());
        states.put("Second lead-lag", blocks.get("leadLag2").getStateX());
        states.put("Third lead-lag", blocks.get("leadLag3").getStateX());
        return Map.copyOf(states);
    }

    /**
     * PSS/E STATE-array coordinates, kept separate from the canonical CML
     * integrator memories returned by {@link #getNamedStates()}.
     */
    public Map<String, Double> getPsseStateCoordinates() {
        Map<String, Double> states = getNamedStates();
        if (states.isEmpty()) return states;
        double firstOffset = nativeInputOffset(data.mode1(), k1, input1Reference);
        double secondOffset = nativeInputOffset(data.mode2(), k2, input2Reference);
        Map<String, Double> nativeStates = new LinkedHashMap<>(states);
        nativeStates.put("First signal transducer",
                states.get("First signal transducer") + firstOffset);
        nativeStates.put("Second signal transducer",
                states.get("Second signal transducer") + secondOffset);
        nativeStates.put("Washout", Math.abs(t3) <= 1.0e-12
                ? states.get("Washout")
                : t4 * states.get("Washout") + t3 * (firstOffset + secondOffset));
        nativeStates.put("First lead-lag", t6 * states.get("First lead-lag"));
        nativeStates.put("Second lead-lag", t8 * states.get("Second lead-lag"));
        nativeStates.put("Third lead-lag", t10 * states.get("Third lead-lag"));
        return Map.copyOf(nativeStates);
    }

    private static double nativeInputOffset(int code, double gain, double reference) {
        return code == 3 || code == 4 || code == 5 ? gain * reference : 0.0;
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
