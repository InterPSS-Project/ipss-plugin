package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.Cdc6tParameters;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent short-lag boundaries, including excited algebraic measurement feedback. */
public class Cdc6tTransducerBoundaryTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("zero","short","one","two","equal")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void sourceTimesAndCoupledObservationsUseThePreparedOuterInterval(String profile,boolean staggered)throws Exception{
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-short-transducers-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent transducer boundary captures");
        assertEquals("70102ccd87680e741dbb251ad9e9b08fb2d6994cc95a6dfc19e5316b0e6eedf1",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        try(var zip=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            byte[] capture=zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(capture,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(capture);
            raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(prefix+"manifest.json")));
            for(int index:new int[]{2,3,31})assertEquals(parameters.copySourceConstants()[index],manifest.get("initialized_constants").get(index).doubleValue(),1e-9,
                    "Independent preparation preserves source constants, including zero");
        }
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-.001);recorder.enabled=true;
        while(a.getSimuTime()<.5-.00025)assertTrue(a.solveDEqnStep(true),"Coupled short-lag solve at "+a.getSimuTime());
        recorder.capture(a.getSimuTime());assertEquals(reference.size(),recorder.rows.size());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-short-transducers")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] maxima=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,.0005);
        boolean allBypassed=profile.equals("zero")||profile.equals("short")||profile.equals("one");
        if(allBypassed||staggered){
            double[] limits={3.5e-6,.00026,4e-6,.0017,.25,.0007,.25,
                    .000003,.000043,.00049,.000003,.000045,.00048,.25,.25};
            for(int j=0;j<limits.length;j++)assertTrue(maxima[j]<limits[j],Cdc6tCoupledReferenceTest.KEYS[j]+" full-trajectory error "+maxima[j]);
        }else if(profile.equals("two")){
            // A fixed-current control isolates the one remaining differential transducer.
            // Boundary/bypassed coordinates still have strict independent acceptance.
            for(int j:new int[]{0,2})assertTrue(maxima[j]<4e-6);
            assertTrue(maxima[4]<.25);assertEquals(0,maxima[5],0);
        }
        // At equality/above the threshold, modified-Euler differential trajectory accuracy
        // remains distinct from the independently accepted algebraic preparation boundary.
    }
}
