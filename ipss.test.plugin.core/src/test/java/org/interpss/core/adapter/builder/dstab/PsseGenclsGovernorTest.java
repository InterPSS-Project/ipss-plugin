package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.gov.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class PsseGenclsGovernorTest extends CorePluginTestSetup {
    @Test void attachedGovernorInitializesAndPreservesEquilibrium(@TempDir Path directory) throws Exception {
        var m=machine(directory);assertTrue(m.initStates(m.getDStabBus()));
        var gov=assertInstanceOf(PsseTGov1SteamTurGovernor.class,m.getGovernor());
        double initial=m.getPm();assertEquals(initial,gov.getOutput(m),1e-12);
        for(int i=0;i<20;i++){assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));}
        assertEquals(initial,m.getPm(),1e-12);assertEquals(1,m.getSpeed(),1e-12);
    }
    @Test void referenceStepAdvancesGovernorAndMechanicalPowerInsteadOfSilentlyIgnoringIt(@TempDir Path directory) throws Exception {
        var m=machine(directory);assertTrue(m.initStates(m.getDStabBus()));
        var gov=(PsseTGov1SteamTurGovernor)m.getGovernor();double initial=m.getPm();gov.setRefPoint(gov.getRefPoint()-.005);
        for(int i=0;i<30;i++){assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));}
        assertTrue(m.getPm()<initial-.002);assertTrue(m.getSpeed()<1);assertEquals(gov.getOutput(m),m.getPm(),0);
    }
    @Test void inactiveGovernorDoesNotInitializeOrPerturbTheClassicalMachine(@TempDir Path directory) throws Exception {
        var m=machine(directory);m.getGovernor().setStatus(false);assertTrue(m.initStates(m.getDStabBus()));double initial=m.getPm();
        assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(m.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        assertEquals(initial,m.getPm(),0);assertEquals(1,m.getSpeed(),0);
    }
    private static Machine machine(Path directory) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();Path dyr=directory.resolve("governed.dyr");
        Files.writeString(dyr,"1 'GENCLS' 1 3 0 /\n1 'TGOV1' 1 .05 .1 1.5 0 0 .1 0 /\n");
        new PSSEDStabDirectParser(b).setStrictImport(true).parseDynFile(dyr.toString());
        return (Machine)((DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }
}
