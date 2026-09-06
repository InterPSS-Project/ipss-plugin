package org.interpss.dstab.control.pss.ieee.y2016.pss3c;

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

    private double previousCompensatedAngle;
    private double compensatedFrequencyState;
    private double compensatedFrequencySignal;
    private double compensatedFrequencyDerivative;
    private double compensatedFrequencyTrial;
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
        previousCompensatedAngle = pss3cData.ics1() == 7
                ? compensatedVoltageAngle(machine, pss3cData.xcomp()) : 0.0;
        compensatedFrequencyState = 0.0;
        compensatedFrequencySignal = 0.0;
        compensatedFrequencyDerivative = 0.0;
        compensatedFrequencyTrial = 0.0;
        filteredPgen = initialPgen;
        pgenFilterDerivative = 0.0;
        pgenFilterTrial = initialPgen;
        pssActive = initialPgen >= pss3cData.pssActivation();
        return super.initStates(bus, machine);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (pss3cData.ics1() == 7) {
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
        return code == 7 ? compensatedFrequencySignal : super.selectedInput(code, bus, machine);
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
        if (pssActive) {
            if (pgen <= pss3cData.pssDeactivation()) pssActive = false;
        } else if (pgen >= pss3cData.pssActivation()) {
            pssActive = true;
        }
    }

    private double updateCompensatedFrequency(Machine machine, double dt, int flag) {
        if (dt <= 0.0) return compensatedFrequencyState;
        double angle = compensatedVoltageAngle(machine, pss3cData.xcomp());
        double raw = wrapAngle(angle - previousCompensatedAngle)
                / (2.0 * Math.PI * machine.getDStabBus().getNetwork().getFrequency() * dt);
        double timeConstant = pss3cData.tcomp();
        if (timeConstant <= 0.0) {
            compensatedFrequencyState = raw;
        } else if (flag == 0) {
            compensatedFrequencyDerivative =
                    (raw - compensatedFrequencyState) / timeConstant;
            compensatedFrequencyTrial = compensatedFrequencyState
                    + compensatedFrequencyDerivative * dt;
            return compensatedFrequencyTrial;
        } else {
            double correctedDerivative =
                    (raw - compensatedFrequencyTrial) / timeConstant;
            compensatedFrequencyState += 0.5
                    * (compensatedFrequencyDerivative + correctedDerivative) * dt;
        }
        if (flag != 0) previousCompensatedAngle = angle;
        return compensatedFrequencyState;
    }

    private static Complex compensatedVoltage(Machine machine, double xcomp) {
        Complex currentMachineBase = machine.getIxy().divide(machine.getIMultiFactor());
        return machine.getDStabBus().getVoltage()
                .add(currentMachineBase.multiply(new Complex(0.0, xcomp)));
    }

    private static double compensatedVoltageAngle(Machine machine, double xcomp) {
        return compensatedVoltage(machine, xcomp).getArgument();
    }

    private static double wrapAngle(double angle) {
        if (angle > Math.PI) return angle - 2.0 * Math.PI;
        if (angle < -Math.PI) return angle + 2.0 * Math.PI;
        return angle;
    }
}
