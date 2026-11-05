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
import com.interpss.core.HvdcObjectFactory;
import com.interpss.core.aclf.hvdc.*;

/** Solved-network preparation; this does not claim full dynamic network attachment. */
public class Chvdc2NetworkPreparationTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static Chvdc2Parameters parameters(String name,boolean dynamic,double capacitance) {
        double[] c=Chvdc2ParametersTest.constants();c[47]=capacitance;
        return new Chvdc2Parameters(name,Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,dynamic?1:0},c);
    }
    private Cdc6tCoupledNetworkTest.Fixture fixture()throws Exception {
        var f=Cdc6tCoupledNetworkTest.load(folder);
        f.ports().rectifierPort().setDStabBus(null);f.ports().inverterPort().setDStabBus(null);
        return f;
    }
    @Test void namedSolvedOwnerPreservesPhysicalBoundaryAndDoesNotAttachOrMutate()throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);owner.setName("Link, O'Brien");
        var p=parameters(owner.getName(),true,0);var r=owner.getRectifier();var i=owner.getInverter();
        var rv=r.getBus().getVoltage();var iv=i.getBus().getVoltage();
        var b=Chvdc2NetworkPreparation.prepare(n,p,.0005,-.001);
        assertSame(owner,b.owner());assertTrue(owner.isActive());assertTrue(n.isLfConverged());
        assertEquals(rv,r.getBus().getVoltage());assertEquals(iv,i.getBus().getVoltage());
        assertTrue(n.getBus(r.getBus().getId()).getDynamicBusDeviceList().isEmpty());
        assertTrue(n.getBus(i.getBus().getId()).getDynamicBusDeviceList().isEmpty());
        assertEquals(r.getIdc(),b.operatingPoint().currentAmperes(),0);
        assertEquals(-.001,b.operatingPoint().initialTimeSeconds(),0);
        double[] values=new double[10];b.runtime().readBoundary(values,0);
        assertEquals(r.getDcVoltage(),values[0],1e-8);assertEquals(i.getDcVoltage(),values[1],1e-8);
        assertEquals(r.getIdc(),values[2],1e-7);assertEquals(i.getIdc(),values[3],1e-7);
        assertEquals(r.powerIntoConverter().getReal(),values[4],1e-8);
        assertEquals(r.powerIntoConverter().getImaginary(),values[5],1e-7);
        assertEquals(i.powerIntoConverter().getReal(),values[6],1e-8);
        assertEquals(i.powerIntoConverter().getImaginary(),values[7],1e-7);
        var another=Chvdc2NetworkPreparation.prepare(n,p,.0005,-.001);
        var untouched=another.runtime().getIntegrationStates();
        b.runtime().setBoundary(.99,0,1.01,0,900,0);b.runtime().step(.00001,0);b.runtime().step(.00001,1);
        assertEquals(untouched,another.runtime().getIntegrationStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothIntegratorsMaintainPreparedEquilibrium(boolean staggered)throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        for(boolean dynamic:new boolean[]{false,true}) {
            var b=Chvdc2NetworkPreparation.prepare(n,parameters(owner.getName(),dynamic,0),.0005,0);
            var m=b.runtime();int size=m.staggeredStateCount();
            double[] initial=new double[size],x=new double[size],rates=new double[size],old=new double[size];
            m.readStaggeredState(initial,0);m.evaluateStaggered(0,rates,0);
            for(double rate:rates)assertEquals(0,rate,1e-7);
            m.acceptBoundary(0,0);
            for(int k=0;k<200;k++) {
                if(staggered) {
                    m.readStaggeredState(x,0);m.evaluateStaggered(.0005,rates,0);
                    for(int j=0;j<size;j++)x[j]+=.0005*(k==0?rates[j]:1.5*rates[j]-.5*old[j]);
                    System.arraycopy(rates,0,old,0,size);m.applyStaggeredState(x,0,.0005);m.completeStaggeredStep(.0005);
                }else {m.step(.0005,0);m.evaluatePredictorProtection((k+1)*.0005,2*k+1);m.step(.0005,1);}
                m.acceptBoundary((k+1)*.0005,2*k+2);
            }
            m.readStaggeredState(x,0);assertArrayEquals(initial,x,1e-6);
        }
    }
    @ParameterizedTest @ValueSource(strings={"rectifier","inverter"})
    void finiteConverterResistancePreservesPreparedEnergyAndEquilibrium(String end)throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var converter=end.equals("rectifier")?owner.getRectifier():owner.getInverter();
        converter.setCommutingZ(new Complex(.5,converter.getCommutingZ().getImaginary()));
        var flow=f.context().getDynSimuAlgorithm().getAclfAlgorithm();flow.setTolerance(1e-10);
        assertTrue(flow.loadflow());
        for(boolean staggered:new boolean[]{false,true}) {
            var b=Chvdc2NetworkPreparation.prepare(n,parameters(owner.getName(),true,0),.0005,0);
            var m=b.runtime();double[] initial=new double[m.staggeredStateCount()],x=initial.clone(),rates=initial.clone(),old=initial.clone(),boundary=new double[10];
            m.readStaggeredState(initial,0);m.evaluateStaggered(0,rates,0);
            for(double rate:rates)assertEquals(0,rate,1e-7);
            m.acceptBoundary(0,0);
            for(int k=0;k<100;k++) {
                if(staggered) {
                    m.readStaggeredState(x,0);m.evaluateStaggered(.0005,rates,0);
                    for(int j=0;j<x.length;j++)x[j]+=.0005*(k==0?rates[j]:1.5*rates[j]-.5*old[j]);
                    System.arraycopy(rates,0,old,0,x.length);m.applyStaggeredState(x,0,.0005);m.completeStaggeredStep(.0005);
                }else {m.step(.0005,0);m.evaluatePredictorProtection((k+1)*.0005,2*k+1);m.step(.0005,1);}
                m.acceptBoundary((k+1)*.0005,2*k+2);m.readBoundary(boundary,0);
                double current=boundary[2],loss=(owner.getRdc(UnitType.Ohm)+2*converter.getNBridges()*.5)*current*current/(n.getBaseKva()*1000);
                assertTrue(loss>0);assertEquals(loss,boundary[4]+boundary[6],1e-10,"Line plus winding dissipative power");
            }
            m.readStaggeredState(x,0);assertArrayEquals(initial,x,1e-6);
        }
    }
    @Test void preparationUsesFrequencyAndOuterIntervalAndFreezesEquipment()throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();
        var o=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);var r=o.getRectifier();var i=o.getInverter();
        n.setFrequency(50);var p=parameters(o.getName(),true,1);
        var fine=Chvdc2NetworkPreparation.prepare(n,p,.0005,0);
        double inductance=2.5+2*1.75*(r.getCommutingZ().getImaginary()+i.getCommutingZ().getImaginary())/(2*Math.PI*50);
        assertEquals(inductance,fine.effectiveHenries(),1e-14);assertEquals(1e-6,fine.capacitanceFarads(),0);
        var coarse=Chvdc2NetworkPreparation.prepare(n,p,1,0);assertEquals(0,coarse.capacitanceFarads(),0);
        assertEquals(1,p.value(Chvdc2Parameters.Constant.C_LINE_MICRO_F),0);
        double[] before=new double[10],after=new double[10];fine.runtime().readBoundary(before,0);
        r.setXformerTapSetting(r.getXformerTapSetting()*1.01);i.setCommutingZ(new Complex(0,20));n.setFrequency(60);
        fine.runtime().readBoundary(after,0);assertArrayEquals(before,after);assertEquals(50,fine.frequencyHz(),0);
    }
    @Test void missingAmbiguousAndUnsolvedOwnersAreRejected()throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();var o=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var p=parameters(o.getName(),true,0);
        assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,parameters("absent",true,0),.0005,0));
        var duplicate=HvdcObjectFactory.createHvdcLine2TLCC();duplicate.setName(o.getName());n.getSpecialBranchList().add(duplicate);
        assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,p,.0005,0));
        n.getSpecialBranchList().remove(duplicate);
        o.setStatus(false);assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,p,.0005,0));o.setStatus(true);
        n.setLfConverged(false);assertThrows(IllegalStateException.class,()->Chvdc2NetworkPreparation.prepare(n,p,.0005,0));
    }
    @Test void inconsistentPhysicalDataAndUnsupportedEquipmentFailBeforeAnyAttachment()throws Exception {
        var f=fixture();var n=f.context().getDStabilityNet();var o=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var p=parameters(o.getName(),true,0);var i=o.getInverter();
        double current=i.getIdc();i.setIdc(current+1);reject(n,p);i.setIdc(current);
        double voltage=i.getDcVoltage();i.setDcVoltage(voltage+1);reject(n,p);i.setDcVoltage(voltage);
        int bridges=i.getNBridges();i.setNBridges(bridges+1);reject(n,p);i.setNBridges(bridges);
        o.setPuBasedPowerFlowAlgo(true);reject(n,p);o.setPuBasedPowerFlowAlgo(false);
        var z=i.getCommutingZ();for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {i.setCommutingZ(new Complex(bad,z.getImaginary()));reject(n,p);}i.setCommutingZ(z);
        i.setCommutingCapacitor(1);reject(n,p);i.setCommutingCapacitor(0);
        double rated=i.getAcRatedVoltage();i.setAcRatedVoltage(Double.NaN);reject(n,p);i.setAcRatedVoltage(rated);
        double resistance=o.getRdc(UnitType.Ohm);o.setRdc(0,UnitType.Ohm);reject(n,p);o.setRdc(resistance,UnitType.Ohm);
        n.setFrequency(0);reject(n,p);n.setFrequency(60);
        assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,p,0,0));
        assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,p,.0005,Double.NaN));
    }
    private static void reject(com.interpss.dstab.BaseDStabNetwork<?,?> n,Chvdc2Parameters p) {
        var o=(HvdcLine2TLCC<?>)n.getSpecialBranchList().get(0);
        var rv=o.getRectifier().getBus().getVoltage();var iv=o.getInverter().getBus().getVoltage();
        boolean active=o.isActive(),solved=n.isLfConverged();
        assertThrows(IllegalArgumentException.class,()->Chvdc2NetworkPreparation.prepare(n,p,.0005,0));
        assertEquals(active,o.isActive());assertEquals(solved,n.isLfConverged());
        assertEquals(rv,o.getRectifier().getBus().getVoltage());assertEquals(iv,o.getInverter().getBus().getVoltage());
        assertTrue(n.getBus(o.getFromBus().getId()).getDynamicBusDeviceList().isEmpty());
        assertTrue(n.getBus(o.getToBus().getId()).getDynamicBusDeviceList().isEmpty());
    }
}
