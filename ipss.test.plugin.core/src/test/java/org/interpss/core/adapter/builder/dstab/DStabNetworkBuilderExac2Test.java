package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderExac2Test extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    @TempDir Path tempDir;

    @Test
    void parsesTexas7kRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac2.dyr");
        Files.writeString(dyr,"1 'EXAC2' '1' .066667 1 1 1000 .066667 9.8378 -9.8378 1 29.0988 -29.098801 1.3 4 0 .49 1 .1 1.6 1 10 3.0789 .0084 4.1053 .0189 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setEfd(1.2);
        Exac2Exciter exc=(Exac2Exciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(1000,exc.getData().getKa(),TOL);
        assertEquals(9.8378,exc.getData().getVamax(),TOL);
        assertEquals(4,exc.getData().getKl(),TOL);
        assertEquals(10,exc.getData().getVlr(),TOL);
        assertEquals(.0189,exc.getData().getSe2(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);

        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(Double.isFinite(exc.getOutput(machine)));
        assertNotEquals(initial,exc.getOutput(machine),1e-4);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void unloadedControllerRaisesFieldVoltageAfterTerminalVoltageDrop() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac2-unloaded.dyr");
        Files.writeString(dyr,"1 'EXAC2' '1' 0 1 1 100 .02 10 -10 1 20 -20 1 4 0 .1 1 0 0 1 10 0 0 1 0 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        Exac2Exciter exc=(Exac2Exciter)machine.getExciter();
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++) {
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        double regulator=exc.getFieldVaule("this.regulator.y");
        double lowGate=exc.getFieldVaule("this.lowValueGate.y");
        double vr=exc.getFieldVaule("this.vrLimiter.y");
        double ve=exc.getFieldVaule("this.fieldIntegrator.y");
        double vfe=exc.getFieldVaule("this.vfe.y");
        double leadLag=exc.getFieldVaule("this.leadLag.y");
        double washout=exc.getFieldVaule("this.washout.y");
        assertTrue(exc.getOutput(machine)>initial,()->"initial="+initial+", final="+exc.getOutput(machine)
                +", ref="+exc.getRefPoint()+", leadLag="+leadLag+", washout="+washout
                +", regulator="+regulator+", lowGate="+lowGate+", vr="+vr+", ve="+ve+", vfe="+vfe);
    }

    @Test
    void lowValueGateAndEffectiveLimitMatchAndesEquations() {
        assertEquals(1.0,Exac2Exciter.lowValueGate(5,1,.5,4,1.25),TOL);
        assertEquals(1.25,Exac2Exciter.effectiveVlr(0,1,4,1),TOL);
        assertEquals(3.0,Exac2Exciter.effectiveVlr(3,1,4,1),TOL);
    }
}
