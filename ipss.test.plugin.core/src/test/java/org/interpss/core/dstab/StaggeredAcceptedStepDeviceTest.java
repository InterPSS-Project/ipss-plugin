package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.relay.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.controller.runtime.AcceptedStepDevice;
import com.interpss.dstab.relay.model.*;

/** Discrete protection keeps its once-per-accepted-interval timing under staggered integration. */
public class StaggeredAcceptedStepDeviceTest {
    @ParameterizedTest @ValueSource(strings={"LDS3BL","LVS3BL","FRQTPAT","VTGTPAT"})
    void initializesAttachedProtectionAndOperatesOnceAtTheEnteredDelay(String family)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/HspModels");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("tiocr1-0.dyr").toString());
        var a=c.getDynSimuAlgorithm();var net=a.getNetwork();
        for(var b:net.getBusList())for(var d:b.getDynamicBusDeviceList())d.setStatus(false);
        var host=net.getDStabBus("Bus2");var target=net.getDStabBus("Bus1");
        Object relay;
        if(family.equals("LDS3BL")||family.equals("LVS3BL")) {
            var data=new StagedLoadSheddingRelayData(List.of(new LoadSheddingStage(family.equals("LDS3BL")?61:2,.01,.01,.25),
                    new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0)),false);
            relay=family.equals("LDS3BL")?new Lds3blRelayModel(host,"1",data,null,null,0):new Lvs3blRelayModel(host,"1",data,null,0,null,0);
        } else {
            var data=new GeneratorTripRelayData(0,family.equals("FRQTPAT")?10:.1,.01,.01);
            relay=family.equals("FRQTPAT")?new FrqtpatRelayModel(2,host,target,(com.interpss.dstab.DStabGen)target.getContributeGen("1"),data)
                    :new VtgtpatRelayModel(2,host,target,(com.interpss.dstab.DStabGen)target.getContributeGen("1"),data);
        }
        var discrete=(AcceptedStepDevice)relay;
        assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.05);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(a.initialization());assertEquals(0,discrete.getAcceptedNetworkActionVersion());
        for(int i=0;i<3;i++){assertTrue(a.solveDEqnStep(true));assertEquals(0,discrete.getAcceptedNetworkActionVersion());}
        assertTrue(a.solveDEqnStep(true));assertEquals(1,discrete.getAcceptedNetworkActionVersion());
        if(relay instanceof AbstractStagedLoadSheddingRelayModel staged)assertEquals(.25,staged.getShedFraction(),1e-12);
        else assertFalse(target.getContributeGen("1").isActive());
        for(int i=0;i<4;i++)assertTrue(a.solveDEqnStep(true));
        assertEquals(1,discrete.getAcceptedNetworkActionVersion(),"No repeated physical action or trial-stage timer advance");
    }
}
