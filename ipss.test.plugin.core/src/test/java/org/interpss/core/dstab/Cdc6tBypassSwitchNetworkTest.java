package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.*;
import com.interpss.dstab.hvdc.model.Cdc6tNormalRuntime.OperatingMode;
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
        return java.util.stream.Stream.of("dc","ac","long","short","dc-half","ac-half","short-half")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void sourcePreparedProtectionControlsTheCoupledNetwork(String profileName,boolean staggered)throws Exception{
        boolean half=profileName.endsWith("-half");
        String profile=half?profileName.substring(0,profileName.length()-5):profileName;
        boolean switching=profile.equals("long")||profile.equals("short");
        double step=half?.00025:.0005,clear,modeThreshold;
        String group=half?"release-half":switching?"mode-switch":"bypass";
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent bypass and mode-switch controls");
        assertEquals(half?"c213f1d75cfd32f46652fabe6490e1d08e65ad61e05572670c742d9a575bd078":switching?"28b4d06ee2aa18d3d052a7240032a6ec3f804dbbb4849a39d32e2d84c87b7ad8"
                :"4af03eff4e7d3699b4eba5bce4088e796625179c81a460ab40362d9eaae2fda7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        try(var z=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            var settings=new com.fasterxml.jackson.databind.ObjectMapper().readTree(z.getInputStream(z.getEntry(prefix+"manifest.json"))).get("settings");
            // The earlier immutable bypass probe disables mode transfer and has no threshold setting.
            clear=settings.get("fault_clear").doubleValue();
            if(settings.has("step"))assertEquals(step,settings.get("step").doubleValue(),0);
            else assertFalse(half,"Half-step evidence must declare its interval");
            modeThreshold=switching?settings.get("mode_threshold_kv").doubleValue():0;
            assertTrue(clear>.05&&clear<.5);assertEquals("inverter",settings.get("fault_terminal").textValue());
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var rawCapture=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(rawCapture,z.getInputStream(z.getEntry(profile+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(rawCapture);
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var dc=records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).toList();assertEquals(1,dc.size());
            parameters=Cdc6tRecordMapper.prepare(dc.get(0));
        }
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);
        var transitions=new ArrayList<Cdc6tNetworkTransition>();
        if(profile.equals("short"))f.ports().setTransitionObserver(transitions::add);
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
        var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-bypass-switch-network")),profileName+"-"+(staggered?"staggered-":"euler-"));
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
        if(switching){
            double deadline=reference.stream().filter(r->r.get("VAR14")<90000)
                    .findFirst().orElseThrow().get("VAR14");
            assertEquals(.05-.5*step+.02,deadline,1e-6,
                    "Independent pickup timer uses the preceding midpoint, not a universal positive clock offset");
        }else{
            var recoveryObservation=reference.stream().filter(r->r.get("time_s")>clear&&r.get("VAR17")<90000)
                    .findFirst().orElseThrow();
            assertEquals(clear+step,recoveryObservation.get("time_s"),1e-6,
                    "Independent recovery timer is first visible at the following endpoint");
            assertEquals(clear+.5*step+.01,recoveryObservation.get("VAR17"),1e-6,
                    "Independent recovery deadline starts at the following midpoint");
            assertEquals(clear+.01+step,expectedReturn,1e-6);
        }
        assertEquals(expectedActive,firstActive,2.2*step,"First protected transition");
        assertEquals(expectedReturn,firstReturn,2.2*step,"Return from protected operation");
        if(profile.equals("short")){
            var sourceSides=reference.stream().filter(r->Math.abs(r.get("time_s")-expectedReturn)<step/4).toList();
            assertEquals(2,sourceSides.size(),"Retain both independent return observations");
            for(String state:List.of("STATE0","STATE1","STATE2"))
                assertEquals(sourceSides.get(0).get(state),sourceSides.get(1).get(state),0,
                        "The independent internal action does not integrate continuous state");
            assertEquals(1000.,sourceSides.get(0).get("VAR11"),0);
            assertTrue(sourceSides.get(1).get("VAR11")>1000.,"The post-action current order changes on the second side");
            var returns=transitions.stream().filter(t->t.beforeMode()==OperatingMode.POWER_TO_CURRENT
                    &&t.afterMode()==OperatingMode.NORMAL).toList();
            assertEquals(1,returns.size(),"Return must publish one accepted internal transition");
            var transition=returns.get(0);
            assertEquals(expectedReturn,transition.time(),2.2*step);
            assertEquals(transition.before().measuredInverterVolts(),transition.after().measuredInverterVolts(),0,
                    "An internal mode switch cannot advance continuous measurement storage");
            assertEquals(transition.before().measuredAmperes(),transition.after().measuredAmperes(),0);
            assertEquals(transition.before().measuredRectifierVolts(),transition.after().measuredRectifierVolts(),0);
            double beforeVoltage=Math.hypot(transition.before().rectifierReal(),transition.before().rectifierImaginary());
            double afterVoltage=Math.hypot(transition.after().rectifierReal(),transition.after().rectifierImaginary());
            double beforeInverter=Math.hypot(transition.before().inverterReal(),transition.before().inverterImaginary());
            double afterInverter=Math.hypot(transition.after().inverterReal(),transition.after().inverterImaginary());
            assertTrue(Math.abs(beforeVoltage-sourceSides.get(0).get("V7"))<5e-6);
            assertTrue(Math.abs(afterVoltage-sourceSides.get(1).get("V7"))<5e-6);
            assertTrue(Math.abs(beforeInverter-sourceSides.get(0).get("V9"))<6e-6);
            assertTrue(Math.abs(afterInverter-sourceSides.get(1).get("V9"))<6e-6);
            assertTrue(Math.abs(transition.before().rectifierP()-sourceSides.get(0).get("VAR5"))<3e-5);
            assertTrue(Math.abs(transition.after().rectifierP()-sourceSides.get(1).get("VAR5"))<3e-5);
            assertTrue(Math.abs(beforeVoltage-sourceSides.get(1).get("V7"))>.002,
                    "The previous nearest-side diagnostic paired different physical regimes");
            System.out.println("CDC6T short return sides staggered="+staggered+" h="+step+
                    " before/after V7="+beforeVoltage+"/"+afterVoltage+
                    " reference="+sourceSides.get(0).get("V7")+"/"+sourceSides.get(1).get("V7")+
                    " V9="+beforeInverter+"/"+afterInverter+
                    " reference="+sourceSides.get(0).get("V9")+"/"+sourceSides.get(1).get("V9")+
                    " Pr="+transition.before().rectifierP()+"/"+transition.after().rectifierP()+
                    " reference="+sourceSides.get(0).get("VAR5")+"/"+sourceSides.get(1).get("VAR5"));
        }
        assertTrue(activeSamples>=35);assertEquals(OperatingMode.NORMAL,f.protection().operatingMode());
        System.out.println("CDC6T coupled "+profileName+" staggered="+staggered+" active/return="+firstActive+"/"+firstReturn);
    }
    private static boolean isActive(Map<String,Double> row,boolean switching){
        return switching?row.get("VAR14")<90000:row.get("VAR7")==0&&row.get("VAR5")>0;
    }
}
