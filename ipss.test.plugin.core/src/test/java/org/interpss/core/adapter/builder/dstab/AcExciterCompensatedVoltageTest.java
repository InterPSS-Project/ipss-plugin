package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.exc.ac7b.*;
import com.interpss.dstab.controller.exc.ac8b.*;
import com.interpss.dstab.mach.ieeevc.IeeeVcData;
import com.interpss.dstab.mach.ieeevc.IeeeVoltageCompensatedMachine;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** Synthetic live compensation boundaries; physical power-supply inputs stay separate. */
public class AcExciterCompensatedVoltageTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;
    private record Fixture(Machine machine,AnnotateExciter exciter,IeeeVoltageCompensatedMachine compensated) {}
    private static Fixture fixture(boolean ac7,double tr,IeeeVcData vc)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(m.initStates(m.getDStabBus()));m.setEfd(1.2);
        var compensated=assertInstanceOf(IeeeVoltageCompensatedMachine.class,m);compensated.setIeeeVcData(vc);
        AnnotateExciter e;
        if(ac7){
            Ac7bData d=new Ac7bData();d.setTr(tr);d.setKpr(2);d.setKir(3);d.setKdr(0);d.setTdr(.03);
            d.setVrmax(99);d.setVrmin(-99);d.setKpa(1);d.setKia(1);d.setVamax(99);d.setVamin(-99);
            d.setKp(2);d.setKl(1);d.setKf1(.1);d.setKf2(1);d.setKf3(0);d.setTf(.05);
            d.setKc(0);d.setKd(0);d.setKe(1);d.setTe(.5);d.setVfemax(99);d.setVemin(0);
            e=b.addExcAc7b("Bus1","1","AC7B",d);
        }else{
            Ac8bData d=new Ac8bData();d.setTr(tr);d.setKpr(2);d.setKir(3);d.setKdr(0);d.setTdr(.03);
            d.setVpidmax(99);d.setVpidmin(-99);d.setKa(1);d.setTa(0);d.setVrmax(99);d.setVrmin(-99);
            d.setKc(0);d.setKd(0);d.setKe(1);d.setTe(.5);d.setVfemax(99);d.setVemin(0);
            e=b.addExcAc8b("Bus1","1",d);
        }
        assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e,compensated);
    }
    private static double sensed(Fixture f){return f.exciter instanceof Ac7bExciter e?e.getSensedVoltage():((Ac8bExciter)f.exciter).getSensedVoltage();}
    private static double[] storage(Fixture f,String field)throws Exception{Field v=f.exciter.getClass().getDeclaredField(field);v.setAccessible(true);return ((double[])v.get(f.exciter));}
    private static double[] rhs(Fixture f)throws Exception{Method v=f.exciter.getClass().getDeclaredMethod("derivatives",double[].class,double[].class,Machine.class);v.setAccessible(true);double[] d=new double[storage(f,"state").length];v.invoke(f.exciter,storage(f,"state"),d,f.machine);return d;}
    @Test void initializationUsesPositiveAndNegativeCompensationAndRemainsInEquilibrium()throws Exception{
        for(boolean ac7:new boolean[]{false,true})for(double xc:new double[]{.034,-.041}){
            Fixture f=fixture(ac7,.02,new IeeeVcData(.023,xc));
            double expected=f.compensated.getCmlMachineVoltage();
            assertTrue(Math.abs(expected-f.machine.getDStabBus().getVoltageMag())>1e-4);
            assertEquals(expected,sensed(f),TOL);assertEquals(expected,f.exciter.getRefPoint(),TOL);
            assertArrayEquals(new double[rhs(f).length],rhs(f),TOL);
        }
    }
    @Test void positiveTransducerUsesTheLiveCompensatedBoundaryAtBothStages()throws Exception{
        for(boolean ac7:new boolean[]{false,true}){
            Fixture f=fixture(ac7,.02,new IeeeVcData(.023,.034));double initial=sensed(f);
            f.compensated.setIeeeVcData(new IeeeVcData(.017,-.029));double input=f.compensated.getCmlMachineVoltage();
            assertEquals((input-initial)/.02,rhs(f)[1],TOL);
            double h=.001;double left=(input-initial)/.02;
            assertTrue(f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
            double predictor=initial+h*left;assertEquals(predictor,sensed(f),TOL);
            assertTrue(f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
            assertEquals(initial+h*.5*(left+(input-predictor)/.02),sensed(f),TOL);
        }
    }
    @Test void bypassedTransducerUsesCompensationImmediately()throws Exception{
        for(boolean ac7:new boolean[]{false,true}){
            Fixture f=fixture(ac7,0,new IeeeVcData(.023,.034));double reference=sensed(f);
            f.compensated.setIeeeVcData(new IeeeVcData(.017,-.029));double input=f.compensated.getCmlMachineVoltage();
            assertEquals(input,sensed(f),TOL);assertEquals(3*(reference-input),rhs(f)[2],TOL);
        }
    }
    @Test void ac7PotentialSourceContinuesToUsePhysicalTerminalVoltage()throws Exception{
        Fixture f=fixture(true,.02,new IeeeVcData(.023,.034));Ac7bExciter e=(Ac7bExciter)f.exciter;
        double physical=f.machine.getDStabBus().getVoltageMag();
        assertEquals(1.2/(2*physical),e.getPiOutput(),TOL);assertEquals(1.2,e.getRegulatorOutput(),TOL);
        f.machine.getDStabBus().setVoltage(new Complex(1.02,0));
        assertEquals(1.2*1.02/physical,e.getRegulatorOutput(),TOL);
    }
    @Test void absentCompensationAndZeroDurationPreserveTheExistingStageContract()throws Exception{
        for(boolean ac7:new boolean[]{false,true}){
            Fixture f=fixture(ac7,.02,null);assertEquals(f.machine.getDStabBus().getVoltageMag(),sensed(f),TOL);
            f.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
            double[] state=storage(f,"state").clone(),trial=storage(f,"trial").clone(),derivative=storage(f,"oldDerivative").clone();
            f.compensated.setIeeeVcData(new IeeeVcData(.023,.034));
            for(int flag:new int[]{0,1})assertTrue(f.exciter.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
            assertArrayEquals(state,storage(f,"state"),0);assertArrayEquals(trial,storage(f,"trial"),0);assertArrayEquals(derivative,storage(f,"oldDerivative"),0);
        }
    }
}
