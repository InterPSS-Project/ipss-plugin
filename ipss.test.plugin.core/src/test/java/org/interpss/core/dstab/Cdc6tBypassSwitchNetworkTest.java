package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.aclf.hvdc.LccSourceControls;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent coupled bypass/recovery and temporary current-control checks under both methods. */
public class Cdc6tBypassSwitchNetworkTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("dc","ac","long","short")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void sourcePreparedProtectionControlsTheCoupledNetwork(String profile,boolean staggered)throws Exception{
        boolean switching=profile.equals("long")||profile.equals("short");
        double step=.0005,clear,modeThreshold;
        String group=switching?"mode-switch":"bypass";
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent bypass and mode-switch controls");
        assertEquals(switching?"28b4d06ee2aa18d3d052a7240032a6ec3f804dbbb4849a39d32e2d84c87b7ad8"
                :"4af03eff4e7d3699b4eba5bce4088e796625179c81a460ab40362d9eaae2fda7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        try(var z=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            var settings=new com.fasterxml.jackson.databind.ObjectMapper().readTree(z.getInputStream(z.getEntry(prefix+"manifest.json"))).get("settings");
            // The earlier immutable bypass probe disables mode transfer and has no threshold setting.
            clear=settings.get("fault_clear").doubleValue();
            modeThreshold=switching?settings.get("mode_threshold_kv").doubleValue():0;
            assertTrue(clear>.05&&clear<.5);assertEquals("inverter",settings.get("fault_terminal").textValue());
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            reference=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var dc=records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).toList();assertEquals(1,dc.size());
            parameters=Cdc6tRecordMapper.prepare(dc.get(0));
        }
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);
        var owner=(com.interpss.core.aclf.hvdc.HvdcLine2TLCC<?>)f.context().getDStabilityNet().getSpecialBranchList().get(0);
        assertEquals(modeThreshold,((LccSourceControls)owner.getExtensionObject()).modeTransferKilovolts(),0);
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,.05,clear);
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,clear-.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        double firstActive=Double.NaN,firstReturn=Double.NaN;int activeSamples=0;
        double[] history=new double[23],boundary=new double[8];
        OperatingMode active=switching?OperatingMode.POWER_TO_CURRENT:OperatingMode.BYPASSED;
        while(a.getSimuTime()<.5-.5*step){
            assertTrue(a.solveDEqnStep(true),"Coupled protected solve at "+a.getSimuTime());
            double t=a.getSimuTime();f.protection().readAcceptedHistory(history,0);assertEquals(t,history[0],1e-12);
            var mode=f.protection().operatingMode();assertNotEquals(OperatingMode.BLOCKED,mode);
            if(profile.equals("short")&&t>clear+step&&t<.05+.02-step)
                assertEquals(OperatingMode.POWER_TO_CURRENT,mode,"Current mode persists after early fault clearing until its minimum duration");
            f.ports().rectifierPort().getOutputObject();f.runtime().readBoundary(boundary,0);
            if(mode==active){
                if(Double.isNaN(firstActive))firstActive=t;activeSamples++;
                assertEquals(1000,boundary[0]*1e8/boundary[6],1e-8,"Scheduled current during bypass or temporary current control");
                if(!switching){assertEquals(0,boundary[3],0);assertEquals(0,boundary[4],0);assertEquals(5000,boundary[6],1e-8);}
            }else if(!Double.isNaN(firstActive)&&Double.isNaN(firstReturn))firstReturn=t;
            if(mode==OperatingMode.RECOVERING){assertFalse(switching);assertEquals(1,f.protection().currentFactor(),0,"Bypass recovery ramps voltage only");}
        }
        recorder.capture(a.getSimuTime());
        var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-bypass-switch-network")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] maxima=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,step);
        if(profile.equals("long")){
            assertEquals(reference.size(),recorder.rows.size(),"Complete samples and both scheduled event sides");
            // Measured full-trajectory envelopes, preserving each method's event-stage response.
            double[] bounds=staggered
                    ?new double[]{3.2e-6,.00026,4e-6,.0017,1.5,.0012,1.5,.000018,.000046,.00043,.000017,.000046,.00049,.17,.17}
                    :new double[]{.000049,.0029,.0000061,.0049,122,.16,123,.0014,.000094,.0089,.0014,.00086,.00094,1.37,.17};
            for(int j=0;j<bounds.length;j++)assertTrue(maxima[j]<bounds[j],Cdc6tCoupledReferenceTest.KEYS[j]+" full-trajectory error "+maxima[j]);
        }
        double expectedActive=reference.stream().filter(r->isActive(r,switching)).mapToDouble(r->r.get("time_s")).min().orElseThrow();
        double expectedReturn=reference.stream().filter(r->r.get("time_s")>expectedActive&&!isActive(r,switching)).mapToDouble(r->r.get("time_s")).min().orElseThrow();
        assertEquals(expectedActive,firstActive,2.2*step,"First protected transition");
        assertEquals(expectedReturn,firstReturn,2.2*step,"Return from protected operation");
        assertTrue(activeSamples>=35);assertEquals(OperatingMode.NORMAL,f.protection().operatingMode());
        System.out.println("CDC6T coupled "+profile+" staggered="+staggered+" active/return="+firstActive+"/"+firstReturn);
    }
    private static boolean isActive(Map<String,Double> row,boolean switching){
        return switching?row.get("VAR14")<90000:row.get("VAR7")==0&&row.get("VAR5")>0;
    }
}
