package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Complete relay-driven composite demand and equivalent-network comparisons. */
public class CompositeCollapsedFullTripTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-collapsed-shedding-20261104/captures.zip");

    @ParameterizedTest @CsvSource({"false,0.0","true,0.0","false,1.0","true,1.0"})
    void completeCompositeSheddingTrajectory(boolean staggered,String fraction,@TempDir Path temporary)throws Exception {
        Path archive=ARCHIVE;
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent collapsed composite captures");
        assertEquals("79598412cecf97c2c595a46506c012d3d9f61dfdbfe3f2918955a7871344c059",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;
        String prefix="target/cmld-collapsed-both-"+fraction+"-20261104/cmldblu1-restart-fault-h0.0005-";
        Path dynamic=temporary.resolve("dynamic.dyr");
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] raw=zip.getInputStream(zip.getEntry(prefix+"a-part0/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(prefix+"b-part0/native.csv")).readAllBytes());
            reference=csv(raw).stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(dynamic,zip.getInputStream(zip.getEntry(prefix+"a-part0/dynamic.dyr")).readAllBytes());
            assertArrayEquals(Files.readAllBytes(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw")),
                    zip.getInputStream(zip.getEntry(prefix+"a-part0/network.raw")).readAllBytes());
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        double end=Math.round(reference.getLast().get("time_s")/.0005)*.0005;
        var recording=Cmldblu1SharedRecording.simulateStaticControl(dynamic,staggered,.0005,.2,0,end);
        Path output=Files.createTempDirectory(Path.of("target"),"tiocr-composite-");
        recording.write(output.resolve("complete.csv.gz"));
        assertEquals(reference.size(),recording.frames.size(),"Every grid row and both event sides");
        var maxima=new TreeMap<String,Double>();var squares=new TreeMap<String,Double>();
        for(String key:List.of("V1","V2","V3","Distribution.LowVoltage","Distribution.LoadVoltage","P_PU","Q_PU","ANGLE_DEG")) {maxima.put(key,0.);squares.put(key,0.);}
        double positionError=0.;int duplicates=0;
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=recording.frames.get(i);
            assertEquals(r.get("time_s"),a.time(),5e-6);
            if(i>0&&r.get("time_s").equals(reference.get(i-1).get("time_s")))duplicates++;
            for(String key:maxima.keySet()) {
                double error=a.values().get(key)-r.get(key);
                maxima.put(key,Math.max(maxima.get(key),Math.abs(error)));
                squares.put(key,squares.get(key)+error*error);
            }
            positionError=Math.max(positionError,Math.abs(a.values().get("RELAY.Position")-r.get("RELAY_VAR1")));

        }
        assertEquals(3,duplicates,"Fault application, relay operation and fault clearing");
        assertEquals(1.-Double.parseDouble(fraction),recording.model.getRemainingCapacity(),1e-12);
        var normalized=new TreeMap<String,Double>();
        for(String key:List.of("V1","V2","Distribution.LowVoltage","Distribution.LoadVoltage","P_PU","Q_PU")) {
            double range=reference.stream().mapToDouble(r->r.get(key)).max().orElseThrow()
                    -reference.stream().mapToDouble(r->r.get(key)).min().orElseThrow();
            normalized.put(key,Math.sqrt(squares.get(key)/reference.size())/range);
        }
        System.out.println("Collapsed composite staggered="+staggered+" fraction="+fraction+" peaks="+maxima
                +" NRMSE="+normalized+" position="+positionError+" recording="+output.getFileName());
        for(var entry:normalized.entrySet())assertTrue(entry.getValue()<.001,entry.toString());
        for(var entry:normalized.entrySet())assertTrue(entry.getValue()<(staggered?8e-6:5e-5),
                "Qualified regression envelope: "+entry);
        assertTrue(Math.sqrt(squares.get("ANGLE_DEG")/reference.size())<.01,"Rotor angle RMSE");
        assertTrue(maxima.get("ANGLE_DEG")<6e-5,"Qualified rotor-angle peak envelope");
        assertTrue(maxima.get("V3")<1e-5,"Constant-voltage boundary");
        assertTrue(positionError<(staggered?1.5e-6:5e-5),"Moving-element position");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void fullRemovalPreservesParentAndRebuiltMatrix(boolean staggered,@TempDir Path temporary)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        Path dynamic=temporary.resolve("dynamic.dyr");
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            Files.write(dynamic,zip.getInputStream(zip.getEntry("target/cmld-collapsed-both-1.0-20261104/cmldblu1-restart-fault-h0.0005-a-part0/dynamic.dyr")).readAllBytes());
        }
        // This regression invokes the owner directly; no competing relay action is installed.
        Files.writeString(dynamic,Files.readString(dynamic).replaceAll("(?m)^.*TIOCR1.*$", ""));
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(
                Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),dynamic.toString());
        var a=context.getDynSimuAlgorithm();var parent=a.getNetwork().getDStabBus("Bus2");
        var outsideShunt=new org.apache.commons.math3.complex.Complex(.002,.003);
        parent.setShuntY(outsideShunt);
        var outside=com.interpss.dstab.DStabObjectFactory.createDStabLoad("outside");
        outside.setCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
        var outsidePower=new org.apache.commons.math3.complex.Complex(.2,.04);
        outside.setLoadCP(outsidePower);outside.setStatus(true);parent.getContributeLoadList().add(outside);
        assertTrue(parent.initContributeLoad(false));assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.01);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        assertTrue(a.initialization());
        var model=(org.interpss.dstab.dynLoad.impl.Cmldblu1Model)parent.getInfoOnlyDynModel();
        assertSame(parent,model.getLoadBus());assertSame(parent,model.getLowVoltBus());
        var source=a.getNetwork().getMachine("Bus1-mach1");boolean sourceActive=source.isActive();
        var parentVoltage=parent.getVoltage();
        // An unrelated shunt adjustment after initialization must not become owned.
        var lateShunt=new org.apache.commons.math3.complex.Complex(0,.004);
        parent.setShuntY(parent.getShuntY().add(lateShunt));parent.applyPositiveSequenceShuntAdmittanceChange(lateShunt);
        int row=parent.getSortNumber();a.getNetwork().getYMatrix().addToA(lateShunt,row,row);
        assertTrue(model.canPrepareLoadChange("1",-1));assertTrue(model.changeLoad(-1));
        assertTrue(parent.isActive());assertEquals(sourceActive,source.isActive());
        assertEquals(0,parent.getShuntY().subtract(outsideShunt.add(lateShunt)).abs(),1e-14);
        assertSame(outside,parent.getContributeLoad("outside"));assertTrue(outside.isActive());assertEquals(outsidePower,outside.getLoadCP());
        for(var leaf:parent.getDynLoadModelList())if(model.ownsCapacityLoad(leaf))assertFalse(leaf.isActive());
        var diagonal=a.getNetwork().getYMatrix().getA(row,row);
        a.getNetwork().formYMatrix4DStab();assertEquals(0,a.getNetwork().getYMatrix().getA(row,row).subtract(diagonal).abs(),1e-12);
        assertFalse(model.changeLoad(.1));assertFalse(model.changeLoad(-.1));
        for(int i=0;i<20;i++)assertTrue(a.solveDEqnStep(true));
        assertTrue(parent.getVoltage().subtract(parentVoltage).abs()>1e-5);
        assertEquals(parentVoltage.abs(),model.getNamedStates().get("Distribution.LoadVoltage"),0);
    }

    private static List<Map<String,Double>> csv(byte[] raw) {
        String[] lines=new String(raw,StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");
        var result=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(keys.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int j=0;j<keys.length;j++) {double value=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(value));row.put(keys[j],value);}
            row.put("Distribution.LowVoltage",row.get("VAR30"));row.put("Distribution.LoadVoltage",row.get("VAR29"));
            result.add(row);
        }
        return result;
    }
}
