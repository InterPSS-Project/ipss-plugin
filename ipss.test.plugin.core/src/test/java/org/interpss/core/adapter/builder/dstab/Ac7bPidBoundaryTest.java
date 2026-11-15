package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bData;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Public synthetic boundary equations, not a full-model reference qualification. */
public class Ac7bPidBoundaryTest extends CorePluginTestSetup {
    private static final double TOL=1e-11;
    private static final int VE=0, PID_I=2, PI_I=4;
    private record Fixture(Ac7bExciter e,Machine m) {}
    private static Ac7bData parameters(){
        Ac7bData d=new Ac7bData();d.setKpr(2);d.setKir(3);d.setTdr(.05);
        d.setVrmax(2);d.setVrmin(-2);d.setKpa(1);d.setKia(0);
        d.setVamax(10);d.setVamin(-10);d.setKl(100);d.setTf(.2);
        d.setKe(1);d.setTe(.5);d.setVfemax(100);d.setVemin(-100);return d;
    }
    private static Fixture fixture(Ac7bData d,String dialect,double efd,boolean init)throws Exception{
        var b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setEfd(efd);m.getDStabBus().setVoltage(new Complex(1,0));
        Ac7bExciter e=b.addExcAc7b("Bus1","1",dialect,d);
        if(init)assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(e,m);
    }
    private static Fixture fixture()throws Exception{return fixture(parameters(),"AC7B",1,true);}
    private static double[] storage(Fixture f,String name)throws Exception{
        Field x=Ac7bExciter.class.getDeclaredField(name);x.setAccessible(true);return (double[])x.get(f.e);
    }
    private static void integral(Fixture f,int index,double value)throws Exception{
        storage(f,"state")[index]=value;storage(f,"trial")[index]=value;
    }
    private static double[] rhs(Fixture f,double[] x)throws Exception{
        Method m=Ac7bExciter.class.getDeclaredMethod("derivatives",double[].class,double[].class,Machine.class);m.setAccessible(true);
        double[] result=new double[6];m.invoke(f.e,x,result,f.m);return result;
    }
    private static double[] rhs(Fixture f)throws Exception{return rhs(f,storage(f,"state").clone());}
    private static void step(Fixture f,double dt){
        assertTrue(f.e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.m,0));
        assertTrue(f.e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.m,1));
    }
    @Test
    void saturatedFieldProjectsToItsCeilingDuringAnOvershootingStep() throws Exception {
        Ac7bData data = parameters();
        data.setE1(2.0);
        data.setSe1(.2);
        data.setE2(4.0);
        data.setSe2(.45);
        data.setVfemax(2.4); // E1 * (KE + SE1)
        data.setVemin(0.0);
        data.setVrmax(20.0);
        for (double dt : new double[] {.05, .025}) {
            Fixture f = fixture(data, "AC7B", 1.0, true);
            f.e.setVuel(5.0);
            double previous = f.e.getInternalFieldVoltage();
            for (int sample = 0; sample < 60; sample++) {
                step(f, dt);
                double field = f.e.getInternalFieldVoltage();
                assertTrue(field >= previous - TOL, "The field must approach the ceiling monotonically");
                assertTrue(field <= 2.0 + TOL, "The field feedback must stay within its ceiling");
                previous = field;
            }
            assertEquals(2.0, previous, TOL);
            f.e.setVuel(-5.0);
            step(f, dt);
            assertTrue(f.e.getInternalFieldVoltage() < previous);
        }
    }

    @Test void zeroInnerGainInitializesPureProportionalAmplifierBackwards()throws Exception{
        for(double gain:new double[]{.25,-.25}){
            Ac7bData d=parameters();d.setKpa(gain);d.setVrmax(10);d.setVrmin(-10);
            Fixture f=fixture(d,"AC7B",1,true);
            assertEquals(0,storage(f,"state")[PI_I],0);
            assertEquals(1/gain,storage(f,"state")[PID_I],TOL);
            assertEquals(1,f.e.getPiOutput(),TOL);assertArrayEquals(new double[6],rhs(f),TOL);
            step(f,.0033333);assertEquals(1,f.e.getOutput(f.m),TOL);
            assertEquals(10,d.getVrmax(),0);assertEquals(-10,d.getVrmin(),0);
        }
    }
    @Test void inverseInitializationUsesPhysicalPotentialFactorAndAllFeedback()throws Exception{
        Ac7bData d=parameters();d.setKpa(.5);d.setKp(2);d.setKf1(.1);d.setKf2(.2);
        Fixture f=fixture(d,"AC7B",1,true);
        assertEquals(1.3,storage(f,"state")[PID_I],TOL);
        assertEquals(.5,f.e.getPiOutput(),TOL);assertArrayEquals(new double[6],rhs(f),TOL);
    }
    @Test void lowGainKeepsExistingExplicitInitializationAdmissionPolicy()throws Exception{
        for(double gain:new double[]{.25,-.25}){
            Ac7bData d=parameters();d.setKpa(gain);Fixture f=fixture(d,"AC7B",1,true);
            // Existing runtime range admission remains a legacy policy; this
            // assertion does not claim native expansion of invalid source limits.
            assertEquals(Math.max(2,1/gain),f.e.vrmax,0);
            assertEquals(Math.min(-2,1/gain),f.e.vrmin,0);
            assertEquals(2,d.getVrmax(),0);assertEquals(-2,d.getVrmin(),0);
            assertArrayEquals(new double[6],rhs(f),TOL);
        }
    }
    @Test void impossibleZeroInnerGainsDoNotInventAnOutputBias()throws Exception{
        Ac7bData d=parameters();d.setKpa(0);Fixture f=fixture(d,"AC7B",1,false);
        assertFalse(f.e.initStates(f.m.getDStabBus(),f.m));
    }
    @Test void zeroInnerGainsCanRepresentZeroField()throws Exception{
        Ac7bData d=parameters();d.setKpa(0);Fixture f=fixture(d,"AC7B",0,true);
        assertArrayEquals(new double[6],rhs(f),TOL);
    }
    @Test void nativeInteriorIntegralRemainsActive()throws Exception{
        Fixture f=fixture();f.e.setVuel(.1);assertEquals(.3,rhs(f)[PID_I],TOL);
        step(f,.01);assertEquals(1.003,storage(f,"state")[PID_I],TOL);
    }
    @Test void bothOutwardLimitsHoldInPredictorAndCorrector()throws Exception{
        for(double error:new double[]{.75,-2}){
            Fixture f=fixture();f.e.setVuel(error);assertEquals(0,rhs(f)[PID_I],0);
            assertTrue(f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,0));
            assertEquals(1,storage(f,"state")[PID_I],0);assertEquals(1,storage(f,"trial")[PID_I],0);
            assertEquals(0,storage(f,"oldDerivative")[PID_I],0);
            assertTrue(f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,1));
            assertEquals(1,storage(f,"state")[PID_I],0);
        }
    }
    @Test void equalityAtBothPidBoundsHoldsOutwardRates()throws Exception{
        Fixture upper=fixture();upper.e.setVuel(.5);assertEquals(2,upper.e.getPidOutput(),TOL);assertEquals(0,rhs(upper)[PID_I],0);
        Fixture lower=fixture();integral(lower,PID_I,-1);lower.e.setVuel(-.5);
        assertEquals(-2,lower.e.getPidOutput(),TOL);assertEquals(0,rhs(lower)[PID_I],0);
    }
    @Test void syntheticInwardRecoveryAtBothBoundsIsNotNativeTrajectoryQualification()throws Exception{
        for(double sign:new double[]{1,-1}){
            Fixture f=fixture();integral(f,PID_I,3*sign);f.e.setVuel(-.1*sign);
            assertEquals(2*sign,f.e.getPidOutput(),TOL);assertEquals(-.3*sign,rhs(f)[PID_I],TOL);
            assertTrue(f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,0));
            assertEquals(3*sign,storage(f,"state")[PID_I],TOL);
            assertEquals(2.997*sign,storage(f,"trial")[PID_I],TOL);
            assertTrue(f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,1));
            assertEquals(2.997*sign,storage(f,"state")[PID_I],TOL);
        }
    }
    @Test void directionalGateUsesSignedRateRatherThanErrorSign()throws Exception{
        Ac7bData d=parameters();d.setKir(-3);Fixture f=fixture(d,"AC7B",1,true);
        f.e.setVuel(.75);assertEquals(2,f.e.getPidOutput(),TOL);assertEquals(-2.25,rhs(f)[PID_I],TOL);
    }
    @Test void innerAmplifierOnlyClipDoesNotFreezeOuterIntegral()throws Exception{
        Ac7bData d=parameters();d.setVamax(1.1);Fixture f=fixture(d,"AC7B",1,true);
        f.e.setVuel(.1);assertEquals(1.2,f.e.getPidOutput(),TOL);assertEquals(1.1,f.e.getPiOutput(),TOL);
        assertEquals(.3,rhs(f)[PID_I],TOL);step(f,.01);assertEquals(1.003,storage(f,"state")[PID_I],TOL);
    }
    @Test void zeroInnerGainBiasIsAbsentAfterEitherOuterClip()throws Exception{
        Fixture f=fixture();f.e.setVuel(2);assertEquals(2,f.e.getPiOutput(),TOL);
        f.e.setVuel(-2);assertEquals(-2,f.e.getPiOutput(),TOL);assertEquals(0,storage(f,"state")[PI_I],0);
    }
    @Test void reorderedDialectRetainsInnerTrackingAtBothAmplifierLimits()throws Exception{
        for(double sign:new double[]{1,-1}){
            Ac7bData d=parameters();d.setKia(4);d.setVamax(1.1);d.setVamin(-1.1);
            Fixture f=fixture(d,"ESAC7B",1,true);integral(f,PI_I,2*sign);f.e.setVuel(.1*sign);
            assertEquals(-8*sign,rhs(f)[PI_I],TOL);assertEquals(.3*sign,rhs(f)[PID_I],TOL);
        }
    }
    @Test void nativeZeroKlBypassesNegativeRegulatorLimit() throws Exception {
        Ac7bData d = parameters();
        d.setKl(0.0);
        Fixture nativeModel = fixture(d, "AC7B", 1.0, true);
        nativeModel.e.setVuel(-2.0);
        assertEquals(-2.0, nativeModel.e.getRegulatorOutput(), TOL);
        assertEquals(-6.0, rhs(nativeModel)[VE], TOL);

        d.setKl(.2);
        Fixture limited = fixture(d, "AC7B", 1.0, true);
        limited.e.setVuel(-2.0);
        assertEquals(-.2, limited.e.getRegulatorOutput(), TOL);

        d.setKl(0.0);
        Fixture reordered = fixture(d, "ESAC7B", 1.0, true);
        reordered.e.setVuel(-2.0);
        assertEquals(0.0, reordered.e.getRegulatorOutput(), TOL);
    }

    @Test void nativeInnerIntegralHoldsAtEitherOutwardAmplifierLimit()throws Exception{
        for(double sign:new double[]{1,-1}){
            Ac7bData d=parameters();d.setKia(4);d.setVamax(1.1);d.setVamin(-1.1);
            Fixture f=fixture(d,"AC7B",1,true);integral(f,PI_I,2*sign);f.e.setVuel(.1*sign);
            assertEquals(1.1*sign,f.e.getPiOutput(),TOL);
            assertEquals(0,rhs(f)[PI_I],0,"Clipping cannot back-calculate the integral");
            assertEquals(.3*sign,rhs(f)[PID_I],TOL,"Inner clipping does not freeze outer PI");
        }
    }
    @Test void nativeInnerIntegralReleasesInwardWhileAmplifierStillClips()throws Exception{
        for(double sign:new double[]{1,-1}){
            Ac7bData d=parameters();d.setKia(4);d.setVamax(1.1);d.setVamin(-1.1);
            Fixture f=fixture(d,"AC7B",1,true);integral(f,PI_I,2*sign);f.e.setVuel(-.1*sign);
            assertEquals(1.1*sign,f.e.getPiOutput(),TOL);
            assertEquals(-.8*sign,rhs(f)[PI_I],TOL,"The ordinary inward PI rate remains active");
        }
    }
    @Test void nativeInnerIntegralIncludesExactLimitsAndSignedGains()throws Exception{
        for(double sign:new double[]{1,-1}){
            Ac7bData d=parameters();d.setKia(4);d.setVamax(1);d.setVamin(-1);
            Fixture f=fixture(d,"AC7B",1,true);integral(f,PI_I,.5*sign);f.e.setVuel(.25*sign);
            assertEquals(sign,f.e.getPiOutput(),0);assertEquals(0,rhs(f)[PI_I],0);
            // Reversing integral gain makes the same positive error an inward
            // rate; error-based clamping would incorrectly hold it.
            f.e.kia=-4;assertEquals(-2*sign,rhs(f)[PI_I],TOL);
        }
    }
    @Test void nativeInnerIntegralDoesNotAcquireTrackingBiasDuringAcceptedSteps()throws Exception{
        Ac7bData d=parameters();d.setKia(4);d.setVamax(1.1);d.setVamin(-1.1);
        Fixture f=fixture(d,"AC7B",1,true);integral(f,PI_I,2);f.e.setVuel(.1);
        step(f,.001);assertEquals(2,storage(f,"state")[PI_I],0);
        f.e.setVuel(-.1);step(f,.001);
        assertTrue(storage(f,"state")[PI_I]<2,"Both stages permit inward recovery");
    }
    @Test void legacyReorderedDialectRetainsBothInitializationAndOuterTracking()throws Exception{
        Fixture f=fixture(parameters(),"ESAC7B",1,true);
        assertEquals(0,storage(f,"state")[PID_I],0);assertEquals(1,storage(f,"state")[PI_I],0);
        f.e.setVuel(2);assertEquals(-6,rhs(f)[PID_I],TOL);
        Fixture nativeModel=fixture();nativeModel.e.setVuel(2);assertEquals(0,rhs(nativeModel)[PID_I],0);
    }
    @Test void correctorUsesSavedPredictorRateAcrossLimiterRelease()throws Exception{
        Fixture f=fixture();f.e.setVuel(.75);f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,0);
        assertEquals(0,storage(f,"oldDerivative")[PID_I],0);f.e.setVuel(.1);
        f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,1);
        assertEquals(1.0015,storage(f,"state")[PID_I],TOL);
    }
    @Test void zeroDurationPreservesAcceptedTrialRateAndOutputBetweenStages()throws Exception{
        Fixture f=fixture();f.e.setVuel(.1);f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,0);
        double[] state=storage(f,"state").clone(),trial=storage(f,"trial").clone(),old=storage(f,"oldDerivative").clone();double output=f.e.outputSignal;
        for(int stage:new int[]{0,1})assertTrue(f.e.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,f.m,stage));
        assertArrayEquals(state,storage(f,"state"),0);assertArrayEquals(trial,storage(f,"trial"),0);assertArrayEquals(old,storage(f,"oldDerivative"),0);assertEquals(output,f.e.outputSignal,0);
        f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,1);assertEquals(1.003,storage(f,"state")[PID_I],TOL);
    }
    @Test void rhsAndObservationsDoNotMutateStateOrHistories()throws Exception{
        Fixture f=fixture();f.e.setVuel(.1);f.e.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,f.m,0);
        double[] state=storage(f,"state").clone(),trial=storage(f,"trial").clone(),old=storage(f,"oldDerivative").clone(),input=trial.clone();
        double[] first=rhs(f,input),second=rhs(f,input);assertArrayEquals(first,second,0);assertArrayEquals(trial,input,0);
        f.e.getPidOutput();f.e.getPiOutput();f.e.getRegulatorOutput();f.e.getOutput(f.m);
        assertArrayEquals(state,storage(f,"state"),0);assertArrayEquals(trial,storage(f,"trial"),0);assertArrayEquals(old,storage(f,"oldDerivative"),0);
    }
    @Test void existingDynamicFieldLimitsStillConstrainBothStages()throws Exception{
        for(double error:new double[]{2,-2}){
            Ac7bData d=parameters();d.setVfemax(1.05);d.setVemin(.5);Fixture f=fixture(d,"AC7B",1,true);
            f.e.setVuel(error);double expected=error>0?1.05:.5;
            f.e.nextStep(1,DynamicSimuMethod.MODIFIED_EULER,f.m,0);assertEquals(expected,storage(f,"trial")[VE],TOL);
            f.e.nextStep(1,DynamicSimuMethod.MODIFIED_EULER,f.m,1);
            assertTrue(storage(f,"state")[VE]>=.5&&storage(f,"state")[VE]<=1.05);
            assertEquals(1.05,f.e.vfemax,0);assertEquals(.5,f.e.vemin,0);
        }
    }
}
