package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.RepcaNativeVerification;
import com.interpss.dstab.mach.model.Reaxbu1Model;
import com.interpss.dstab.renewable.Regca1Model;
import com.interpss.dstab.renewable.Reeca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;

/** Generic converter host, absolute initialization and independent auxiliary-state replay. */
public class ReaxbGenericHostTest {
    private static final Path ROOT=Path.of("testData/private/reaxb-host");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    private static List<Path> acceptedProfiles()throws Exception {
        try(var paths=Files.list(ROOT)) {
            var result=new ArrayList<Path>();
            for(Path path:paths.sorted().toList())if(path.getFileName().toString().endsWith("-a")) {
                var m=com.google.gson.JsonParser.parseString(Files.readString(path.resolve("manifest.json"))).getAsJsonObject();
                if(m.get("status").getAsString().equals("complete")&&m.getAsJsonObject("settings").get("mode").getAsString().startsWith("legacy"))result.add(path);
            }
            return result;
        }
    }

    @Test void nativeLegacyRecordsAttachWithTheirAbsoluteOperatingReferences()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private auxiliary host references unavailable");
        for(Path directory:acceptedProfiles()) {
            var loader=new PSSEMultiFileLoader();
            var context=loader.loadDStab(directory.resolve("network.raw").toString(),directory.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());
            var generator=(com.interpss.dstab.DStabGen)context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
            var converter=assertInstanceOf(Regca1Model.class,generator.getDynamicGenDevice());
            var controller=assertInstanceOf(Reeca1Model.class,converter.getActiveElectricalController());
            var auxiliary=controller.getAuxiliaryController();assertNotNull(auxiliary);assertNotNull(auxiliary.getPlantController());
            assertEquals(9,auxiliary.getIntegrationStates().size());
            assertEquals(9,auxiliary.getStateDerivativeSnapshot().rates().size());
            for(double rate:auxiliary.getStateDerivativeSnapshot().rates().values())assertEquals(0,rate,1e-12);
            var first=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv"))).getFirst();
            assertEquals(first.get("AUX_STATE0"),auxiliary.getReactiveOutput(),1e-6);
            assertEquals(first.get("AUX_STATE1"),auxiliary.getActiveOutput(),1e-6);
            double p=auxiliary.getActiveOutput(),q=auxiliary.getReactiveOutput();
            algorithm.setTotalSimuTimeSec(.05);assertTrue(algorithm.performSimulation());
            assertEquals(p,auxiliary.getActiveOutput(),1e-10);assertEquals(q,auxiliary.getReactiveOutput(),1e-10);
        }
    }

    @Test void bothLegacyNamesMatchTheStandardReferenceAndColdRegeneration()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private auxiliary host references unavailable");
        for(String grid:List.of("coarse","fine"))for(String repeat:List.of("a","b"))for(String mode:List.of("legacy3","legacy4")) {
            Path nativeFile=ROOT.resolve(mode+"-"+grid+"-"+repeat).resolve("native.csv");
            var standard=RepcaNativeVerification.rows(Files.readString(ROOT.resolve("standard-"+grid+"-"+repeat).resolve("native.csv")));
            var legacy=RepcaNativeVerification.rows(Files.readString(nativeFile));assertEquals(standard.size(),legacy.size());
            for(int i=0;i<legacy.size();i++)for(String channel:legacy.get(i).keySet())
                // In voltage mode the inactive power-factor reference slot
                // differs by 1.36e-19 in one standard cold run. Retain and score it.
                assertEquals(standard.get(i).get(channel),legacy.get(i).get(channel),
                        channel.equals("PLANT_VAR9")||channel.equals("ELEC_VAR6")?2e-19:0,channel+" "+i);
            assertEquals(-1,Files.mismatch(ROOT.resolve(mode+"-"+grid+"-a").resolve("native.csv"),nativeFile));
        }
    }

    @Test void recordedPlantOutputsDriveBothAuxiliaryStatesAtBothStepSizes()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private auxiliary host references unavailable");
        for(Path directory:acceptedProfiles()) {
            var settings=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject().getAsJsonObject("settings");
            var rows=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv")));
            var first=rows.getFirst();var previous=first;
            var model=new Reaxbu1Model("REAX3BU1",new com.interpss.dstab.mach.model.Reaxbu1Data(2,(double)(float).066667,1,1,9999,-9999,9999,-9999));
            double mva=settings.has("machine_mva")?settings.get("machine_mva").getAsDouble():100;
            model.initializeOutputs(first.get("AUX_STATE0"),first.get("AUX_STATE1"),100/mva);
            double dt=settings.get("step_s").getAsDouble();double[] error=new double[2];
            for(var row:rows) {
                if(row.get("time_s")>previous.get("time_s")) {
                    model.setExternalSignals(previous.get("PLANT_VAR13"),previous.get("PLANT_VAR14"));model.step(dt,0);
                    model.setExternalSignals(row.get("PLANT_VAR13"),row.get("PLANT_VAR14"));model.step(dt,1);
                }
                error[0]=Math.max(error[0],Math.abs(model.getReactiveOutput()-row.get("AUX_STATE0")));
                error[1]=Math.max(error[1],Math.abs(model.getActiveOutput()-row.get("AUX_STATE1")));previous=row;
            }
            System.out.println("REAXB_STATE_REPLAY "+directory.getFileName()+" "+Arrays.toString(error));
            assertTrue(error[0]<=1e-7,"Reactive state error "+error[0]);
            assertTrue(error[1]<=1.5e-6,"Active state error "+error[1]);
        }
    }
}
