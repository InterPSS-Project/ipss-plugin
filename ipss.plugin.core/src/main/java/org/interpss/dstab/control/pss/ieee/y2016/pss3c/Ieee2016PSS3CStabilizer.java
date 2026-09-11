package org.interpss.dstab.control.pss.ieee.y2016.pss3c;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2016 PSS3C with power logic and compensated frequency. */
@AnController(input="mach.speed", output="this.notch2.y", refPoint="0.0", display={})
public final class Ieee2016PSS3CStabilizer extends Ieee2005PSS3BStabilizer {
    private final Ieee2016PSS3CStabilizerData pss3cData;

    private double compensatedWashoutState;
    private double compensatedFrequencySignal;
    private double compensatedWashoutDerivative;
    private double compensatedWashoutTrial;
    private double filteredPgen;
    private double pgenFilterDerivative;
    private double pgenFilterTrial;
    private boolean pssActive;

    public Ieee2016PSS3CStabilizer(String id,
            Ieee2016PSS3CStabilizerData data, Machine machine) {
        super(id, "PSS3C", "IEEE-2016", data.baseData(), machine);
        pss3cData = data;
    }

    public Ieee2016PSS3CStabilizerData getPss3cData() {
        return pss3cData;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        double initialPgen = machine.getPe();
        compensatedWashoutState = pss3cData.ics1() == 6 && pss3cData.tcomp() > 0.0
                ? compensatedVoltageAngle(machine, pss3cData.xcomp()) / pss3cData.tcomp()
                : 0.0;
        compensatedFrequencySignal = 0.0;
        compensatedWashoutDerivative = 0.0;
        compensatedWashoutTrial = compensatedWashoutState;
        filteredPgen = initialPgen;
        pgenFilterDerivative = 0.0;
        pgenFilterTrial = initialPgen;
        pssActive = outputLogicDisabled()
                || initialPgen >= pss3cData.pssActivation();
        return super.initStates(bus, machine);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (pss3cData.ics1() == 6) {
            compensatedFrequencySignal = updateCompensatedFrequency(machine, dt, flag);
        }
        double measuredPgen = updateFilteredPgen(machine.getPe(), dt, flag);
        boolean result = super.nextStep(dt, method, machine, flag);
        if (flag != 0) updateActivation(measuredPgen);
        return result;
    }

    @Override
    public double getOutput(Machine machine) {
        return pssActive ? super.getOutput(machine) : 0.0;
    }

    @Override
    protected double selectedInput(int code, BaseDStabBus<?, ?> bus, Machine machine) {
        if (code == 0) return 0.0;
        return code == 6 ? compensatedFrequencySignal : super.selectedInput(code, bus, machine);
    }

    @Override
    protected double deviationInput(int code, double reference,
            BaseDStabBus<?, ?> bus, double previousVoltage,
            Machine machine, double dt) {
        return code == 6 ? compensatedFrequencySignal
                : super.deviationInput(code, reference, bus, previousVoltage, machine, dt);
    }

    /** PSS/E Model Library STATE order, extending the inherited nine PSS3B states. */
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>(super.getNamedStates());
        if (pss3cData.ics1() == 6) {
            // PSS/E's compensated-frequency signal includes the published
            // -1 pu offset. The internal deviation coordinate removes that
            // constant, so restore it only at the native state boundary.
            states.put("input1Transducer",
                    states.get("input1Transducer") - pss3cData.k1());
            states.put("input1Washout",
                    states.get("input1Washout") - pss3cData.k1());
        }
        states.put("compensatedFrequencyWashout", compensatedWashoutState);
        states.put("generatorPowerFilter", filteredPgen);
        return Map.copyOf(states);
    }

    public boolean isPssActive() { return pssActive; }
    public double getFilteredPgen() { return filteredPgen; }
    public double getCompensatedFrequencySignal() { return compensatedFrequencySignal; }

    private double updateFilteredPgen(double pgen, double dt, int flag) {
        double timeConstant = pss3cData.tpgfilt();
        if (timeConstant <= 0.0 || dt <= 0.0) {
            filteredPgen = pgen;
            return filteredPgen;
        }
        if (flag == 0) {
            pgenFilterDerivative = (pgen - filteredPgen) / timeConstant;
            pgenFilterTrial = filteredPgen + pgenFilterDerivative * dt;
            return pgenFilterTrial;
        }
        double correctedDerivative = (pgen - pgenFilterTrial) / timeConstant;
        filteredPgen += 0.5 * (pgenFilterDerivative + correctedDerivative) * dt;
        return filteredPgen;
    }

    private void updateActivation(double pgen) {
        if (outputLogicDisabled()) {
            pssActive = true;
            return;
        }
        if (pssActive) {
            if (pgen <= pss3cData.pssDeactivation()) pssActive = false;
        } else if (pgen >= pss3cData.pssActivation()) {
            pssActive = true;
        }
    }

    private boolean outputLogicDisabled() {
        return pss3cData.pssActivation() < 0.0
                || pss3cData.pssActivation() == pss3cData.pssDeactivation();
    }

    private double updateCompensatedFrequency(Machine machine, double dt, int flag) {
        if (dt <= 0.0) return 0.0;
        double angle = compensatedVoltageAngle(machine, pss3cData.xcomp());
        double timeConstant = pss3cData.tcomp();
        if (timeConstant <= 0.0) {
            compensatedWashoutState = 0.0;
            compensatedWashoutTrial = 0.0;
            return 0.0;
        }
        double target = angle / timeConstant;
        double omegaBase = 2.0 * Math.PI
                * machine.getDStabBus().getNetwork().getFrequency();
        if (flag == 0) {
            compensatedWashoutDerivative =
                    (target - compensatedWashoutState) / timeConstant;
            compensatedWashoutTrial = compensatedWashoutState
                    + compensatedWashoutDerivative * dt;
            return (target - compensatedWashoutState) / omegaBase;
        }
        double correctedDerivative =
                (target - compensatedWashoutTrial) / timeConstant;
        compensatedWashoutState += 0.5
                * (compensatedWashoutDerivative + correctedDerivative) * dt;
        return (target - compensatedWashoutState) / omegaBase;
    }

    private static Complex compensatedVoltage(Machine machine, double xcomp) {
        Complex terminalVoltage = machine.getDStabBus().getVoltage();
        Complex terminalCurrent = machine.getIgen()
                .subtract(terminalVoltage.multiply(machine.getYgen()));
        Complex power = terminalVoltage.multiply(terminalCurrent.conjugate())
                .divide(machine.getIMultiFactor());
        double voltageMagnitude = terminalVoltage.abs();
        if (voltageMagnitude == 0.0) return Complex.ZERO;
        // Published Vt-aligned form:
        // (|Vt| + Q Xcomp / |Vt|) + j(P Xcomp / |Vt|).
        return new Complex(
                voltageMagnitude + power.getImaginary() * xcomp / voltageMagnitude,
                power.getReal() * xcomp / voltageMagnitude);
    }

    private static double compensatedVoltageAngle(Machine machine, double xcomp) {
        return compensatedVoltage(machine, xcomp).getArgument();
    }

}
