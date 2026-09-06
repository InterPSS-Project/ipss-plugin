package org.interpss.dstab.control.pss.ieee.y2005.pss3b;

import java.lang.reflect.Field;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aWashoutBlock;
import org.interpss.dstab.control.pss.psse.ieeest.SecondOrderLeadLagBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2005 dual-input PSS3B stabilizer. */
@AnController(input="mach.speed", output="this.notch2.y", refPoint="0.0", display={})
public class Ieee2005PSS3BStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private final Ieee2005PSS3BStabilizerData data;

    public double ks1, t1;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input1Signal",
            parameter={"type.NoLimit", "this.ks1", "this.t1"},
            y0="this.input1Washout.u0", initOrderNumber=1)
    public DelayControlBlock input1Transducer;

    public double one = 1.0, tw1;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input1Transducer.y",
            parameter={"this.one", "this.tw1"}, y0="0.0", initOrderNumber=2)
    public Pss2aWashoutBlock input1Washout;

    public double ks2, t2;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input2Signal",
            parameter={"type.NoLimit", "this.ks2", "this.t2"},
            y0="this.input2Washout.u0", initOrderNumber=3)
    public DelayControlBlock input2Transducer;

    public double tw2;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.input2Transducer.y",
            parameter={"this.one", "this.tw2"}, y0="0.0", initOrderNumber=4)
    public Pss2aWashoutBlock input2Washout;

    public double tw3;
    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.input1Washout.y + this.input2Washout.y",
            parameter={"this.one", "this.tw3"}, y0="this.notch1.u0",
            initOrderNumber=5)
    public Pss2aWashoutBlock mainWashout;

    public double a1, a2, a3, a4;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.mainWashout.y",
            parameter={"this.a3", "this.a4", "this.a1", "this.a2"},
            y0="this.notch2.u0", initOrderNumber=6)
    public SecondOrderLeadLagBlock notch1;

    public double a5, a6, a7, a8;
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.notch1.y",
            parameter={"this.a7", "this.a8", "this.a5", "this.a6"},
            y0="pss.vs", initOrderNumber=7)
    public SecondOrderLeadLagBlock notch2;

    public double vstmax, vstmin;
    public double input1Signal, input2Signal;

    private double input1Reference;
    private double input2Reference;
    private double previousVoltage;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;

    public Ieee2005PSS3BStabilizer(String id,
            Ieee2005PSS3BStabilizerData data, Machine machine) {
        this(id, "PSS3B", "IEEE-2005", data, machine);
    }

    protected Ieee2005PSS3BStabilizer(String id, String name, String category,
            Ieee2005PSS3BStabilizerData data, Machine machine) {
        super(id, name, category);
        this.data = data;
        setMachine(machine);
    }

    public Ieee2005PSS3BStabilizerData getData() {
        return data;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "PSS3B integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        ks1 = data.ks1(); t1 = data.t1(); tw1 = data.tw1();
        ks2 = data.ks2(); t2 = data.t2(); tw2 = data.tw2(); tw3 = data.tw3();
        a1 = data.a1(); a2 = data.a2(); a3 = data.a3(); a4 = data.a4();
        a5 = data.a5(); a6 = data.a6(); a7 = data.a7(); a8 = data.a8();
        vstmax = data.vstmax(); vstmin = data.vstmin();
        applyPowerWorldCorrections();

        input1Reference = selectedInput(data.ics1(), bus, machine);
        input2Reference = selectedInput(data.ics2(), bus, machine);
        previousVoltage = bus.getVoltageMag();
        input1Signal = 0.0;
        input2Signal = 0.0;
        return super.initStates(bus, machine);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        BaseDStabBus<?, ?> bus = machine.getDStabBus();
        input1Signal = deviationInput(data.ics1(), input1Reference, bus, machine, dt);
        input2Signal = deviationInput(data.ics2(), input2Reference, bus, machine, dt);
        boolean result = super.nextStep(dt, method, machine, flag);
        if (flag != 0) previousVoltage = bus.getVoltageMag();
        return result;
    }

    @Override
    public double getOutput(Machine machine) {
        return Math.max(vstmin, Math.min(vstmax, super.getOutput(machine)));
    }

    protected double deviationInput(int code, double reference,
            BaseDStabBus<?, ?> bus, Machine machine, double dt) {
        if (code == 6) {
            return dt > 0.0 ? (bus.getVoltageMag() - previousVoltage) / dt : 0.0;
        }
        return selectedInput(code, bus, machine) - reference;
    }

    protected double selectedInput(int code, BaseDStabBus<?, ?> bus, Machine machine) {
        return switch (code) {
            case 1 -> machine.getSpeed();
            case 2 -> bus.getFreq();
            case 3 -> machine.getPe();
            case 4 -> machine.getPm() - machine.getPe();
            case 5 -> bus.getVoltageMag();
            case 6 -> 0.0;
            default -> throw new IllegalArgumentException("Unsupported PSS3B input code: " + code);
        };
    }

    private void applyPowerWorldCorrections() {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        tw1 = minimumPositive(tw1, minimum);
        tw2 = minimumPositive(tw2, minimum);
        tw3 = halfStepBypass(tw3, minimum);
        t1 = halfStepBypass(t1, minimum);
        t2 = halfStepBypass(t2, minimum);
        double high = Math.max(vstmax, vstmin);
        double low = Math.min(vstmax, vstmin);
        vstmax = Math.abs(high);
        vstmin = -Math.abs(low);
    }

    private static double minimumPositive(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double halfStepBypass(double value, double minimum) {
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
