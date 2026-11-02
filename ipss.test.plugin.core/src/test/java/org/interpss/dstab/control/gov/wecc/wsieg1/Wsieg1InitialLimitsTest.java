package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Initial valve bounds preserve entered physics and expose non-equilibrium residuals. */
public class Wsieg1InitialLimitsTest {
    private static final double H=.0005;
    @BeforeAll static void setup(){IpssCorePlugin.init();}
    static Wsieg1Governor model(Consumer<double[]> edit)throws Exception {
        var root=Wsieg1SharedSmibTest.CASE;
        var net=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("wsieg1-curve.dyr").toString()).getDStabilityNet();
        var m=net.getMachine("Bus1-mach1");var old=(Wsieg1Governor)m.getGovernor();double[] c=old.getConstants();edit.accept(c);
        m.getControllerList().remove(old);var g=new Wsieg1Governor("initial-limits",c);g.setMachine(m);m.setPm(.5);m.setPe(.5);m.setSpeed(1.);g.configureIntegrationStep(H);
        assertTrue(g.initStates(m.getDStabBus(),m));return g;
    }

    @Test void ordinaryBoundsClipTheValveAndExposeTheTurbineInitializationResidual()throws Exception {
        for(boolean upper:new boolean[]{false,true}) {
            var g=model(c->{c[6]=upper?.2:1.2;c[7]=upper?0.:.7;});
            double gate=upper?.2:.7,output=upper?.201:.699;
            assertEquals(upper?.2:1.2,g.getEffectiveMaximum(),0.);assertEquals(upper?0.:.7,g.getEffectiveMinimum(),0.);
            assertEquals(gate,g.getValvePosition(),0.);assertEquals(output,g.getValveOutput(),1e-15);assertEquals(gate,g.getBacklashInputHistory(),0.);
            assertEquals(.4+(.5-.3)/1.5,g.getInitialReference(),1e-15);
            assertEquals(g.getInitialReference()-gate,g.getInitialValveLimitMismatch(),0.);
            var snapshot=g.getStateDerivativeSnapshot();assertEquals(Stage.INITIALIZATION,snapshot.stage());
            assertEquals((g.valvePower(output)-.5)/.1,snapshot.rates().get("Turbine 1"),1e-14);
            assertTrue(Math.abs(snapshot.rates().get("Turbine 1"))>2.);
            assertEquals(0.,snapshot.rates().get("Valve"),0.);assertEquals(.5,g.getOutput(g.getMachine()),1e-15);
            assertEquals(upper?.2:1.2,g.getConstants()[6],0.);assertEquals(upper?0.:.7,g.getConstants()[7],0.);
        }
    }

    @Test void iblockUsesInitialValvePositionRatherThanMechanicalPower()throws Exception {
        for(int block:new int[]{1,2,3}) {
            var g=model(c->{c[33]=block;c[7]=0.;if(block>=2)c[6]=0.;});double gate=g.getInitialReference();
            assertTrue(Math.abs(gate-.5)>.03);
            assertEquals(block==1||block==3?gate:0.,g.getEffectiveMinimum(),0.);
            assertEquals(block==2||block==3?gate:1.2,g.getEffectiveMaximum(),0.);
            assertEquals(0.,g.getInitialValveLimitMismatch(),0.);
            for(double rate:g.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,1e-14);
        }
    }

    @Test void reversedEffectiveBoundsRetainBothValuesAndTheObservedInitialProjection()throws Exception {
        for(boolean lowerBlock:new boolean[]{false,true}) {
            var g=model(c->{c[33]=lowerBlock?1:2;c[6]=lowerBlock?.2:0.;c[7]=lowerBlock?0.:.7;});
            double upper=lowerBlock?.2:g.getInitialReference(),lower=lowerBlock?g.getInitialReference():.7;
            assertTrue(lower>upper);assertEquals(upper,g.getEffectiveMaximum(),0.);assertEquals(lower,g.getEffectiveMinimum(),0.);
            assertEquals(upper,g.getValvePosition(),0.);
            // This establishes initialization only. The independent runtime
            // alternates between reversed bounds after a command reversal;
            // that behavior remains covered by the failing coupled gate.
        }
    }

    @Test void bypassedTurbinesExposeTheClippedAlgebraicPowerImmediately()throws Exception {
        for(boolean upper:new boolean[]{false,true}) {
            var g=model(c->{c[6]=upper?.2:1.2;c[7]=upper?0.:.7;for(int index:new int[]{8,11,14,17})c[index]=0.;});
            double power=g.valvePower(g.getValveOutput());double[] state=new double[6];g.readStaggeredState(state,0);
            for(int i=2;i<6;i++)assertEquals(power,state[i],0.);
            assertEquals(power,g.getOutput(g.getMachine()),1e-15);assertTrue(Math.abs(power-.5)>.2);
            for(double rate:g.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,0.);
            assertTrue(Math.abs(g.getInitialValveLimitMismatch())>.1,"An algebraic power jump is not hidden by zero turbine derivatives");
        }
    }

    @Test void initialMismatchIsStableDuringStagesAndRefreshesOnReinitialization()throws Exception {
        var g=model(c->c[6]=.2);double mismatch=g.getInitialValveLimitMismatch();
        g.setRefPoint(.1);var history=g.getIntegrationHistories();double[] values=new double[6],rates=new double[6];
        g.readStaggeredState(values,0);g.evaluateStaggered(H,rates,0);values[1]=.1;g.applyStaggeredState(values,0,H);
        assertEquals(history,g.getIntegrationHistories());assertEquals(mismatch,g.getInitialValveLimitMismatch(),0.);
        g.completeStaggeredStep(H);assertEquals(mismatch,g.getInitialValveLimitMismatch(),0.);
        g.getMachine().setPm(.1);assertTrue(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));assertEquals(0.,g.getInitialValveLimitMismatch(),0.);
        g.getMachine().setPm(Double.NaN);assertFalse(g.initStates(g.getMachine().getDStabBus(),g.getMachine()));assertThrows(IllegalStateException.class,g::getInitialValveLimitMismatch);
    }

    @Test void sharedModifiedEulerPreservesTheSameProjectionAndHistoryAsOrdinaryUpdates()throws Exception {
        for(boolean upper:new boolean[]{false,true}) {
            var a=model(c->{c[6]=upper?.2:1.2;c[7]=upper?0.:.7;});var b=model(c->{c[6]=upper?.2:1.2;c[7]=upper?0.:.7;});
            double[] start=new double[6],trial=new double[6],d0=new double[6],d1=new double[6];
            for(int tick=0;tick<400;tick++) {
                double ref=tick<200?.1:.9,speed=1.+.0004*Math.sin(tick*.04);a.setRefPoint(ref);b.setRefPoint(ref);a.getMachine().setSpeed(speed);b.getMachine().setSpeed(speed);
                a.readStaggeredState(start,0);a.evaluateStaggered(H,d0,0);for(int i=0;i<6;i++)trial[i]=start[i]+H*d0[i];a.applyStaggeredState(trial,0,H);a.evaluateStaggered(H,d1,0);
                for(int i=0;i<6;i++)trial[i]=start[i]+.5*H*(d0[i]+d1[i]);a.applyStaggeredState(trial,0,H);a.completeStaggeredStep(H);
                assertTrue(b.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,b.getMachine(),0));assertTrue(b.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,b.getMachine(),1));
                assertEquals(b.getIntegrationStates(),a.getIntegrationStates());assertEquals(b.getIntegrationHistories(),a.getIntegrationHistories());assertEquals(b.getOutput(b.getMachine()),a.getOutput(a.getMachine()),0.);
            }
        }
    }
}
