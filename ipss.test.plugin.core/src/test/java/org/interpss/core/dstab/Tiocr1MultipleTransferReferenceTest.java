package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;

/** Two distinct transfer targets and a duplicated target retain one accepted network/load action. */
public class Tiocr1MultipleTransferReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-multiple-transfer-20261104/captures.zip");
    private static final Path SURVIVOR=Path.of("testData/private/verification-artifacts/tiocr-fourth-survivor-20261104/captures.zip");
    private static final Path ALL_OPEN=Path.of("testData/private/verification-artifacts/tiocr-three-transfer-20261104/captures.zip");
    @ParameterizedTest @ValueSource(booleans={false,true})
    void isolatedTerminalsReconnectWhileFaultRemainsActive(boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ALL_OPEN),"Retain independent all-open reference");
        try(var zip=new ZipFile(ALL_OPEN.toFile())) {
            for(String file:List.of("network.raw","dynamic.dyr"))
                Files.write(staged.resolve(file),zip.getInputStream(zip.getEntry("a/"+file)).readAllBytes());
        }
        HspUsageRankedModelTest.setup();
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(staged.resolve("network.raw").toString(),
                staged.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        if(staggered)algorithm.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm));
        algorithm.setSimuStepSec(.0005);algorithm.setTotalSimuTimeSec(.5);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        var fault=DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.05,.05);
        net.addDynamicEvent(fault,"Fault");
        assertTrue(algorithm.initialization());
        var first=net.getBranch("Bus1->Bus2(1)");
        while(first.isActive() && algorithm.getSimuTime()<.15)
            assertTrue(algorithm.solveDEqnStep(true),"Trip at "+algorithm.getSimuTime());
        assertFalse(first.isActive(),"Accepted transfer opens the first corridor");
        assertFalse(net.getDStabBus("Bus1").isActive());
        assertFalse(net.getDStabBus("Bus2").isActive());
        assertTrue(fault.isActive(),"The fault is still applied when the relay operates");
        first.setStatus(true);
        assertTrue(algorithm.solveDEqnStep(true),"Reclose at "+algorithm.getSimuTime());
        assertTrue(net.getDStabBus("Bus1").isActive());
        assertTrue(net.getDStabBus("Bus2").isActive());
        assertTrue(net.getDStabBus("Bus1").getVoltageMag()>0);
        assertTrue(fault.isActive(),"The fault stamp must survive the topology rebuild");
        int faultRow=net.getDStabBus("Bus1").getSortNumber();
        Complex stamped=net.getYMatrix().getA(faultRow,faultRow);
        net.formYMatrix4DStab();
        Complex noFault=net.getYMatrix().getA(faultRow,faultRow);
        assertTrue(stamped.subtract(noFault).abs()>1.,"Active fault admittance survives reconnection");
        new com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor().rebuildCurrentTopology(net);
        while(algorithm.getSimuTime()<.12)
            assertTrue(algorithm.solveDEqnStep(true),"Clearing after reclose at "+algorithm.getSimuTime());
        assertTrue(Double.isFinite(net.getDStabBus("Bus1").getVoltageMag()));
        assertTrue(net.getDStabBus("Bus1").isActive());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allThreeTargetsOpenAndDisconnectBothTerminalBuses(boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ALL_OPEN),"Retain independent all-open reference");
        assertEquals("0905ea9affc020617ecbe588ddb548f158fc772311be8f2e5907ff4cb5f10f50",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ALL_OPEN))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ALL_OPEN.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String file:List.of("network.raw","dynamic.dyr"))Files.write(staged.resolve(file),
                    zip.getInputStream(zip.getEntry("a/"+file)).readAllBytes());
        }
        HspUsageRankedModelTest.setup();
        Path out=Files.createTempDirectory(Path.of("target"),"tiocr-three-open-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),out);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-action"
                +(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete grid including action sides");
        int action=-1,duplicates=0;
        for(int i=0;i<actual.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(r.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates);assertTrue(action>0);
        for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double preActionLimit=channel.equals("Q_PU")||channel.equals("ANGLE_DEG")?.004:channel.equals("P_PU")?.002:.001;
            assertEquals(reference.get(action-1).get(channel),actual.get(action-1).get(channel),preActionLimit,
                    channel+" pre-action event side");
            assertEquals(0.,reference.get(action).get(channel),1e-12);
            assertEquals(0.,actual.get(action).get(channel),1e-8,
                    channel+" disconnected action side");
        }
        for(int i=action;i<actual.size();i++)for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG"))
            assertEquals(0.,actual.get(i).get(channel),1e-8,
                    channel+" stays de-energized after all target branches open at row "+i);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void threeTargetsWithSurvivingCorridor(boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SURVIVOR),"Retain independent connected-corridor reference");
        assertEquals("1c4087c09e497647464aa72d552a3bf546ed833ee0e4ccaefa1c5b23e8ce0440",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(SURVIVOR))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(SURVIVOR.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry("a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry("b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String file:List.of("network.raw","dynamic.dyr")) {
                byte[] source=zip.getInputStream(zip.getEntry("a/"+file)).readAllBytes();
                assertArrayEquals(source,zip.getInputStream(zip.getEntry("b/"+file)).readAllBytes());
                Files.write(staged.resolve(file),source);
            }
        }
        HspUsageRankedModelTest.setup();
        Path out=Files.createTempDirectory(Path.of("target"),"tiocr-surviving-fourth-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),out);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-action"
                +(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete grid and both event sides");
        int action=-1,duplicates=0;
        for(int i=0;i<actual.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(r.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates);assertTrue(action>0);
        var left=actual.get(action-1);var right=actual.get(action);
        assertEquals(1.,left.get("RELAY.Position"));assertEquals(1.,left.get("RELAY.Operated"));
        assertEquals(0.,left.get("RELAY.Revision"));assertEquals(1.,right.get("RELAY.Revision"));
        assertEquals(.1,right.get("RELAY.ShedFraction"),1e-12);
        for(String circuit:List.of("1","2","3")) {
            assertEquals(1.,left.get("BRANCH.Bus1->Bus2("+circuit+")"));
            assertEquals(0.,right.get("BRANCH.Bus1->Bus2("+circuit+")"));
        }
        assertEquals(1.,left.get("BRANCH.Bus1->Bus2(4)"));
        assertEquals(1.,right.get("BRANCH.Bus1->Bus2(4)"));
        for(int i=action;i<actual.size();i++) {
            var row=actual.get(i);
            assertEquals(0.,row.get("RELAY.Current"),"Monitored branch is open");
            assertEquals(1.,row.get("RELAY.Revision"),"One accepted action");
            assertEquals(1.,row.get("BRANCH.Bus1->Bus2(4)"),"Fourth circuit retains the corridor");
        }
        for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                double value=reference.get(i).get(channel),delta=actual.get(i).get(channel)-value;
                sum+=delta*delta;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size());
            double error=channel.equals("ANGLE_DEG")?rmse:rmse/(max-min);
            assertTrue(error<(channel.equals("ANGLE_DEG")?5e-5:staggered?8e-6:5e-5),
                    channel+" full-trajectory error="+error);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void threeDistinctTransferTargets(boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent two-target pre-trip capture");
        assertEquals("05760ac11b534f31068cd01d5ad7d71c75a53add35c19b24d54537b747c7b483",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            reference=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry("1_2/a/native.csv")).readAllBytes())
                    .stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(staged.resolve("network.raw"),zip.getInputStream(zip.getEntry("1_2/a/network.raw")).readAllBytes());
            String dyr=new String(zip.getInputStream(zip.getEntry("1_2/a/dynamic.dyr")).readAllBytes());
            String third="1 2 '2' 0 0 '0'";
            assertEquals(1,dyr.split(java.util.regex.Pattern.quote(third),-1).length-1);
            Files.writeString(staged.resolve("dynamic-two.dyr"),dyr);
            Files.writeString(staged.resolve("dynamic.dyr"),dyr.replace(third,"1 2 '2' 1 2 '3'"));
        }
        HspUsageRankedModelTest.setup();
        Path out=Files.createTempDirectory(Path.of("target"),"tiocr-three-transfer-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),out);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-action"
                +(staggered?"-staggered":"")+"-fault.csv")));
        Path baselineOut=Files.createTempDirectory(Path.of("target"),"tiocr-two-transfer-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,
                staged.resolve("network.raw"),staged.resolve("dynamic-two.dyr"),baselineOut);
        var baseline=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(baselineOut.resolve("tiocr1-action"
                +(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"Complete grid and event-side duplicates");
        assertEquals(reference.size(),baseline.size());
        int action=-1,duplicates=0;
        for(int i=0;i<actual.size();i++) {
            var a=actual.get(i);var r=reference.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(r.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates);assertTrue(action>0);
        for(int i=0;i<action;i++) {
            var a=actual.get(i);var b=baseline.get(i);
            for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG",
                    "RELAY.Position","RELAY.Current","RELAY.Operated","RELAY.Revision"))
                assertEquals(b.get(channel),a.get(channel),1e-12,"Third target has no pre-action effect: "+channel);
        }
        var left=actual.get(action-1);var right=actual.get(action);
        assertEquals(1.,left.get("RELAY.Position"));assertEquals(1.,left.get("RELAY.Operated"));
        assertEquals(0.,left.get("RELAY.Revision"));assertEquals(1.,right.get("RELAY.Revision"));
        assertEquals(.1,right.get("RELAY.ShedFraction"),1e-12);
        for(String circuit:List.of("1","2","3")) {
            assertEquals(1.,left.get("BRANCH.Bus1->Bus2("+circuit+")"));
            assertEquals(0.,right.get("BRANCH.Bus1->Bus2("+circuit+")"));
        }
        for(int i=action;i<actual.size();i++) {
            var row=actual.get(i);
            assertEquals(0.,row.get("RELAY.Current"));
            assertEquals(1.,row.get("RELAY.Revision"),"One accepted physical action");
            assertEquals(.1,row.get("RELAY.ShedFraction"),1e-12);
            for(String circuit:List.of("1","2","3"))
                assertEquals(0.,row.get("BRANCH.Bus1->Bus2("+circuit+")"));
        }
    }
    @ParameterizedTest @CsvSource({"false,1_2","true,1_2","false,1_2_1","true,1_2_1"})
    void completeMultipleTransfer(boolean staggered,String terminal,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain paired independent action references");
        assertEquals("05760ac11b534f31068cd01d5ad7d71c75a53add35c19b24d54537b747c7b483",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(terminal+"/a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(terminal+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(first).stream().filter(r->r.get("time_s")>=0).toList();
            for(String file:List.of("network.raw","dynamic.dyr"))
                Files.write(staged.resolve(file),zip.getInputStream(zip.getEntry(terminal+"/a/"+file)).readAllBytes());
        }
        HspUsageRankedModelTest.setup();
        Path out=Files.createTempDirectory(Path.of("target"),"tiocr-multiple-"+terminal+"-");
        HspUsageRankedModelTest.run("tiocr1-action",false,staggered,false,staged.resolve("network.raw"),staged.resolve("dynamic.dyr"),out);
        var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-action"+(staggered?"-staggered":"")+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),"All grid rows and event-side duplicates");
        double positionError=0,rawCurrentDifference=0;int action=-1,duplicates=0;
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6);
            positionError=Math.max(positionError,Math.abs(a.get("RELAY.Position")-r.get("VAR1")));
            rawCurrentDifference=Math.max(rawCurrentDifference,Math.abs(a.get("RELAY.Current")-r.get("VAR0")));
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s"))) {
                duplicates++;
                if(r.get("VAR1")==0.&&reference.get(i-1).get("VAR1")==1.)action=i;
            }
        }
        assertEquals(3,duplicates,"Fault application, relay trip, fault clearing");assertTrue(action>0);
        var left=actual.get(action-1);var right=actual.get(action);
        assertEquals(1.,left.get("RELAY.Position"));assertTrue(left.get("RELAY.Current")>.4);
        assertEquals(1.,left.get("RELAY.Operated"),"Internal operation latch precedes physical commit");
        assertEquals(0.,left.get("RELAY.Revision"),"Physical revision remains pre-action");
        assertEquals(0.,right.get("RELAY.Position"));assertEquals(0.,right.get("RELAY.Current"));
        assertEquals(1.,right.get("RELAY.Operated"));assertEquals(.1,right.get("RELAY.ShedFraction"),1e-12);
        for(String circuit:List.of("1","2")){assertEquals(1.,left.get("BRANCH.Bus1->Bus2("+circuit+")"));assertEquals(0.,right.get("BRANCH.Bus1->Bus2("+circuit+")"));}
        assertEquals(1.,left.get("BRANCH.Bus1->Bus2(3)"));assertEquals(1.,right.get("BRANCH.Bus1->Bus2(3)"));
        for(int i=action;i<actual.size();i++) {
            assertEquals(0.,actual.get(i).get("RELAY.Current"),"Opened physical branch carries no current");
            assertEquals(1.,actual.get(i).get("RELAY.Revision"),"Transfer and shedding commit once");
            assertEquals(.1,actual.get(i).get("RELAY.ShedFraction"),1e-12);
            assertEquals(0.,actual.get(i).get("BRANCH.Bus1->Bus2(1)"));assertEquals(0.,actual.get(i).get("BRANCH.Bus1->Bus2(2)"));
            assertEquals(1.,actual.get(i).get("BRANCH.Bus1->Bus2(3)"));
        }
        assertTrue(positionError<(staggered?3.3e-7:2.2e-5),"Unchanged terminal position envelope: "+positionError);
        // Diagnostic memory is not the physical elapsed timer/current. Retain and expose this distinction.
        assertEquals(9999.,reference.get(0).get("VAR2"));
        assertEquals(reference.get(action-1).get("time_s"),reference.get(action-1).get("VAR2"),5e-6);
        assertEquals(0.,reference.get(action).get("VAR2"));assertTrue(rawCurrentDifference>1.);
        var errors=new LinkedHashMap<String,Double>();
        for(String channel:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                double value=reference.get(i).get(channel),delta=actual.get(i).get(channel)-value;
                sum+=delta*delta;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size());
            double error=channel.equals("ANGLE_DEG")?rmse:rmse/(max-min);errors.put(channel,error);
            assertTrue(error<(channel.equals("ANGLE_DEG")?5e-5:staggered?8e-6:5e-5),channel+" error="+error);
        }
        System.out.println("TIOCR physical terminal="+terminal+" staggered="+staggered+" errors="+errors+" position="+positionError);
    }
}
