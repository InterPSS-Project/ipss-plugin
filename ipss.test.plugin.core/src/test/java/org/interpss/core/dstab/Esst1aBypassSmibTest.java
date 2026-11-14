package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Esst1aBypassReference;
import org.interpss.core.dstab.reference.GensalNativeReference;
import com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Zero-time outputs are algebraic observations, not dormant CML storage. */
public class Esst1aBypassSmibTest extends CorePluginTestSetup {
    static GensaeFieldSmibTest.Fixture load(String mode,Path directory)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(x->x.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        double[] c={.02,.8,-.8,.2,.8,.1,.4,80,.04,99,-99,7.3,-7.3,0,.1,1,0,1.3};
        switch(mode) {
            case "zero-transducer" -> c[0]=0;
            case "zero-regulator" -> c[8]=0;
            case "first-bypass" -> {c[3]=0;c[4]=0;}
            case "second-bypass" -> {c[5]=0;c[6]=0;}
            case "zero-feedback" -> c[14]=0;
            case "all-bypass" -> {for(int index:new int[]{0,3,4,5,6,8,14})c[index]=0;}
            default -> throw new IllegalArgumentException(mode);
        }
        var input=new StringBuilder(p.input()).append("1 'ESST1A' '1' 1 1 ");
        for(double value:c)input.append(value).append(' ');
        input.append("/\n");
        return GensaeFieldSmibTest.load(p.network(),input.toString(),p.step(),false,directory);
    }

    @Test void zeroTimeSnapshotsFollowAlgebraicOutputsWithoutChangingStoredStates(@TempDir Path directory)throws Exception {
        for(String mode:List.of("zero-transducer","zero-regulator","all-bypass")) {
            var f=load(mode,directory.resolve(mode));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());
            exc.setRefPoint(exc.getRefPoint()+.02);
            for(int tick=0;tick<400;tick++) {
                assertTrue(f.algorithm().solveDEqnStep(true));
                var generic=exc.getNamedStates();var observed=exc.getNativeStateSnapshot();
                if(exc.tr==0)assertEquals(exc.getSensedVoltage(),observed.get("Measured voltage"),1e-14,"Zero-time transducer reports its algebraic output");
                if(exc.ta==0)assertEquals(exc.getRegulatorOutput(),observed.get("Regulator output"),1e-14,"Zero-time regulator reports its algebraic output");
                double tr=exc.tr,ta=exc.ta;exc.tr=99;exc.ta=99;
                assertEquals(observed,exc.getNativeStateSnapshot(),"Observation uses running blocks, not edited configuration");
                exc.tr=tr;exc.ta=ta;
                assertEquals(generic,exc.getNamedStates(),"Observation must leave integration storage untouched");
            }
        }
    }

    @Test void bypassedLeadLagsHaveZeroPublishedStorageAndUnityOutput(@TempDir Path directory)throws Exception {
        for(String mode:List.of("first-bypass","second-bypass","all-bypass")) {
            var f=load(mode,directory.resolve(mode));var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());
            exc.setRefPoint(exc.getRefPoint()+.02);
            for(int tick=0;tick<400;tick++) {
                assertTrue(f.algorithm().solveDEqnStep(true));var observed=exc.getNativeStateSnapshot();
                if(exc.tb==0){assertEquals(0,observed.get("First lead-lag"),0);assertEquals(exc.getUelGate1Output(),exc.getLeadLagOutput(),1e-14);}
                if(exc.tb1==0){assertEquals(0,observed.get("Second lead-lag"),0);assertEquals(exc.getLeadLagOutput(),exc.getLeadLag1Output(),1e-14);}
            }
        }
    }

    @Test void disabledFeedbackRemainsZeroInTheCoupledSolver(@TempDir Path directory)throws Exception {
        for(String mode:List.of("zero-feedback","all-bypass")) {
            var f=load(mode,directory.resolve(mode));var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());
            assertEquals(1,exc.tf,0);exc.setRefPoint(exc.getRefPoint()+.02);
            for(int tick=0;tick<1000;tick++) {
                assertTrue(f.algorithm().solveDEqnStep(true));
                assertEquals(0,exc.getRateFeedback(),0);assertEquals(0,exc.getNativeStateSnapshot().get("Feedback"),0);
            }
        }
    }

    @Test void completeNativeHistoriesRetainBypassMeaningAndLiveEndpoints() {
        assertEquals(12,Esst1aBypassReference.profiles().size());
        for(var p:Esst1aBypassReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(26,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            assertEquals((double)(float)p.step(),p.nativeStep(),0);
            assertEquals(p.liveFinalWashout(),rows.getLast().get("EXC_FEEDBACK"),1e-11);
            int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
            for(var row:rows) {
                assertTrue(row.get("time_s")>=previous);if(row.get("time_s")==previous)duplicates++;previous=row.get("time_s");
                for(double value:row.values())assertTrue(Double.isFinite(value));
                if(p.name().startsWith("zero-transducer") || p.name().startsWith("all-bypass"))
                    assertEquals(row.get("V_BUS1"),row.get("EXC_VMEAS"),0,"Native zero-time transducer is algebraic");
                if(p.name().startsWith("first-bypass") || p.name().startsWith("all-bypass"))assertEquals(0,row.get("EXC_LL1"),0);
                if(p.name().startsWith("second-bypass") || p.name().startsWith("all-bypass"))assertEquals(0,row.get("EXC_LL2"),0);
                if(p.name().startsWith("zero-feedback") || p.name().startsWith("all-bypass"))assertEquals(0,row.get("EXC_FEEDBACK"),0);
            }
            assertEquals(2,duplicates);
            assertEquals(p.liveFinalTime(),previous,6e-12,"Keep the actual native endpoint rather than assume its floating clock reaches the requested time");
            assertTrue(Math.abs(previous-p.end())<p.step(),"Native stopping time remains within one entered step of the request");
            assertEquals(p.increment(),rows.getLast().get("VREF")-rows.getFirst().get("VREF"),1e-7);
        }
    }

    @Test void allProfilesInitializeAndRetainCompleteCoupledDiagnostics(@TempDir Path directory)throws Exception {
        for(var p:Esst1aBypassReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());double reference=exc.getRefPoint();
            var initial=GensaeFieldSmibTest.snapshot(f,0);var trace=new StringBuilder(String.join(",",initial.keySet())+"\n");
            var points=new ArrayList<Map<String,Double>>();
            int ticks=(int)Math.ceil(Math.max(p.end(),rows.getLast().get("time_s"))/p.step());
            f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            for(int tick=0;tick<=ticks;tick++) {
                if(tick==Math.round(.05/p.step()))exc.setRefPoint(reference+p.increment());
                var row=GensaeFieldSmibTest.snapshot(f,tick*p.step());points.add(row);boolean first=true;
                for(double value:row.values()){assertTrue(Double.isFinite(value));if(!first)trace.append(',');trace.append(value);first=false;}trace.append('\n');
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            var maximum=new LinkedHashMap<String,Double>();
            var diagnostic=new StringBuilder("time_s,channel,native_value,coupled_value\n");
            for(var expected:rows) {
                double position=expected.get("time_s")<0?0:expected.get("time_s")/p.step();
                int lower=(int)Math.floor(position),upper=Math.min(points.size()-1,lower+1);double fraction=position-lower;
                assertTrue(lower>=0 && lower<points.size());
                for(String key:expected.keySet())if(!key.equals("time_s")) {
                    double actual=points.get(lower).get(key)+fraction*(points.get(upper).get(key)-points.get(lower).get(key));
                    maximum.merge(key,Math.abs(actual-expected.get(key)),Math::max);
                    diagnostic.append(expected.get("time_s")).append(',').append(key).append(',').append(expected.get(key)).append(',').append(actual).append('\n');
                }
            }
            write("coupled",p.name(),trace);write("diagnostic",p.name(),diagnostic);
            System.out.println("ESST1A bypass diagnostic "+p.name()+" all-channel maxima="+maximum);
        }
    }

    private static void write(String kind,String name,StringBuilder text)throws Exception {
        Path path=Path.of("target/esst1a-bypass-"+kind,name+".csv");Files.createDirectories(path.getParent());Files.writeString(path,text);
    }
}
