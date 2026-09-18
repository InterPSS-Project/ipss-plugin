package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bData;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Equation-level oracle translated from ANDES 2.0.0 AC8B/PIDTrackAW. */
public class Ac8bAndesEquationConformanceTest extends CorePluginTestSetup {
    private static final int VE=0, VSENSE=1, PID_I=2, PID_D=3, VR=4;

    @Test
    void fiveStateUnsaturatedTrajectoryMatchesAndesEquations() throws Exception {
        Fixture fixture = fixture(99.0, -99.0, 99.0);
        Ac8bExciter exciter = fixture.exciter;
        Machine machine = fixture.machine;
        exciter.setVuel(0.1);

        double[] x = {1.2, 1.0, 0.8, 0.0, 1.2};
        double dt = 0.00025;
        double maxError = 0.0;
        for (int step=0; step<2000; step++) {
            double[] d0 = derivatives(x, 0.1, 99.0, -99.0);
            double[] predicted = add(x, d0, dt);
            double[] d1 = derivatives(predicted, 0.1, 99.0, -99.0);
            for (int i=0; i<x.length; i++) x[i] += 0.5*(d0[i]+d1[i])*dt;

            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maxError = Math.max(maxError, Math.abs(exciter.getInternalFieldVoltage()-x[VE]));
            maxError = Math.max(maxError, Math.abs(exciter.getSensedVoltage()-x[VSENSE]));
            maxError = Math.max(maxError, Math.abs(exciter.getRegulatorOutput()-x[VR]));
        }
        assertTrue(maxError < 1.0e-10, "AC8B five-state max error="+maxError);
    }

    @Test
    void trackingAntiWindupMatchesAndesPidTrackAw() throws Exception {
        Fixture fixture = fixture(0.9, -0.9, 99.0);
        fixture.exciter.setVuel(0.4);
        double[] x = {1.2, 1.0, 0.8, 0.0, 1.2};
        double dt=0.0001;
        for (int step=0; step<500; step++) {
            double[] d0=derivatives(x,0.4,0.9,-0.9);
            double[] predicted=add(x,d0,dt);
            double[] d1=derivatives(predicted,0.4,0.9,-0.9);
            for(int i=0;i<x.length;i++) x[i]+=0.5*(d0[i]+d1[i])*dt;
            fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0);
            fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1);
        }
        assertEquals(clamp(pidUnlimited(x,0.4),-0.9,0.9),
                fixture.exciter.getPidOutput(),1.0e-10);
        assertEquals(x[VR],fixture.exciter.getRegulatorOutput(),1.0e-10);
    }

    @Test
    void powerWorldTimeCorrectionsAndFieldLimitsAreAppliedAtRuntime() throws Exception {
        Fixture fixture=fixture(99,-99,99);
        Ac8bData data=fixture.exciter.getData();
        data.setTr(.004); data.setTdr(.006); data.setTa(.004); data.setTe(.005);
        data.setVfemax(.8);
        fixture.machine.setEfd(.5);
        fixture.exciter.configureIntegrationStep(0.01,2.0);
        assertTrue(fixture.exciter.initStates(fixture.machine.getDStabBus(),fixture.machine));
        assertEquals(0.0,fixture.exciter.tr,1e-12);
        assertEquals(0.0,fixture.exciter.tdr,1e-12);
        assertEquals(0.0,fixture.exciter.ta,1e-12);
        assertEquals(0.02,fixture.exciter.te,1e-12);
        fixture.exciter.setVuel(2.0);
        for(int i=0;i<4000;i++){
            fixture.exciter.nextStep(0.00025,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0);
            fixture.exciter.nextStep(0.00025,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1);
        }
        assertTrue(fixture.exciter.getInternalFieldVoltage() <= 0.8 + 1e-10);
    }

    private static Fixture fixture(double pidMax,double pidMin,double vfeMax) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Ac8bData d=new Ac8bData();
        d.setTr(.02); d.setKpr(2); d.setKir(3); d.setKdr(.4); d.setTdr(.03);
        d.setVpidmax(pidMax); d.setVpidmin(pidMin); d.setVrmax(99); d.setVrmin(-99);
        d.setVfemax(vfeMax); d.setVemin(-99); d.setTa(.3); d.setKa(1.5); d.setTe(.4);
        d.setKc(0); d.setKd(0); d.setKe(1); d.setE1(0); d.setSe1(0); d.setE2(0); d.setSe2(0);
        Ac8bExciter exciter=builder.addExcAc8b("Bus1","1",d);
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2); machine.getDStabBus().setVoltage(new org.apache.commons.math3.complex.Complex(1,0));
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        return new Fixture(exciter,machine);
    }

    private static double[] derivatives(double[] x,double step,double pidMax,double pidMin){
        double error=step;
        double derivative=.4*(error-x[PID_D])/.03;
        double unlimited=2*error+x[PID_I]+derivative;
        double pid=clamp(unlimited,pidMin,pidMax);
        return new double[]{(x[VR]-x[VE])/.4,0,
                3*(error-2*(unlimited-pid)),(error-x[PID_D])/.03,
                (1.5*pid-x[VR])/.3};
    }

    private static double pidUnlimited(double[] x,double step){
        return 2*step+x[PID_I]+.4*(step-x[PID_D])/.03;
    }
    private static double[] add(double[] x,double[] d,double dt){
        double[] y=new double[x.length]; for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt; return y;
    }
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    private record Fixture(Ac8bExciter exciter,Machine machine){}
}
