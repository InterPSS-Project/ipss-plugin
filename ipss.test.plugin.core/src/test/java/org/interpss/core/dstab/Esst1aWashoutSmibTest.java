package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GensalNativeReference;
import org.interpss.core.dstab.reference.Esst1aWashoutReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Published washout observation is distinct from the unchanged CML integration coordinate. */
public class Esst1aWashoutSmibTest extends CorePluginTestSetup {
    @Test void publishedWashoutStorageIsTimeScaledWithoutChangingGenericStateKeys(@TempDir Path directory)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(profile->profile.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        for(double tau:List.of(.5,1.,2.)) {
            String input=p.input()+"1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 2 -99 7.3 -7.3 0 .1 "+tau+" 0 1.3 /\n";
            var f=GensaeFieldSmibTest.load(p.network(),input,p.step(),false,directory.resolve("tau-"+tau));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());
            var generic=exc.getNamedStates();double observed=exc.getNativeStateSnapshot().get("Feedback");
            assertEquals(exc.tf*generic.get("washoutBlock"),observed,1e-14,"Published coordinate includes Tf");
            assertEquals(exc.kf*f.machine().getEfd(),observed,1e-14,"Native initial storage is Kf*Efd, independent of Tf");
            assertEquals(generic,exc.getNamedStates(),"Observation must not mutate integration storage");
            double configured=exc.tf;exc.tf=99;
            assertEquals(observed,exc.getNativeStateSnapshot().get("Feedback"),0,"Observe the running block, not edited configuration");
            exc.tf=configured;
        }
    }
    @Test void completeHistoriesRetainLiveEndpointsAndExactSchemas() {
        assertEquals(6,Esst1aWashoutReference.profiles().size());
        for(var p:Esst1aWashoutReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(26,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            assertEquals((double)(float)p.step(),p.nativeStep(),0);
            assertEquals(p.liveFinalWashout(),rows.getLast().get("EXC_FEEDBACK"),1e-11,"Live STATE and recorded channel agree");
            int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
            for(var row:rows) {
                assertTrue(row.get("time_s")>=previous);if(row.get("time_s")==previous)duplicates++;previous=row.get("time_s");
                for(double value:row.values())assertTrue(Double.isFinite(value));
            }
            assertEquals(2,duplicates);assertTrue(previous>=p.end());
            assertEquals(p.increment(),rows.getLast().get("VREF")-rows.getFirst().get("VREF"),1e-7);
        }
    }
    @Test void unforcedInitialPublishedStorageMatchesEveryNativeTimeConstant(@TempDir Path directory)throws Exception {
        for(var p:Esst1aWashoutReference.profiles()) {
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());
            var first=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();
            assertEquals(p.tau(),exc.tf,0);assertEquals(2,exc.vamax,0);assertEquals(0,exc.kc,0);assertEquals(0,exc.klr,0);
            assertEquals(first.get("EXC_FEEDBACK"),exc.getNativeStateSnapshot().get("Feedback"),6e-8,p.name()+" initial published coordinate");
            assertEquals(exc.kf*f.machine().getEfd(),exc.getNativeStateSnapshot().get("Feedback"),1e-14);
        }
    }
    @Test void coupledConstantInputFollowsTheAnalyticLagWithinItsMethodBound(@TempDir Path directory)throws Exception {
        for(var p:Esst1aWashoutReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double last=rows.getLast().get("time_s");
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());double reference=exc.getRefPoint();
            int ticks=(int)Math.ceil(Math.max(p.end(),last)/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var first=GensaeFieldSmibTest.snapshot(f,0);var trace=new StringBuilder(String.join(",",first.keySet())+"\n");
            int origin=(int)Math.round(.5/p.step());double initial=Double.NaN,previousObserved=Double.NaN,maximum=0,bound=0;
            for(int tick=0;tick<=ticks;tick++) {
                if(tick==Math.round(.05/p.step()))exc.setRefPoint(reference+p.increment());
                var row=GensaeFieldSmibTest.snapshot(f,tick*p.step());boolean leading=true;
                for(double value:row.values()){assertTrue(Double.isFinite(value));if(!leading)trace.append(',');trace.append(value);leading=false;}trace.append('\n');
                if(tick>=origin) {
                    double observed=row.get("EXC_FEEDBACK"),target=exc.kf*2;
                    assertEquals(2,exc.getRegulatorOutput(),1e-14);assertEquals(2,exc.getPreFieldVoltageSignal(),1e-14);assertEquals(2,row.get("EFD"),1e-14);
                    assertTrue(exc.ka*exc.getLeadLag1Output()>2.1,"Outward regulator drive keeps both stages saturated");
                    if(tick==origin) {
                        initial=observed;double z=p.step()/exc.tf;
                        // Telescoping the constant-input modified-Euler local
                        // Taylor remainder gives this all-time global bound.
                        bound=Math.abs(target-initial)*z*z/(6*(1-z/2))+2e-12;
                    }
                    double expected=target+(initial-target)*Math.exp(-(tick-origin)*p.step()/exc.tf);
                    double error=Math.abs(expected-observed);maximum=Math.max(maximum,error);
                    assertTrue(error<=bound,p.name()+" analytic error="+error+" bound="+bound);
                    if(tick>origin) {
                        double z=p.step()/exc.tf;
                        assertEquals(target+(previousObserved-target)*(1-z+z*z/2),observed,4e-15,
                                "Constant-input stages retain modified Euler, not an implicit corrector");
                    }
                    assertEquals((exc.kf*2-observed)/exc.tf,exc.getRateFeedback(),2e-14,"Transfer function is unchanged by state observation");
                    previousObserved=observed;
                }
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            write("coupled",p.name(),trace);
            System.out.println("ESST1A analytic "+p.name()+" maximum="+maximum+" theory_bound="+bound);
        }
    }
    @Test void nativeConstantSegmentIsRetainedAsAnIndependentNumericalDiagnostic()throws Exception {
        for(var p:Esst1aWashoutReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());int origin=(int)Math.round(.5/p.step()),tick=0;
            double previous=Double.NEGATIVE_INFINITY,initial=Double.NaN,maximum=0;
            var trace=new StringBuilder("time_s,in_constant_segment,native_state,analytic_state\n");
            for(var row:rows) {
                double time=row.get("time_s");if(time>0 && time>previous)tick++;
                boolean constant=time>=0 && tick>=origin;double observed=row.get("EXC_FEEDBACK"),expected=observed;
                if(constant) {
                    assertEquals(2,row.get("EXC_VA"),1e-8);assertEquals(2,row.get("EFD"),1e-8);
                    if(tick==origin)initial=observed;
                    double target=p.nativeKf()*2;expected=target+(initial-target)*Math.exp(-(tick-origin)*p.nativeStep()/p.tau());
                    assertTrue(Double.isFinite(expected));maximum=Math.max(maximum,Math.abs(expected-observed));
                }
                trace.append(time).append(',').append(constant?1:0).append(',').append(observed).append(',').append(expected).append('\n');previous=time;
            }
            write("native-analytic",p.name(),trace);
            System.out.println("ESST1A native analytic diagnostic "+p.name()+" maximum="+maximum);
        }
    }
    private static void write(String kind,String name,StringBuilder text)throws Exception {
        Path output=Path.of("target/esst1a-washout-"+kind,name+".csv");Files.createDirectories(output.getParent());Files.writeString(output,text);
    }
}
