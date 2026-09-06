package org.interpss.dstab.control.pss.ieee.y2016.pss4c;

import java.lang.reflect.Field;

import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer.Band;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData.BandData;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;

/** IEEE Std 421.5-2016 four-band PSS4C power-system stabilizer. */
@AnController(input="mach.speed", output="this.outputSignal", refPoint="0.0", display={})
public final class Ieee2016PSS4CStabilizer extends AnnotateStabilizer
        implements IntegrationStepAware {
    private final Ieee2016PSS4CStabilizerData sourceData;
    private final Ieee2005PSS4BStabilizer threeBandModel;
    private BandData effectiveVeryLowBand;
    private Band veryLowBand;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double veryLowOutput;
    public double outputSignal;

    public Ieee2016PSS4CStabilizer(String id,
            Ieee2016PSS4CStabilizerData data, Machine machine) {
        super(id, "PSS4C", "IEEE-2016");
        sourceData = data;
        threeBandModel = Ieee2005PSS4BStabilizer.createDetachedEngine(
                id + "-three-band", data.threeBandData(), machine);
        setMachine(machine);
    }

    public Ieee2016PSS4CStabilizerData getData() { return sourceData; }
    public BandData getEffectiveVeryLowBand() { return effectiveVeryLowBand; }
    public double getVeryLowOutput() { return veryLowOutput; }
    public double getLowOutput() { return threeBandModel.getLowOutput(); }
    public double getIntermediateOutput() { return threeBandModel.getIntermediateOutput(); }
    public double getHighOutput() { return threeBandModel.getHighOutput(); }
    public double getEffectiveInertiaCoefficient() {
        return threeBandModel.getEffectiveData().input().h();
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "PSS4C integration-step settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
        threeBandModel.configureIntegrationStep(timeStepSec, multiplier);
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        if (!threeBandModel.initStates(bus, machine)) return false;
        effectiveVeryLowBand = Ieee2005PSS4BStabilizer.correctedBand(
                sourceData.veryLowBand(),
                minimumTimeConstantMultiplier * integrationStep);
        veryLowBand = new Band(effectiveVeryLowBand);
        veryLowOutput = 0.0;
        outputSignal = 0.0;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (!threeBandModel.nextStep(dt, method, machine, flag)) return false;
        if (dt <= 0.0) return true;
        int stage = method == DynamicSimuMethod.MODIFIED_EULER ? flag : 2;
        veryLowOutput = veryLowBand.advance(
                threeBandModel.getLowIntermediateInput(), dt, stage);
        outputSignal = Ieee2005PSS4BStabilizer.clamp(
                veryLowOutput + threeBandModel.getLowOutput()
                        + threeBandModel.getIntermediateOutput()
                        + threeBandModel.getHighOutput(),
                threeBandModel.getEffectiveData().vstmax(),
                threeBandModel.getEffectiveData().vstmin());
        return true;
    }

    @Override public double getOutput(Machine machine) { return outputSignal; }
    @Override public AnController getAnController() { return getClass().getAnnotation(AnController.class); }
    @Override public Field getField(String name) throws Exception { return getClass().getField(name); }
    @Override public Object getFieldObject(Field field) throws Exception { return field.get(this); }
}
