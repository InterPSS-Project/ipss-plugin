package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.relay.*;
import com.interpss.dstab.relay.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Actual staged relay classes reject stale static preparation before transfer callbacks. */
public class StagedRelayTargetLifecycleTest {
    @ParameterizedTest @CsvSource({"LDS,false","LDS,true","LVS,false","LVS,true"})
    void staleStaticSourceCannotAdvanceTimersOrTransfer(String family,boolean staggered)throws Exception {
        for(String mutation:List.of("removed","inactive","demand")) {
            IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
            Path root=Path.of("testData/adpter/psse/v33/HspModels");
            var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("tiocr1-0.dyr").toString());
            var a=context.getDynSimuAlgorithm();var net=a.getNetwork();assertTrue(a.getAclfAlgorithm().loadflow());
            if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
            a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.1);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
            var host=net.getDStabBus("Bus2");var source=host.getContributeLoad("1");var branch=net.getBranchList().getFirst();
            var generator=net.getDStabBus("Bus1").getContributeGen("1");var power=source.getLoadCP();
            var data=new StagedLoadSheddingRelayData(List.of(new LoadSheddingStage(family.equals("LDS")?61:1.1,0,0,.25),
                    new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0)),false);
            AbstractStagedLoadSheddingRelayModel relay=family.equals("LDS")
                    ?new Lds3blRelayModel(host,"1",data,net.getDStabBus("Bus1"),"1",0)
                    :new Lvs3blRelayModel(host,"1",data,branch,0,null,0);
            assertTrue(relay.initStates(host));var history=relay.getNamedStates();var matrix=net.getYMatrix().getA(0,0);
            if(mutation.equals("removed"))host.getContributeLoadList().remove(source);
            if(mutation.equals("inactive"))source.setStatus(false);
            if(mutation.equals("demand"))source.setLoadCP(power.multiply(1.2));
            assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));
            assertFalse(relay.afterStep(.01),mutation);assertEquals(history,relay.getNamedStates(),mutation);
            assertTrue(branch.isActive());assertTrue(generator.isActive());assertEquals(matrix,net.getYMatrix().getA(0,0));assertEquals(0.,relay.getShedFraction());
            if(mutation.equals("removed"))host.getContributeLoadList().add((com.interpss.dstab.DStabLoad)source);
            source.setStatus(true);source.setLoadCP(power);
            assertTrue(relay.afterStep(.01));assertEquals(.25,relay.getShedFraction(),1e-12);
            assertEquals(!family.equals("LVS"),branch.isActive());assertEquals(!family.equals("LDS"),generator.isActive());
            assertTrue(relay.afterStep(.01));assertEquals(.25,relay.getShedFraction(),1e-12);
        }
    }
}
