package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.RepccNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-solver typed composition; native import is a separate claim. */
public class RepccTypedRuntimeSmibTest extends CorePluginTestSetup {
    @Test void referenceOffsetsStagesAndInactiveOwnerLifecycleAreExplicit(@TempDir Path dir)throws Exception{
        Path file=dir.resolve("plant.dyr");Files.writeString(file,RepccNativeReference.profiles().getFirst().input());
        var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw",file.toString());
        var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();net.setAllowGenWithoutMach(true);
        algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuStepSec(.0005);algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuOutputHandler(new StateMonitor());assertTrue(algo.initialization());
        var converter=(Regca1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var electrical=converter.getActiveElectricalController();var plant=electrical.getRepccController();assertNotNull(plant);
        assertThrows(IllegalStateException.class,()->plant.step(.0005,1));
        var refs=plant.getKernel().references();plant.getKernel().setReferences(new RepccKernel.References(refs.voltage(),refs.reactive(),refs.frequency(),refs.active()+.04,refs.powerFactor(),0,0));
        plant.step(.0005,0);assertThrows(IllegalStateException.class,plant::acceptedEndpoint);plant.step(.0005,1);
        assertEquals(.0005,plant.getKernel().time());assertTrue(plant.activeIncrement()>0&&plant.activeIncrement()<.04);
        double initialPower=converter.getParentGen().getGen().getReal()*2;
        assertEquals(initialPower+plant.activeIncrement(),electrical.getPlantPowerReference(initialPower),1e-14);
        plant.acceptedEndpoint();var before=plant.getNamedStates();assertThrows(UnsupportedOperationException.class,before::clear);
        converter.getParentGen().setStatus(false);plant.step(.0005,0);plant.step(.0005,1);assertTrue(plant.acceptedEndpoint().isEmpty());
        assertEquals(before,plant.getNamedStates());assertEquals(.0005,plant.getKernel().time());
    }
    @Test void allEightNativeFoundationProfilesRemainStationaryInTheCoupledSolver(@TempDir Path dir) throws Exception {
        int profiles=0;
        for(var profile:RepccNativeReference.profiles()){
            Path file=dir.resolve(profile.name()+".dyr");
            Files.writeString(file,profile.input().lines().filter(s->!s.contains("'REPCC'")).reduce("",(a,b)->a+b+"\n"));
            var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw",file.toString());
            var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();net.setAllowGenWithoutMach(true);
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
            var converter=assertInstanceOf(Regca1Model.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
            var electrical=assertInstanceOf(Reeca1Model.class,converter.getActiveElectricalController());var plant=new RepccModel(profile.parameters(),converter);
            electrical.setRepccController(plant);algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.0005);algo.setTotalSimuTimeSec(.3);algo.setSimuOutputHandler(new StateMonitor());
            assertTrue(algo.initialization());double initial=net.getBus("Bus1").getVoltageMag(),drift=0;
            var states=plant.getKernel().getNamedStates();assertEquals(45,plant.getNamedStates().size());
            assertEquals(0,plant.activeIncrement(),1e-14);assertEquals(0,plant.reactiveIncrement(),1e-14);
            for(int tick=0;tick<600;tick++){
                assertTrue(algo.solveDEqnStep(true));drift=Math.max(drift,Math.abs(net.getBus("Bus1").getVoltageMag()-initial));
                for(double value:plant.getNamedStates().values())assertTrue(Double.isFinite(value));
            }
            double stateDrift=0;for(var entry:states.entrySet())stateDrift=Math.max(stateDrift,Math.abs(plant.getKernel().getNamedStates().get(entry.getKey())-entry.getValue()));
            System.out.println("REPCC typed coupled "+profile.name()+" voltage drift="+drift+" state drift="+stateDrift);
            assertTrue(drift<1e-7,profile.name());assertTrue(stateDrift<1e-6,profile.name());assertEquals(.3,plant.getKernel().time(),1e-12);
            assertThrows(IllegalStateException.class,()->electrical.setRepccController(new RepccModel(profile.parameters(),converter)));
            assertThrows(IllegalArgumentException.class,()->converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP));profiles++;
        }
        assertEquals(8,profiles);
    }
}
