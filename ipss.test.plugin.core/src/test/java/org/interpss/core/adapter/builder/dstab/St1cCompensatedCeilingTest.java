package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st1c.*;
import org.interpss.dstab.mach.IeeeVcData;
import org.interpss.dstab.mach.IeeeVoltageCompensatedMachine;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Distinct compensated sensor and physical terminal supply at synthetic limits. */
public class St1cCompensatedCeilingTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;
    private record Fixture(Machine machine,St1cExciter exciter,IeeeVoltageCompensatedMachine compensated) {}
    private static Fixture fixture(double xc,double efd,boolean expand)throws Exception{
        var b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(m.initStates(m.getDStabBus()));m.setEfd(efd);
        var compensated=assertInstanceOf(IeeeVoltageCompensatedMachine.class,m);compensated.setIeeeVcData(new IeeeVcData(.023,xc));
        St1cData d=new St1cData();d.setUel(1);d.setVos(1);d.setOel(1);d.setTr(.02);
        d.setVimax(99);d.setVimin(-99);d.setTc(0);d.setTb(0);d.setTc1(0);d.setTb1(0);
        d.setKa(10);d.setTa(0);d.setVamax(99);d.setVamin(-99);d.setVrmax(expand?.1:2);d.setVrmin(expand?-.1:-1);
        d.setKc(.1);d.setKf(0);d.setTf(0);d.setKlr(0);d.setIlr(99);
        St1cExciter e=b.addExcSt1c("Bus1","1",d);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e,compensated);
    }
    private static double terminal(Fixture f){return f.machine.getDStabBus().getVoltageMag()/f.machine.getVMultiFactor();}
    @Test void compensationAffectsMeasurementButNeitherPhysicalCeiling()throws Exception{
        for(double xc:new double[]{.034,-.041}){
            Fixture f=fixture(xc,1.2,false);St1cExciter e=f.exciter;double sensed=f.compensated.getCmlMachineVoltage();
            assertTrue(Math.abs(sensed-terminal(f))>1e-4);assertEquals(sensed,e.getSensedVoltage(),TOL);
            double lower=terminal(f)*e.vrmin,upper=terminal(f)*e.vrmax-e.kc*f.machine.calculateIfd(MachineIfdBase.EXCITER);
            assertEquals(lower,e.getFieldLowerLimit(),TOL);assertEquals(upper,e.getFieldUpperLimit(),TOL);
            f.compensated.setIeeeVcData(new IeeeVcData(-.017,-xc));
            assertEquals(lower,e.getFieldLowerLimit(),TOL);assertEquals(upper,e.getFieldUpperLimit(),TOL);
        }
    }
    @Test void upperAndLowerOutputLimitsUsePhysicalSupplyUnderCompensation()throws Exception{
        for(double xc:new double[]{.034,-.041}){
            Fixture f=fixture(xc,1.2,false);double ref=f.exciter.getRefPoint();
            f.exciter.setRefPoint(ref+100);assertEquals(f.exciter.getFieldUpperLimit(),f.exciter.getOutput(f.machine),TOL);
            f.exciter.setRefPoint(ref-100);assertEquals(f.exciter.getFieldLowerLimit(),f.exciter.getOutput(f.machine),TOL);
            f.machine.getDStabBus().setVoltage(new Complex(.98,0));
            assertEquals(.98/f.machine.getVMultiFactor()*f.exciter.vrmin,f.exciter.getFieldLowerLimit(),TOL);
            assertEquals(f.exciter.getFieldLowerLimit(),f.exciter.getOutput(f.machine),TOL);
        }
    }
    @Test void initializationExpandsRuntimeLimitsUsingTheSamePhysicalSupply()throws Exception{
        for(double xc:new double[]{.034,-.041})for(double efd:new double[]{1.2,-1.2}){
            Fixture f=fixture(xc,efd,true);St1cExciter e=f.exciter;double ifd=f.machine.calculateIfd(MachineIfdBase.EXCITER);
            assertEquals(Math.max(.1,(efd+e.kc*ifd)/terminal(f)),e.vrmax,TOL);
            assertEquals(Math.min(-.1,efd/terminal(f)),e.vrmin,TOL);assertEquals(efd,e.getOutput(f.machine),TOL);
            assertEquals(.1,e.getData().getVrmax(),0);assertEquals(-.1,e.getData().getVrmin(),0);
        }
    }
    @Test void algebraicObservationAndZeroDurationDoNotAdvanceTheControllerStates()throws Exception{
        Fixture f=fixture(.034,1.2,false);double[] state=f.exciter.getStateSnapshot();
        f.exciter.setRefPoint(f.exciter.getRefPoint()+100);f.exciter.getOutput(f.machine);
        assertArrayEquals(state,f.exciter.getStateSnapshot(),0);
        for(int flag:new int[]{0,1})assertTrue(f.exciter.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
        assertArrayEquals(state,f.exciter.getStateSnapshot(),0);
    }
}
