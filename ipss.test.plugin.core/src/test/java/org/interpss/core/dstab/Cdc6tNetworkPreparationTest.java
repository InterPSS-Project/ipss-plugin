package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.HvdcObjectFactory;
import com.interpss.core.aclf.hvdc.*;

public class Cdc6tNetworkPreparationTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static Cdc6tParameters parameters(String name){
        return new Cdc6tParameters(name,new double[]{5,20,.05,.05,.2,.3,.02,.2,.3,.02,
                250,500,99,99,400,250,450,500,2000,425,2000,.02,-999,999,-999,0,0,999,999,0,999,.05});
    }
    private static double[] boundary(Cdc6tNetworkPreparation.Binding b){
        var r=b.owner().getRectifier().getBus().getVoltage();var i=b.owner().getInverter().getBus().getVoltage();
        b.runtime().evaluateBoundary(r.getReal(),r.getImaginary(),i.getReal(),i.getImaginary(),0,new double[4],0);
        double[] result=new double[8];b.runtime().readBoundary(result,0);return result;
    }
    @Test void exactNamedOwnerSuppliesSolvedEquipmentWithoutMutatingNetwork()throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);owner.setName("Line, O'Brien");
        var r=owner.getRectifier();var i=owner.getInverter();
        var rb=n.getBus(r.getBus().getId());var ib=n.getBus(i.getBus().getId());
        int before=rb.getDynamicBusDeviceList().size()+ib.getDynamicBusDeviceList().size();
        var b=Cdc6tNetworkPreparation.prepare(n,parameters("Line, O'Brien"),490);
        assertSame(owner,b.owner());assertNull(b.ports().rectifierPort().getDStabBus());
        assertEquals(before,rb.getDynamicBusDeviceList().size()+ib.getDynamicBusDeviceList().size());
        assertEquals(-500,b.operatingPoint().signedSetpoint(),1e-10);
        assertEquals(490,b.operatingPoint().modeThresholdKilovolts(),0);
        assertEquals(i.getDcVoltage(),b.operatingPoint().inverterVolts(),0);
        assertEquals(r.getDcVoltage(),b.operatingPoint().rectifierVolts(),0);
        assertEquals(r.getIdc(),b.operatingPoint().currentAmperes(),0);
        double[] values=boundary(b);var rp=r.powerIntoConverter();var ip=i.powerIntoConverter();
        assertEquals(rp.getReal(),values[0],1e-8);assertEquals(rp.getImaginary(),values[1],1e-7);
        assertEquals(ip.getReal(),values[3],1e-8);assertEquals(ip.getImaginary(),values[4],1e-7);
        var another=Cdc6tNetworkPreparation.prepare(n,parameters("Line, O'Brien"),490);
        b.runtime().applyStaggeredState(new double[]{1,2,3},0,0);
        double[] state=new double[3];another.runtime().readStaggeredState(state,0);
        assertArrayEquals(new double[]{i.getDcVoltage(),r.getIdc(),r.getDcVoltage()},state);
    }
    @Test void demandSignCurrentModeAndCompensationComeFromTheStaticOwner()throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        owner.setControlSide(HvdcControlSide.RECTIFIER);owner.setPowerDemand(505,UnitType.mW);
        var p=Cdc6tNetworkPreparation.prepare(n,parameters(owner.getName()),0);
        assertEquals(505,p.operatingPoint().signedSetpoint(),1e-10);
        assertEquals(1000,boundary(p)[0]*1e8/boundary(p)[6],1e-6);
        owner.setDcLineControlMode(HvdcControlMode.DC_CURRENT);owner.setCurrentDemand(1000);
        owner.setCompondR(1,UnitType.Ohm);owner.setScheduledDCVoltage(501,UnitType.kV);
        var c=Cdc6tNetworkPreparation.prepare(n,parameters(owner.getName()),0);
        assertEquals(Cdc6tCurrentOrder.Mode.CURRENT,c.operatingPoint().mode());
        assertEquals(1000,c.operatingPoint().signedSetpoint(),0);
        assertEquals(1,c.operatingPoint().compensationOhms(),0);
        assertEquals(501,c.operatingPoint().scheduledKilovolts(),0);
        assertEquals(500000,boundary(c)[7],1e-8);
    }
    @ParameterizedTest @ValueSource(doubles={900,1000,1100})
    void onlyBindingInitialCeilingRequestsOperatingPointPreparation(double ceiling)throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        double[] constants=parameters(owner.getName()).copySourceConstants();
        constants[18]=ceiling;constants[20]=ceiling;
        var parameters=new Cdc6tParameters(owner.getName(),constants);
        var b=Cdc6tNetworkPreparation.prepare(n,parameters,0);
        assertEquals(ceiling<1000,b.ports().rectifierPort().isInitialOperatingPointPreparation());
        assertFalse(b.ports().inverterPort().isInitialOperatingPointPreparation());
        assertEquals(1000,b.operatingPoint().currentAmperes(),1e-8,"Preserve the static schedule");
        assertArrayEquals(constants,parameters.copySourceConstants(),0);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preparedOwnerInitializesAndMaintainsEquilibriumUnderBothMethods(boolean staggered)throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();var a=f.context().getDynSimuAlgorithm();
        // Remove only this fixture's manually constructed pair before attaching the prepared owner.
        f.ports().rectifierPort().setDStabBus(null);f.ports().inverterPort().setDStabBus(null);
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var b=Cdc6tNetworkPreparation.prepare(n,parameters(owner.getName()),0);b.ports().attach();
        var rb=b.ports().rectifierPort().getDStabBus();var ib=b.ports().inverterPort().getDStabBus();
        double vr=rb.getVoltageMag(),vi=ib.getVoltageMag();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.1);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new StaggeredEulerSolver(a):new DefaultDynamicSimulationSolver(a));
        assertTrue(a.initialization());double[] state=new double[3],rates=new double[3];
        b.ports().rectifierPort().evaluateStaggered(.0005,rates,0);
        for(double rate:rates)assertEquals(0,rate,1e-5,"Zero initial derivative");
        while(a.getSimuTime()<.1-.00025){
            assertTrue(a.solveDEqnStep(true));assertEquals(vr,rb.getVoltageMag(),1e-5);assertEquals(vi,ib.getVoltageMag(),1e-5);
        }
        b.runtime().readStaggeredState(state,0);var p=b.operatingPoint();
        assertArrayEquals(new double[]{p.inverterVolts(),p.currentAmperes(),p.rectifierVolts()},state,.01);
        assertEquals(1,rb.getDynamicBusDeviceList().size());assertEquals(1,ib.getDynamicBusDeviceList().size());
    }
    @Test void converterPrimaryBaseIsDistinctFromTheBusNominalBase()throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);var p=parameters(owner.getName());
        var original=Cdc6tNetworkPreparation.prepare(n,p,0);double[] expected=boundary(original);
        // Equivalent transformer specification with a different primary base.
        for(var converter:java.util.List.of(owner.getRectifier(),owner.getInverter())){
            converter.setAcRatedVoltage(converter.getAcRatedVoltage()*1.1);
            converter.setXformerRatio(converter.getXformerRatio()/1.1);
        }
        assertArrayEquals(expected,boundary(Cdc6tNetworkPreparation.prepare(n,p,0)),1e-8);
        // Previously prepared coefficients remain independent of subsequent equipment edits.
        owner.getRectifier().setXformerTapSetting(1.1);
        assertArrayEquals(expected,boundary(original),0);
    }
    @Test void missingAmbiguousAndUnsolvedOwnersFailBeforeAttachment()throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);var p=parameters(owner.getName());
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,parameters("absent"),0));
        var duplicate=HvdcObjectFactory.createHvdcLine2TLCC();duplicate.setName(owner.getName());
        n.getSpecialBranchList().add(duplicate);
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));
        n.getSpecialBranchList().remove(duplicate);
        owner.setStatus(false);assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));owner.setStatus(true);
        n.setLfConverged(false);assertThrows(IllegalStateException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));
    }
    @Test void invalidBoundaryAndUnqualifiedEquipmentAreRejected()throws Exception{
        var f=Cdc6tCoupledNetworkTest.load(folder);var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);var p=parameters(owner.getName());var i=owner.getInverter();
        double current=i.getIdc();i.setIdc(current+1);
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));i.setIdc(current);
        double voltage=i.getDcVoltage();i.setDcVoltage(voltage+1);
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));i.setDcVoltage(voltage);
        owner.setPuBasedPowerFlowAlgo(true);assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));owner.setPuBasedPowerFlowAlgo(false);
        var z=i.getCommutingZ();i.setCommutingZ(new Complex(1,z.getImaginary()));
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));i.setCommutingZ(z);
        i.setCommutingCapacitor(1);assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));i.setCommutingCapacitor(0);
        i.setAcRatedVoltage(Double.NaN);assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(n,p,0));
    }
}
