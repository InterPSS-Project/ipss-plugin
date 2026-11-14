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

/** An active initial current ceiling changes the prepared operating point, not a compensation current. */
public class Cdc6tInitialCeilingTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("flat","fault").flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void activeInitialCeilingSettlesStatesAndPhysicalNetwork(String profile,boolean staggered)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-initial-ceiling-20261104/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent initial-ceiling captures");
        assertEquals("9132db7969c3510d50e4efccf9152edcc8eebb5234e6d273dd0872393a54d5d5",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        try(var zip=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            byte[] data=zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(data,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(data);
            raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
        }
        double[] constants=parameters.copySourceConstants();
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);recorder.recordInternalTransitions();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        if(profile.equals("fault"))n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"CeilingFault");
        assertTrue(a.initialization());double[] initial=new double[3],rates=new double[3];
        f.runtime().readStaggeredState(initial,0);f.ports().rectifierPort().evaluateStaggered(.0005,rates,0);
        a.setSimuTime(-.001);recorder.enabled=true;
        while(a.getSimuTime()<.5-.00025)assertTrue(a.solveDEqnStep(true));
        recorder.capture(a.getSimuTime());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-initial-ceiling")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] maximum=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,.0005);
        assertArrayEquals(constants,parameters.copySourceConstants(),0,"Initialization cannot modify entered limit coefficients");
        assertArrayEquals(new double[]{500000,900,504500},initial,.001,"Initialize to the physically limited current and voltage drop");
        for(double rate:rates)assertEquals(0,rate,.001,"Initial limited operating point must be stationary");
        assertTrue(maximum[0]<1e-5&&maximum[2]<1e-5,"Complete terminal-voltage reference agreement");
        // Retain the ordinary conducting reference envelope for every remaining channel.
        double[] tolerance={.00014,.0085,.000008,.0075,125,.031,125,.004,.0007,.022,.004,.0023,.004,10,10};
        for(int j=0;j<maximum.length;j++)assertTrue(maximum[j]<tolerance[j],
                Cdc6tCoupledReferenceTest.KEYS[j]+" maximum error "+maximum[j]);
    }
}
