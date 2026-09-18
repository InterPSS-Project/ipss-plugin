package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bData;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Equation oracle translated from Dynawo BaseAc7 and AcRotatingExciter. */
public class Ac7bDynawoEquationConformanceTest extends CorePluginTestSetup {
    private static final int VE=0, VSENSE=1, PID_I=2, PID_D=3, PI_I=4, RATE=5;

    @Test
    void sixStateUnsaturatedTrajectoryMatchesDynawoEquations() throws Exception {
        Fixture fixture = fixture();
        fixture.exciter.setVuel(.1);
        double[] x = {1.2, 1.0, .6, 0.0, 1.0, 1.2};
        double dt = .0001;
        double maxError = 0.0;
        for (int step=0; step<2000; step++) {
            double[] d0=derivatives(x,.1);
            double[] predicted=add(x,d0,dt);
            double[] d1=derivatives(predicted,.1);
            for(int i=0;i<x.length;i++) x[i]+=0.5*(d0[i]+d1[i])*dt;
            fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0);
            fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1);
            maxError=Math.max(maxError,Math.abs(x[VE]-fixture.exciter.getInternalFieldVoltage()));
            maxError=Math.max(maxError,Math.abs(x[VSENSE]-fixture.exciter.getSensedVoltage()));
            maxError=Math.max(maxError,Math.abs(piOutput(x,.1)-fixture.exciter.getPiOutput()));
        }
        assertTrue(maxError < 1.0e-10, "AC7B six-state max error="+maxError);
    }

    @Test
    void appliesPowerWorldTimeCorrectionsAndDynamicLimitExpansion() throws Exception {
        Fixture fixture=fixture(false);
        Ac7bData data=fixture.exciter.getData();
        data.setTr(.004); data.setTdr(.006); data.setTf(.005); data.setTe(.005);
        data.setVrmax(-2); data.setVrmin(-3); data.setVamax(-2); data.setVamin(-3);
        data.setVfemax(.5); data.setVemin(2);
        fixture.exciter.configureIntegrationStep(.01,2);
        assertTrue(fixture.exciter.initStates(fixture.machine.getDStabBus(),fixture.machine));
        assertEquals(0.0,fixture.exciter.tr,1e-12);
        assertEquals(0.0,fixture.exciter.tdr,1e-12);
        assertEquals(.02,fixture.exciter.tf,1e-12);
        assertEquals(.02,fixture.exciter.te,1e-12);
        assertTrue(fixture.exciter.vrmax >= .6);
        assertTrue(fixture.exciter.vamax >= 1.0);
        assertTrue(fixture.exciter.vfemax >= 1.2);
        assertTrue(fixture.exciter.vemin <= 1.2);
    }

    @Test
    void algebraicFieldCurrentLimiterIsOutsideTheDynamicStates() throws Exception {
        Fixture fixture=fixture();
        fixture.exciter.getData().setKl(.5);
        assertTrue(fixture.exciter.initStates(fixture.machine.getDStabBus(),fixture.machine));
        fixture.exciter.setVuel(-10);
        fixture.exciter.nextStep(.0001,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0);
        assertTrue(fixture.exciter.getRegulatorOutput()
                >= -.5*fixture.exciter.getFieldCurrentSignal()-1e-12);
    }

    private static Fixture fixture() throws Exception { return fixture(true); }
    private static Fixture fixture(boolean initialize) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Ac7bData d=new Ac7bData();
        d.setTr(.02); d.setKpr(2); d.setKir(3); d.setKdr(.4); d.setTdr(.03);
        d.setVrmax(99); d.setVrmin(-99); d.setKpa(1.5); d.setKia(2);
        d.setVamax(99); d.setVamin(-99); d.setKp(1.2); d.setKl(1);
        d.setKf1(.2); d.setKf2(.3); d.setKf3(.4); d.setTf(.05);
        d.setKc(0); d.setKd(0); d.setKe(1); d.setTe(.4);
        d.setVfemax(99); d.setVemin(-99);
        Ac7bExciter exciter=builder.addExcAc7b("Bus1","1","AC7B",d);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        machine.getDStabBus().setVoltage(new org.apache.commons.math3.complex.Complex(1,0));
        if(initialize) assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        return new Fixture(exciter,machine);
    }

    private static double[] derivatives(double[] x,double step){
        double vfe=x[VE];
        double rate=.4*(vfe-x[RATE])/.05;
        double error=step-rate;
        double derivative=.4*(error-x[PID_D])/.03;
        double pid=2*error+x[PID_I]+derivative;
        double feedback=.5*vfe;
        double piError=pid-feedback;
        double pi=1.5*piError+x[PI_I];
        double regulator=1.2*pi;
        return new double[]{(regulator-vfe)/.4,0,3*error,(error-x[PID_D])/.03,
                2*piError,(vfe-x[RATE])/.05};
    }
    private static double piOutput(double[] x,double step){
        double vfe=x[VE];
        double rate=.4*(vfe-x[RATE])/.05;
        double error=step-rate;
        double pid=2*error+x[PID_I]+.4*(error-x[PID_D])/.03;
        return 1.5*(pid-.5*vfe)+x[PI_I];
    }
    private static double[] add(double[] x,double[] d,double dt){
        double[] y=new double[x.length]; for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt; return y;
    }
    private record Fixture(Ac7bExciter exciter,Machine machine){}
}
