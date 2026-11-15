package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Cimtr4Machine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The same induction circuit admits motor, synchronous, and generator points. */
class CimtrOperatingPointBoundaryTest {
    @TempDir Path temporary;

    @Test void preservesSignedPowerAndEquilibriumOnBothSlipBranches() throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path fixture=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33","SMIB");
        for(double power:new double[]{-35,0,35}) {
            String text=Files.readString(fixture.resolve("SMIB_v33_cimtr4.raw"))
                    .replace("1,'1',-35.000,","1,'1',"+power+",");
            Path raw=temporary.resolve("point"+power+".raw");Files.writeString(raw,text);
            var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),fixture.resolve("SMIB_v33_cimtr4.dyr").toString());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());
            var machine=(Cimtr4Machine)context.getDStabilityNet().getMachine("Bus1-mach1");
            var bus=machine.getDStabBus();double speed=machine.getSpeed(),voltage=bus.getVoltageMag();
            if(power>0)assertTrue(speed>1);if(power<0)assertTrue(speed<1);
            var injected=bus.getVoltage().multiply(machine.getIgen().subtract(machine.getYgen().multiply(bus.getVoltage())).conjugate());
            assertEquals(power/100,injected.getReal(),1e-8,"Network injection must retain PG");
            assertEquals(machine.getParentGen().getGen().getImaginary(),injected.getImaginary(),1e-8);
            for(int i=0;i<20;i++)assertTrue(algorithm.solveDEqnStep(true));
            assertEquals(speed,machine.getSpeed(),1e-8);assertEquals(voltage,bus.getVoltageMag(),1e-6);
        }
    }
}
