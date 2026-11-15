package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.Arrays;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac8b.*;
import org.interpss.dstab.control.exc.psse.esac8b.Esac8bData;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Synthetic PID-boundary and stage regressions; no supplied case parameters. */
public class Ac8bPidBoundaryTest extends CorePluginTestSetup {
    private static final double TOL=1e-11;
    private record Fixture(Ac8bExciter exciter,Machine machine) {}
    private static Ac8bData parameters() {
        Ac8bData d=new Ac8bData();
        d.setTr(.02);d.setKpr(2);d.setKir(3);d.setKdr(0);d.setTdr(.05);
        d.setVpidmax(2);d.setVpidmin(-2);d.setKa(1);d.setTa(0);
        d.setVrmax(10);d.setVrmin(-10);d.setTe(.5);d.setKe(1);
        d.setVfemax(100);d.setVemin(-100);
        return d;
    }
    private static Fixture fixture(Ac8bData d) throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();
        Machine m=builder.getDStabNetwork().getMachine("Bus1-mach1");
        m.setEfd(1);m.getDStabBus().setVoltage(new Complex(1,0));
        Ac8bExciter e=builder.addExcAc8b("Bus1","1",d);
        assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(e,m);
    }
    private static double[] storage(Ac8bExciter e,String name)throws Exception {
        Field f=Ac8bExciter.class.getDeclaredField(name);f.setAccessible(true);return (double[])f.get(e);
    }
    private static void integral(Fixture f,double value)throws Exception {
        storage(f.exciter,"state")[2]=value;storage(f.exciter,"trial")[2]=value;
    }
    private static double[] rhs(Fixture f)throws Exception {
        Method m=Ac8bExciter.class.getDeclaredMethod("derivatives",double[].class,double[].class,Machine.class);m.setAccessible(true);
        double[] d=new double[5];m.invoke(f.exciter,f.exciter.getStateSnapshot(),d,f.machine);return d;
    }
    private static void step(Fixture f,double dt){
        assertTrue(f.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
        assertTrue(f.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
    }
    @Test
    void saturatedFieldReachesAndHoldsItsNonlinearCeilingBeforeReleasing() throws Exception {
        Ac8bData data = parameters();
        data.setE1(2.0);
        data.setSe1(.2);
        data.setE2(4.0);
        data.setSe2(.45);
        data.setVfemax(2.4); // E1 * (KE + SE1)
        data.setVemin(0.0);
        data.setVpidmax(20.0);
        for (double dt : new double[] {.05, .025}) {
            Fixture f = fixture(data);
            f.exciter.setVuel(5.0);
            double previous = f.exciter.getInternalFieldVoltage();
            for (int sample = 0; sample < 60; sample++) {
                step(f, dt);
                double field = f.exciter.getInternalFieldVoltage();
                assertTrue(field >= previous - TOL, "The field must approach the ceiling monotonically");
                assertTrue(field <= 2.0 + TOL, "The field feedback must stay within its ceiling");
                previous = field;
            }
            assertEquals(2.0, previous, TOL);
            f.exciter.setVuel(-5.0);
            step(f, dt);
            assertTrue(f.exciter.getInternalFieldVoltage() < previous);
        }
    }

    @Test void defaultAc8bIntegratesInteriorErrorWithoutTracking()throws Exception {
        Fixture f=fixture(parameters());assertFalse(f.exciter.getData().isEsac8bPti());
        f.exciter.setVuel(.1);assertEquals(.3,rhs(f)[2],TOL);
        step(f,.01);assertEquals(1.003,f.exciter.getStateSnapshot()[2],TOL);
    }
    @Test void upperOutwardIntegralIsHeld()throws Exception {
        Fixture f=fixture(parameters());f.exciter.setVuel(.75);
        assertTrue(f.exciter.getPidUnlimitedOutput()>2);assertEquals(0,rhs(f)[2],0);
        step(f,.01);assertEquals(1,f.exciter.getStateSnapshot()[2],0);
    }
    @Test void lowerOutwardIntegralIsHeld()throws Exception {
        Fixture f=fixture(parameters());f.exciter.setVuel(-2);
        assertTrue(f.exciter.getPidUnlimitedOutput()<-2);assertEquals(0,rhs(f)[2],0);
        step(f,.01);assertEquals(1,f.exciter.getStateSnapshot()[2],0);
    }
    @Test void bothExactPidBoundsHoldOutwardMotion()throws Exception {
        Fixture upper=fixture(parameters());upper.exciter.setVuel(.5);
        assertEquals(2,upper.exciter.getPidUnlimitedOutput(),TOL);assertEquals(0,rhs(upper)[2],0);
        Fixture lower=fixture(parameters());integral(lower,-1);lower.exciter.setVuel(-.5);
        assertEquals(-2,lower.exciter.getPidUnlimitedOutput(),TOL);assertEquals(0,rhs(lower)[2],0);
    }
    @Test void upperSaturationAllowsInwardIntegralRecovery()throws Exception {
        Fixture f=fixture(parameters());integral(f,3);f.exciter.setVuel(-.1);
        assertTrue(f.exciter.getPidUnlimitedOutput()>2);assertEquals(-.3,rhs(f)[2],TOL);
        step(f,.01);assertEquals(2.997,f.exciter.getStateSnapshot()[2],TOL);
    }
    @Test void lowerSaturationAllowsInwardIntegralRecovery()throws Exception {
        Fixture f=fixture(parameters());integral(f,-3);f.exciter.setVuel(.1);
        assertTrue(f.exciter.getPidUnlimitedOutput()<-2);assertEquals(.3,rhs(f)[2],TOL);
        step(f,.01);assertEquals(-2.997,f.exciter.getStateSnapshot()[2],TOL);
    }
    @Test void amplifierOnlyClippingDoesNotHoldThePidIntegral()throws Exception {
        Ac8bData d=parameters();d.setVrmax(1.1);Fixture f=fixture(d);f.exciter.setVuel(.1);
        assertEquals(1.2,f.exciter.getPidOutput(),TOL);assertEquals(1.1,f.exciter.getRegulatorOutput(),TOL);
        assertEquals(.3,rhs(f)[2],TOL);step(f,.01);assertEquals(1.003,f.exciter.getStateSnapshot()[2],TOL);
    }
    @Test void laggedAmplifierLimitAlsoLeavesPidIntegralActive()throws Exception {
        Ac8bData d=parameters();d.setTa(.1);d.setVrmax(1);Fixture f=fixture(d);f.exciter.setVuel(.1);
        double[] rates=rhs(f);assertEquals(0,rates[4],0);assertEquals(.3,rates[2],TOL);
        step(f,.01);assertEquals(1,f.exciter.getRegulatorOutput(),TOL);assertEquals(1.003,f.exciter.getStateSnapshot()[2],TOL);
    }
    @Test void predictorAndCorrectorKeepTheirOwnStateAndDerivativeHistory()throws Exception {
        Fixture f=fixture(parameters());integral(f,3);f.exciter.setVuel(-.1);
        assertTrue(f.exciter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
        assertEquals(3,storage(f.exciter,"state")[2],0);
        assertEquals(2.997,storage(f.exciter,"trial")[2],TOL);
        assertEquals(-.3,storage(f.exciter,"oldDerivative")[2],TOL);
        double[] view=f.exciter.getStateSnapshot();view[2]=999;
        assertEquals(2.997,f.exciter.getStateSnapshot()[2],TOL);
        assertTrue(f.exciter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        assertEquals(2.997,storage(f.exciter,"state")[2],TOL);
    }
    @Test void zeroDurationBetweenStagesPreservesAllHistoriesAndOutput()throws Exception {
        Fixture f=fixture(parameters());f.exciter.setVuel(.1);
        f.exciter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.machine,0);
        double[] state=storage(f.exciter,"state").clone(),trial=storage(f.exciter,"trial").clone(),old=storage(f.exciter,"oldDerivative").clone();double output=f.exciter.outputSignal;
        for(int flag:new int[]{0,1})assertTrue(f.exciter.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
        assertArrayEquals(state,storage(f.exciter,"state"),0);assertArrayEquals(trial,storage(f.exciter,"trial"),0);assertArrayEquals(old,storage(f.exciter,"oldDerivative"),0);assertEquals(output,f.exciter.outputSignal,0);
        assertTrue(f.exciter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));assertEquals(1.003,f.exciter.getStateSnapshot()[2],TOL);
        double[] accepted=f.exciter.getStateSnapshot();assertFalse(f.exciter.nextStep(-.01,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));assertArrayEquals(accepted,f.exciter.getStateSnapshot(),0);
    }
    @Test void esac8bDialectRetainsItsExistingTrackingEquation()throws Exception {
        Esac8bData d=new Esac8bData();Ac8bData p=parameters();
        d.setTr(p.getTr());d.setKpr(2);d.setKir(3);d.setKdr(0);d.setTdr(.05);d.setKa(1);d.setTa(0);d.setTe(.5);d.setKe(1);d.setVrmax(10);d.setVrmin(-10);
        // Typed bounds exercise the inherited dialect branch, not a 15-CON import extension.
        d.setVpidmax(2);d.setVpidmin(-2);Fixture f=fixture(d);f.exciter.setVuel(.75);
        assertTrue(f.exciter.getData().isEsac8bPti());assertEquals(3*(.75-2*(2.5-2)),rhs(f)[2],TOL);
    }
    @Test void ac8bInitializationAndFieldLimitShareTheQualifiedSaturationCutoff()throws Exception {
        Ac8bData d=parameters();d.setE1(2);d.setSe1(.12);d.setE2(4);d.setSe2(.12);
        Fixture f=fixture(d);
        // VE=1 is below |A|=sqrt(8), although it exceeds the signed A.
        assertEquals(1,f.exciter.getInternalFieldVoltage(),TOL);
        assertEquals(1,f.exciter.getRegulatorOutput(),TOL);
        assertEquals(1,f.exciter.getIntegralControllerOutput(),TOL);
        assertArrayEquals(new double[5],rhs(f),TOL);
        step(f,.0033333);
        assertEquals(1,f.exciter.getInternalFieldVoltage(),TOL);
        assertEquals(1,f.exciter.getOutput(f.machine),TOL);
        // The dynamic upper bound must use the same zero-saturation branch.
        Method upper=Ac8bExciter.class.getDeclaredMethod("fieldUpperLimit",double.class,double.class);
        upper.setAccessible(true);assertEquals(100,(double)upper.invoke(f.exciter,1.,0.),TOL);
    }
    @Test void esac8bInitializationRetainsItsHistoricalSignedSaturationCutoff()throws Exception {
        Esac8bData d=new Esac8bData();d.setTr(.02);d.setKpr(2);d.setKir(3);
        d.setKdr(0);d.setTdr(.05);d.setKa(1);d.setTa(0);d.setTe(.5);d.setKe(1);
        d.setVrmax(10);d.setVrmin(-10);d.setVpidmax(2);d.setVpidmin(-2);
        d.setE1(2);d.setSe1(.12);d.setE2(4);d.setSe2(.12);
        Fixture f=fixture(d);
        double original=org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter.saturation(1,2,.12,4,.12);
        assertTrue(original>0);
        assertEquals(1+original,f.exciter.getRegulatorOutput(),TOL);
        assertEquals(1+original,f.exciter.getIntegralControllerOutput(),TOL);
        assertArrayEquals(new double[5],rhs(f),TOL);
    }
    @Test void syntheticFiveStateResponseConvergesToAdaptiveHighAccuracyReference()throws Exception {
        // Independent adaptive integration of a public synthetic block diagram.
        // The reference resolves limiter transitions; it does not fit a trace.
        // Isolate the qualified zero-TA PID/amplifier branch. Positive-TA
        // stage projection has separate first-order crossing error and is
        // covered by its own amplifier-only test, not redefined here.
        for(double h:new double[]{.001,.0033333}){
            double coarse=compareAdaptive(h),fine=compareAdaptive(h/2);
            assertTrue(coarse<.012,"state error h="+h+": "+coarse);
            assertTrue(fine<coarse*.7,"halving h must reduce state error: "+coarse+" -> "+fine);
        }
    }
    private static double compareAdaptive(double h)throws Exception {
        Ac8bData p=parameters();p.setKpr(12);p.setKir(4);p.setKdr(1);p.setTdr(.04);p.setTa(0);p.setKa(1.3);p.setVrmax(1.5);p.setVrmin(-1.5);p.setTe(.2);
        Fixture f=fixture(p);double[] reference=f.exciter.getStateSnapshot();double t=0,max=0;double[] maxByState=new double[5],timeByState=new double[5];
        double[] edges={.02,.08,.14,.20,.5},voltage={1,.8,1,1.1,1};
        for(int phase=0;phase<edges.length;phase++){
            final double vt=voltage[phase];f.machine.getDStabBus().setVoltage(new Complex(vt,0));
            FirstOrderDifferentialEquations equation=new FirstOrderDifferentialEquations(){
                public int getDimension(){return 5;}
                public void computeDerivatives(double time,double[] y,double[] d){
                    double error=1-y[1],u=12*error+y[2]+(error-y[3])/.04,pid=Math.max(-2,Math.min(2,u));
                    d[1]=(vt-y[1])/.02;d[3]=(error-y[3])/.04;
                    d[2]=4*error;if((u>=2&&d[2]>0)||(u<=-2&&d[2]<0))d[2]=0;
                    // The qualified zero-TA branch is algebraic. Stored VR is
                    // inactive; using it as the live amplifier output is wrong.
                    d[4]=0;double regulator=Math.max(-1.5,Math.min(1.5,1.3*pid));
                    d[0]=(regulator-y[0])/.2;
                }};
            while(t<edges[phase]-1e-12){
                double dt=Math.min(h,edges[phase]-t);double[] next=new double[5];
                new DormandPrince853Integrator(1e-12,dt,1e-12,1e-12).integrate(equation,t,reference,t+dt,next);reference=next;
                step(f,dt);double[] actual=f.exciter.getStateSnapshot();
                for(int i=0;i<5;i++){double error=Math.abs(actual[i]-reference[i]);max=Math.max(max,error);if(error>maxByState[i]){maxByState[i]=error;timeByState[i]=t+dt;}}t+=dt;
            }
        }
        System.out.println("AC8B synthetic adaptive reference h="+h+" maxStateError="+max+" byState="+Arrays.toString(maxByState)+" at="+Arrays.toString(timeByState));return max;
    }
}
