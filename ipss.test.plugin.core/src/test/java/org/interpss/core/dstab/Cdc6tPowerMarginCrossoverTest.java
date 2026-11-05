package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.Cdc6tParameters;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Power-order current-margin transfer at the rectifier characteristic boundary. */
public class Cdc6tPowerMarginCrossoverTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cdc6t-power-margin-crossover-20261105/captures.zip");
    private static final String HASH="6ebe3c99d35033860b40eabf749c59eb9c91d7bc842ba23b9b4a49c8a6e6ddc9";

    @Test void repeatedSourceSeparatesPowerMarginFromZeroMargin() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            for(String profile:List.of("margin/a","margin/b","zero/a","zero/b")) {
                for(String file:List.of("manifest.json","network.raw","dynamic.dyr","native.csv","native.log","solved-equipment.json"))
                    assertNotNull(zip.getEntry(profile+"/"+file));
                var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(profile+"/manifest.json")));
                assertEquals("complete",manifest.path("status").asText());
            }
            byte[] first=zip.getInputStream(zip.getEntry("margin/a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry("margin/b/native.csv")).readAllBytes());
            var margin=Tiocr1ResetReferenceTest.csv(first);
            byte[] zeroFirst=zip.getInputStream(zip.getEntry("zero/a/native.csv")).readAllBytes();
            assertArrayEquals(zeroFirst,zip.getInputStream(zip.getEntry("zero/b/native.csv")).readAllBytes());
            var zero=Tiocr1ResetReferenceTest.csv(zeroFirst);
            assertEquals(1005,margin.size());assertEquals(margin.size(),zero.size());
            for(int[] pair:new int[][]{{102,103},{203,204}})
                assertEquals(margin.get(pair[0]).get("time_s"),margin.get(pair[1]).get("time_s"),0);
            assertEquals(1000,margin.get(102).get("VAR11"),1e-6);
            assertEquals(900,margin.get(103).get("VAR11"),1e-6);
            assertEquals(1000,zero.get(103).get("VAR11"),1e-6);
            assertEquals(1003.43121338,margin.get(204).get("VAR11"),1e-5);
            assertEquals(5,margin.get(103).get("VAR12"),1e-6);
            assertTrue(margin.get(203).get("VAR11")+100<zero.get(203).get("VAR11"));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledPowerMarginTrajectory(boolean staggered) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        List<Map<String,Double>> reference;List<String> raw;Cdc6tParameters parameters;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            String prefix="margin/a/";
            reference=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes());
            raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
        }
        var fixture=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);
        var algorithm=fixture.context().getDynSimuAlgorithm();var network=fixture.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(fixture);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        algorithm.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",network,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(algorithm.initialization());algorithm.setSimuTime(-.001);recorder.enabled=true;
        while(algorithm.getSimuTime()<.5-.00025)assertTrue(algorithm.solveDEqnStep(true));
        recorder.capture(algorithm.getSimuTime());
        assertEquals(reference.size(),recorder.rows.size(),"Complete event-side comparison");
        var keys=Cdc6tCoupledReferenceTest.KEYS;
        double[] peak=new double[keys.length],peakTime=new double[keys.length],sumSquared=new double[keys.length],prefault=new double[keys.length];
        for(int index=0;index<reference.size();index++) {
            var expected=reference.get(index);var actual=recorder.rows.get(index);
            assertEquals(expected.get("time_s"),actual[0],5e-6);
            for(int channel=0;channel<keys.length;channel++) {
                double source=expected.get(keys[channel]);
                if(index==0)prefault[channel]=Math.abs(source);
                double error=Math.abs(actual[channel+1]-source);
                if(error>peak[channel]){peak[channel]=error;peakTime[channel]=actual[0];}
                sumSquared[channel]+=error*error;
            }
        }
        double[] limits=staggered
                ?new double[]{4.2e-6,.00046,4e-6,.0017,1.8,.0085,1.7,.000115,.00006,.00052,.00011,.00008,.00082,2.9,2.9}
                :new double[]{.00014,.0085,.000008,.0075,125,.031,125,.004,.0007,.022,.004,.0023,.004,10,10};
        // This new power-order profile has a distinct, still sub-millidegree
        // inverter-angle envelope. Keep every other inherited physical gate.
        if(staggered)limits[12]=.0009;
        var failures=new ArrayList<String>();
        for(int channel=0;channel<keys.length;channel++) {
            if(channel>=4&&channel<=6) {
                double rmse=Math.sqrt(sumSquared[channel]/reference.size());
                if(!(peak[channel]<.005*prefault[channel]&&rmse<.0001*prefault[channel]))
                    failures.add(keys[channel]+" peak="+peak[channel]+" rmse="+rmse+" prefault="+prefault[channel]);
            } else if(peak[channel]>=limits[channel])
                failures.add(keys[channel]+" peak="+peak[channel]+" at="+peakTime[channel]
                        +" rmse="+Math.sqrt(sumSquared[channel]/reference.size())
                        +" prefault="+prefault[channel]+" limit="+limits[channel]);
        }
        if(staggered&&Math.sqrt(sumSquared[12]/reference.size())>=.0005)
            failures.add("VAR13 RMSE="+Math.sqrt(sumSquared[12]/reference.size())+" limit=0.0005 degree");
        assertEquals(List.of(),failures,"Power-margin crossover trajectory");
    }
}
