package org.interpss.dstab.renewable;
import com.interpss.dstab.renewable.*;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact battery/auxiliary attachment and a shared-solver flat/disturbed smoke. */
class ReeccAuxiliaryImportTest {
    private static final Path DATA=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33");
    @TempDir Path temp;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void batteryHostOwnsBothAuxiliaryStatesAndTheSharedPlant(boolean staggered) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        String source=Files.readString(DATA.resolve("renewable/regca_reecc1_bus1062.dyr"));
        source=source.replace("3 'GENCLS'","2 'GENCLS'");
        Path file=temp.resolve("battery-aux.dyr");Files.writeString(file,source+"""
                1 'USRMDL' '1' 'REAX4BU1' 107 0 1 7 2 4 1 .02 .8 0 99 -99 99 -99 /
                1 'USRBUS' 'PLNTBU1' 504 0 7 28 7 15
                1 1 2 '1' 0 1 1 .04 1 .1 0 .02 .7 0 0 .01 1 -1 0 0 1 -1
                0 .5 .04 -.002 .003 1 -1 1 -1 .02 20 20 100 /
                """);
        var loader=new PSSEMultiFileLoader().setDynamicModelParameterOverrides(r->
                r.canonicalModelName().equals("REECC1")?java.util.Map.of(1,"0"):java.util.Map.of());
        var context=loader.loadDStab(DATA.resolve("SMIB/SMIB_v33_wt3g2.raw").toString(),file.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var gen=(com.interpss.dstab.DStabGen)context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
        var battery=assertInstanceOf(Reecc1Model.class,((Regca1Model)gen.getDynamicGenDevice()).getActiveElectricalController());
        var auxiliary=battery.getAuxiliaryController();assertNotNull(auxiliary);assertNotNull(auxiliary.getPlantController());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        if(staggered)algorithm.setSolver(new PsseStaggeredEulerSolver(algorithm));algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());
        double p=auxiliary.getActiveOutput(),q=auxiliary.getReactiveOutput();
        for(int i=0;i<20;i++)assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(p,auxiliary.getActiveOutput(),1e-12);assertEquals(q,auxiliary.getReactiveOutput(),1e-6);
        assertTrue(battery.getEnergyOutput()>0,"Battery energy state must remain connected");
        auxiliary.getPlantController().setAuxiliaryInputs(.03,0,0);
        for(int i=0;i<40;i++)assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(p,auxiliary.getActiveOutput(),1e-12);
        assertTrue(auxiliary.getReactiveOutput()>q,"The nonzero reactive weight must still transmit plant commands");
        for(double state:battery.getNamedStates().values())assertTrue(Double.isFinite(state));
    }
}
