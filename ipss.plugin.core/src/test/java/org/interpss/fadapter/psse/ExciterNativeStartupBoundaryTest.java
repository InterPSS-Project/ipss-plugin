package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native record mapping and the documented zero potential-multiplier selector. */
class ExciterNativeStartupBoundaryTest {
    @TempDir Path temporary;
    private com.interpss.simu.SimuContext load(String exciter) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path fixture=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33","SMIB");
        var records=Files.readAllLines(fixture.resolve("SMIB_v33_genrou_ac8b.dyr"));
        var source=new StringBuilder();for(String line:records)source.append(line.contains("'AC8B'")?exciter:line).append('\n');
        Path dyr=temporary.resolve("exciter.dyr");Files.writeString(dyr,source);
        return new PSSEMultiFileLoader().loadDStab(fixture.resolve("SMIB_v33.raw").toString(),dyr.toString());
    }
    @Test void ac8bUsesAllTwentyOneNativePositions() throws Exception {
        var context=load("1 'AC8B' 1 .02 20 30 4 .05 99 -98 2 .06 15 -14 .2 .3 1 .5 97 .01 3.5 .07 4.5 .7 /");
        var exciter=(Ac8bExciter)context.getDStabilityNet().getMachine("Bus1-mach1").getExciter();var d=exciter.getData();
        assertArrayEquals(new double[]{.02,20,30,4,.05,99,-98,2,.06,15,-14,.2,.3,1,.5,97,.01,3.5,.07,4.5,.7},
                new double[]{d.getTr(),d.getKpr(),d.getKir(),d.getKdr(),d.getTdr(),d.getVpidmax(),d.getVpidmin(),
                        d.getKa(),d.getTa(),d.getVrmax(),d.getVrmin(),d.getKc(),d.getKd(),d.getKe(),d.getTe(),
                        d.getVfemax(),d.getVemin(),d.getE1(),d.getSe1(),d.getE2(),d.getSe2()},0);
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        assertTrue(exciter.isActive());
    }
    @Test void ac7bZeroKpBypassesPotentialMultiplicationAtInitializationAndRuntime() throws Exception {
        var context=load("1 'AC7B' 1 .02 10 50 3 .1 15 -15 5 4 20 0 0 999 0 .7 0 1 .2 1.5 .9 .95 99 0 10 .04 15 .015 /");
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var exciter=(Ac7bExciter)machine.getExciter();assertEquals(0,exciter.getData().getKp(),0);assertTrue(exciter.isActive());
        double field=machine.getEfd(),voltage=machine.getDStabBus().getVoltageMag();
        assertEquals(field,exciter.getOutput(machine),1e-10);
        for(int i=0;i<20;i++)assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(field,machine.getEfd(),1e-6);assertEquals(voltage,machine.getDStabBus().getVoltageMag(),1e-6);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void staticSt4FeedbackUsesItsAlgebraicRootWithoutMovingIntegralStates(boolean prepared) throws Exception {
        var context=load("1 'ESST4B' 1 0 20 5 9 -9 .1 1.5 3 8 -8 2 1 0 10 .1 0 0 /");
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        ((org.interpss.fadapter.psse.PsseDStabSolver)algorithm.getSolver()).setControllerRuntimePreparationEnabled(prepared);
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var exciter=(org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter)machine.getExciter();
        var blocks=new java.util.HashMap<String,Object>();for(var wrapper:exciter.getFieldWrapperList())blocks.put(wrapper.getFieldName(),wrapper.getField());
        var vm=(com.interpss.dstab.controller.cml.field.block.PIControlBlock)blocks.get("vmPIBlock");
        var ta=(com.interpss.dstab.controller.cml.field.block.DelayControlBlock)blocks.get("taDelayBlock");
        var bridge=(com.interpss.dstab.controller.cml.field.ICMLStaticBlock)blocks.get("customBlock");
        double state=vm.getStateX(),derivative=vm.getDX_dt();
        for(int i=0;i<20;i++)assertTrue(Double.isFinite(exciter.getOutput(machine)));
        assertEquals(state,vm.getStateX(),0);assertEquals(derivative,vm.getDX_dt(),0);
        double factor=bridge.getY()/bridge.getU();assertTrue(factor*exciter.Kpm*exciter.kg>1);
        double expected=factor*(state+exciter.Kpm*ta.getY())/(1+factor*exciter.Kpm*exciter.kg);
        assertEquals(expected,exciter.getOutput(machine),1e-10);
        double initial=machine.getEfd();for(int i=0;i<20;i++)assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(initial,machine.getEfd(),1e-6);
    }
}
