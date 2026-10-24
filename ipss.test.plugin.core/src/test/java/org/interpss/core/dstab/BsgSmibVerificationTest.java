package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.core.dstab.reference.RepcaNativeVerification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Independent SMIB traces distinguish response limits against native generator response flags. */
public class BsgSmibVerificationTest {
    private static final Path ROOT=Path.of("testData/private/bsg-smib-v2");
    @TempDir Path temporary;
    @BeforeAll static void initialize() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }
    private static ActivePowerResponseLimits policy(String mode) {
        return mode.startsWith("fixed")?ActivePowerResponseLimits.FIXED:
                mode.startsWith("down-")?ActivePowerResponseLimits.DOWN_ONLY:ActivePowerResponseLimits.NORMAL;
    }
    private static List<Map<String,Double>> rows(String mode,String grid) throws Exception {
        var file=ROOT.resolve(mode+"-"+grid+"-a/native.csv");
        assertTrue(Files.isRegularFile(file),"Missing required profile "+file);
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(file.resolveSibling("manifest.json"))).getAsJsonObject();
        assertEquals("complete",manifest.get("status").getAsString());
        for(String name:List.of("network.raw","dynamic.dyr","native.csv","readback.json")) {
            var bytes=Files.readAllBytes(file.resolveSibling(name));
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(manifest.getAsJsonObject("files").get(name).getAsString(),hash,"Changed SMIB evidence "+name);
        }
        var result=RepcaNativeVerification.rows(Files.readString(file));
        assertEquals(manifest.get("sample_count").getAsInt(),result.size());
        for(var row:result)assertTrue(row.values().stream().allMatch(Double::isFinite));
        return result;
    }
    @Test void allControllerStatesMatchIndependentBoundaryReplay() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private SMIB references unavailable");
        for(String grid:List.of("coarse","fine")) for(String mode:List.of("normal-up","normal-down","fixed-up","fixed-down","down-up","down-down")) {
            var rows=rows(mode,grid);double dt=grid.equals("coarse")?.0005:.00025;
            var context=new PSSEMultiFileLoader().loadDStab(ROOT.resolve("normal-up-coarse-a/network.raw").toString(),ROOT.resolve("normal-up-coarse-a/dynamic.dyr").toString());
            var host=(Regca1Model)((DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            for(boolean history:new boolean[]{false,true}) {
                var electrical=Reeca1Model.fromNativeRecord(host.getReeca1Controller().getData(),null);
                var plant=Repca1Model.fromNativeRecord(host.getReeca1Controller().getPlantController().getData());
                electrical.setPlantController(plant);electrical.setActivePowerResponseLimits(policy(mode));
                var first=rows.getFirst();electrical.initialize(first.get("P_PU"),first.get("Q_PU"),first.get("V1"));
                var previous=first;boolean changed=false;double[] maxima=new double[13];
                for(var row:rows) {
                    if(!changed&&previous.get("time_s")>=.05-1e-7) {
                        plant.setActivePowerReference(plant.getActivePowerReference()+(mode.endsWith("-up")?.1:-.1));changed=true;
                    }
                    double elapsed=row.get("time_s")-previous.get("time_s");
                    if(elapsed>1e-8) {
                        if(history) electrical.stepTwoStep(dt,previous.get("P_PU"),previous.get("Q_PU"),previous.get("V1"),1+previous.get("F1_DEV"));
                        else {
                            electrical.step(dt,previous.get("P_PU"),previous.get("Q_PU"),previous.get("V1"),1+previous.get("F1_DEV"),0);
                            electrical.step(dt,row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1+row.get("F1_DEV"),1);
                        }
                    } else if(row!=first&&history) electrical.rebaseNextTwoStep();
                    var e=electrical.getNativeStates();var p=plant.getNativeStates();
                    for(int i=0;i<6;i++)maxima[i]=Math.max(maxima[i],Math.abs(e[i]-row.get("ELEC_STATE"+i)));
                    for(int i=0;i<7;i++)maxima[i+6]=Math.max(maxima[i+6],Math.abs(p[i]-row.get("PLANT_STATE"+i)));
                    previous=row;
                }
                System.out.println("BSG_REPLAY "+mode+" "+grid+" history="+history+" maxima="+Arrays.toString(maxima));
                for(int i=0;i<13;i++)assertTrue(maxima[i]<.004,mode+" "+grid+" history="+history+" state="+i+" error="+maxima[i]);
                if(policy(mode)==ActivePowerResponseLimits.FIXED) {
                    assertEquals(first.get("P_PU"),electrical.getActivePowerOrder(),1e-8);
                    assertEquals(0,plant.getPref(),1e-12);
                }
            }
        }
    }
    @Test void bsgLoaderRunsTheFixedResponseThroughTheProductionSolver() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private SMIB references unavailable");
        var source=ROOT.resolve("normal-up-coarse-a");
        Files.copy(source.resolve("network.raw"),temporary.resolve("network.raw"));
        Files.copy(source.resolve("dynamic.dyr"),temporary.resolve("dynamic.dyr"));
        Files.writeString(temporary.resolve("dynamic_basegen.bsg"),"1 \"RENEWABLE PLANT\" 230 \"1\"\n");
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(temporary.resolve("network.raw").toString(),temporary.resolve("dynamic.dyr").toString());
        assertEquals(1,loader.getLastResponseEntries().size());assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,100./(230.*230./100.)),null,.20,.05),"Fault");
        assertTrue(algorithm.initialization());
        var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var electrical=converter.getReeca1Controller();var plant=electrical.getPlantController();
        double initial=electrical.getActivePowerOrder(),maximumPowerChange=0;
        for(int tick=0;tick<1000;tick++) {
            if(tick==100)plant.setActivePowerReference(plant.getActivePowerReference()+.1);
            assertTrue(algorithm.solveDEqnStep(true));
            assertEquals(initial,electrical.getActivePowerOrder(),1e-12);
            assertEquals(0,plant.getPref(),1e-12);
            double p=((Number)converter.getStates(null).get("REGCA1_P")).doubleValue();
            maximumPowerChange=Math.max(maximumPowerChange,Math.abs(p-initial));
        }
        assertTrue(maximumPowerChange>.02,"Fault current/voltage response remains active");
        assertThrows(IllegalStateException.class,()->electrical.setActivePowerResponseLimits(ActivePowerResponseLimits.NORMAL));
        electrical.initialize(initial,.0474445894361,1.01);
        assertEquals(initial,electrical.getEffectiveActivePowerMaximum(),0);
        assertEquals(initial,electrical.getEffectiveActivePowerMinimum(),0);
    }

    @Test void coldRepeatsPreserveStatesAndPhysicalChannels() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private SMIB references unavailable");
        for(String grid:List.of("coarse","fine")) for(String policy:List.of("normal","fixed","down","baseload","baseload2"))
            for(String direction:List.of("up","down")) {
                String mode=policy+"-"+direction;var first=rows(mode,grid);
                var repeat=RepcaNativeVerification.rows(Files.readString(ROOT.resolve(mode+"-"+grid+"-b/native.csv")));
                assertEquals(first.size(),repeat.size());
                for(int i=0;i<first.size();i++)for(String name:first.get(i).keySet())
                    if(name.contains("STATE")||name.equals("P_PU")||name.equals("Q_PU")||name.matches("[VA][123](_DEG)?"))
                        assertEquals(first.get(i).get(name),repeat.get(i).get(name),name.endsWith("_DEG")?1e-5:1e-6,mode+" "+name);
            }
    }
    @Test void converterOnlyReferenceKeepsInitializedCommandsDuringFault() throws Exception {
        var path=ROOT.resolve("uncontrolled-up-coarse-b");
        assumeTrue(Files.isRegularFile(path.resolve("native.csv")),"Private converter-only reference unavailable");
        var data=RepcaNativeVerification.rows(Files.readString(path.resolve("native.csv")));
        double initialIp=data.getFirst().get("CONVERTER_STATE0"),initialIq=data.getFirst().get("CONVERTER_STATE1");
        for(var row:data) {
            assertEquals(initialIp,row.get("CONVERTER_STATE0"),1e-7);
            assertEquals(initialIq,row.get("CONVERTER_STATE1"),1e-7);
        }
        assertTrue(data.stream().anyMatch(row->Math.abs(row.get("P_PU")-.24)>.02));
    }

    @Test void nativeResponseFlagsAgreeWithExplicitControllerLimits() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private SMIB references unavailable");
        for(String grid:List.of("coarse","fine")) for(String direction:List.of("up","down")) {
            for(var pair:List.of(List.of("down-","baseload-"),List.of("fixed-","baseload2-"))) {
                var explicit=rows(pair.get(0)+direction,grid);var flagged=rows(pair.get(1)+direction,grid);
                assertEquals(explicit.size(),flagged.size());
                for(int i=0;i<explicit.size();i++) for(String name:explicit.get(i).keySet()) {
                    if(name.contains("STATE")||name.equals("P_PU")||name.equals("Q_PU")||name.matches("[VA][123](_DEG)?"))
                        assertEquals(explicit.get(i).get(name),flagged.get(i).get(name),name.endsWith("_DEG")?1e-5:1e-6,
                                grid+" "+direction+" "+name+" at "+explicit.get(i).get("time_s"));
                }
            }
            var fixed=rows("fixed-"+direction,grid);
            for(var row:fixed)assertEquals(.24,row.get("ELEC_STATE5"),1e-7);
            assertTrue(fixed.stream().anyMatch(r->Math.abs(r.get("P_PU")-.24)>.02),"Fixed response retains voltage/current dynamics during a fault");
            var normal=rows("normal-"+direction,grid);
            assertTrue(normal.stream().filter(r->r.get("time_s")>.15&&r.get("time_s")<.19)
                    .anyMatch(r->Math.abs(r.get("ELEC_STATE5")-.24)>.02),"Normal control must respond to the reference step");
        }
    }

}
