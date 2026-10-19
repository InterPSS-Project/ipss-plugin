package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.core.dstab.reference.HplntdProportionalNativeReference;
import org.interpss.dstab.renewable.HplntdData;

/** Proportional-only state replay and explicitly bounded imported coupling. */
public class HplntdProportionalNativeSmibTest {
    @Test void compareEveryPlantStateAndAbsoluteIncrementalCommand()throws Exception {
        HplntdKernelNativeConformanceTest.compareProfiles(HplntdProportionalNativeReference.profiles(),
                p->HplntdProportionalNativeReference.replayBounds(p.name()));
    }
    @Test void compareEveryImportedSmallNetworkStateAndPowerChannel(@TempDir Path dir)throws Exception {
        HplntdSelectorNativeSmibTest.compareImportedProfiles(HplntdProportionalNativeReference.profiles(),
                p->HplntdProportionalNativeReference.coupledBounds(p.name()),dir,"hplntd-proportional-coupled");
    }
    @Test void groupReinitializationReseedsProportionalReferencesAndUnusedIntegrals(@TempDir Path dir)throws Exception {
        var factory=new HplntdTypedRuntimeSmibTest();
        for(var p:HplntdProportionalNativeReference.profiles()){
            var f=factory.fixture(dir.resolve(p.name()),false,p.step(),true,p);var g=f.group();
            for(int tick=0;tick<5;tick++)assertTrue(f.algorithm().solveDEqnStep(true));
            g.ports().getLast().initialize(.3,.08);assertThrows(IllegalStateException.class,g::kernel);
            g.ports().getFirst().initialize(.4,.1);
            assertEquals(0,g.kernel().time(),0);assertEquals(0,g.predictorCount());assertEquals(0,g.correctorCount());
            if(p.plant().value(HplntdData.Parameter.KI)==0)assertEquals(0,g.kernel().states()[2],0);
            if(p.plant().value(HplntdData.Parameter.KIG)==0)assertEquals(0,g.kernel().states()[5],0);
            for(var port:g.ports()){assertEquals(0,port.activeIncrement(),0);assertEquals(0,port.reactiveIncrement(),0);}
            assertEquals(17,g.getNamedStates().size());
        }
    }
}
