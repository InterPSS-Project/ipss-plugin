package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;

/** Rejected initial equilibria establish physical-bound behavior, not trajectory acceptance. */
public class Hyg3InitialBoundReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/hyg3-initial-bounds-20261104/captures.zip");
    @ParameterizedTest @CsvSource({"0,upper,false","0,upper,true","0,lower,false","0,lower,true","1,upper,false","1,upper,true","1,lower,false","1,lower,true"})
    void preservesPhysicalBoundsAndExposesNonzeroResidual(int mode,String side,boolean staggered,@TempDir Path staged)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent rejected-initialization controls");
        assertEquals("94de426512b10c23a1d8ff1be918ba9c10395b053bbf0b1636fe297aaa7edaa4",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        String tag="m"+mode+"-initial-"+side+"-infeasible-h0p0005-t0p5-";
        com.fasterxml.jackson.databind.JsonNode initial;
        Map<String,Double> first;
        try(var archive=new ZipFile(ARCHIVE.toFile())) {
            byte[] csv=archive.getInputStream(archive.getEntry(tag+"a/native.csv")).readAllBytes();
            assertArrayEquals(csv,archive.getInputStream(archive.getEntry(tag+"b/native.csv")).readAllBytes());
            String log=new String(archive.getInputStream(archive.getEntry(tag+"a/native.log")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(log.contains("Initial conditions suspect"));assertFalse(log.contains("Initial conditions check O.K."));
            var manifest=new ObjectMapper().readTree(archive.getInputStream(archive.getEntry(tag+"a/manifest.json")));
            assertEquals("rejected-quality",manifest.path("status").asText());
            initial=new ObjectMapper().readTree(archive.getInputStream(archive.getEntry(tag+"a/initial-boundary.json")));
            assertEquals(initial.get("entered"),initial.get("initialized"),"No source parameter adjustment");
            for(String file:List.of("network.raw","dynamic.dyr"))Files.write(staged.resolve(file),archive.getInputStream(archive.getEntry(tag+"a/"+file)).readAllBytes());
            String[] lines=new String(csv,java.nio.charset.StandardCharsets.UTF_8).split("\\R"),keys=lines[0].split(","),values=lines[1].split(",");first=new HashMap<>();
            for(int i=0;i<keys.length;i++)first.put(keys[i],Double.parseDouble(values[i]));
        }
        org.interpss.IpssCorePlugin.init();org.interpss.IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(staged.resolve("network.raw").toString(),staged.resolve("dynamic.dyr").toString());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuStepSec(.0005);
        if(staggered)algorithm.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm));
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseHyg3Governor)machine.getGovernor();var data=governor.getData();
        assertEquals(data.getPmax(),governor.getEffectivePmax(),0.);assertEquals(data.getPmin(),governor.getEffectivePmin(),0.);
        assertEquals(first.get("STATE4"),governor.getGatePosition(),4e-8);assertEquals(first.get("STATE6"),governor.getWaterFlow(),5e-7);
        assertEquals(first.get("VAR4"),governor.getTurbineHead(),2e-6);assertEquals(first.get("PMECH"),governor.getOutput(machine),2e-6);
        double expectedHead=Math.pow(governor.getWaterFlow()/governor.gateCurve(governor.getGatePosition()),2);
        assertEquals(expectedHead,governor.getTurbineHead(),1e-14);
        com.interpss.dstab.controller.runtime.IntegrationStateAccess diagnostic=governor;
        var snapshot=diagnostic.getStateDerivativeSnapshot();
        assertEquals(staggered ? com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.STAGGERED
                : com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.INITIALIZATION,snapshot.stage());
        assertEquals(diagnostic.getIntegrationStates(),snapshot.evaluationStates());
        assertEquals(snapshot.evaluationStates().keySet(),snapshot.rates().keySet());
        assertTrue(snapshot.evaluationStates().values().stream().allMatch(Double::isFinite));
        assertTrue(snapshot.rates().values().stream().allMatch(Double::isFinite));
        var rates=snapshot.rates();
        assertEquals((data.getH0()-expectedHead)/governor.getEffectiveTw(),rates.get("Water flow"),1e-12);
        assertTrue(Math.abs(rates.get("Water flow"))>.1,"Infeasible equilibrium must not be silently flattened");
        assertEquals(0.,rates.get("Gate"),0.);assertTrue(Math.abs(rates.get("Valve"))>1.);
        var state=governor.getIntegrationStates();for(int i=0;i<5;i++)governor.getOutput(machine);assertEquals(state,governor.getIntegrationStates());
    }
}
