package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Esst1aBypassReference;
import org.interpss.core.dstab.reference.EventSideTrajectory;
import org.interpss.core.dstab.reference.EventSideTrajectory.Side;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Explicit event-side comparisons retain every native history row. */
public class Esst1aEventSideSmibTest extends CorePluginTestSetup {
    private static final List<String> CONTINUOUS=List.of("EP_Q","PSIQ_PP","PSIK_D","SPEED_DEV","ANGLE","EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_FEEDBACK","EFD");
    private static int eventIndex(List<Map<String,Double>> rows,double step) {
        int result=-1;
        for(int i=1;i<rows.size();i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s")) && Math.abs(rows.get(i).get("time_s")-.05)<.5*step) {
            assertEquals(-1,result,"Only one reference-step boundary");result=i-1;
        }
        assertTrue(result>=0,"Both native event sides must be retained");return result;
    }
    @Test void nativeEventPairsSeparateAlgebraicAndContinuousChannels() {
        for(var p:Esst1aBypassReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());int index=eventIndex(rows,p.step());
            var before=rows.get(index);var after=rows.get(index+1);
            assertEquals(p.increment(),after.get("VREF")-before.get("VREF"),1e-7);
            for(String key:CONTINUOUS)assertEquals(before.get(key),after.get(key),0,p.name()+" continuous/cached "+key);
            double feed=p.name().startsWith("all-bypass")?80:p.name().startsWith("zero-regulator")?5:0;
            double jump=after.get("EXC_VA")-before.get("EXC_VA");
            double roundoff=8*Math.ulp((float)Math.max(Math.abs(before.get("EXC_VA")),Math.abs(after.get("EXC_VA"))))+1e-8;
            assertEquals(feed*(after.get("VREF")-before.get("VREF")),jump,roundoff,"Declared feedthrough, binary32 rounding and CSV quantization only");
        }
    }
    @Test void compareEveryRawChannelWithExplicitBeforeAndAfterSnapshots(@TempDir Path directory)throws Exception {
        for(var p:Esst1aBypassReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());int nativeEvent=eventIndex(rows,p.step());
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());double reference=exc.getRefPoint();
            var points=new ArrayList<Map<String,Double>>();int eventTick=(int)Math.round(.05/p.step());
            int ticks=(int)Math.ceil(Math.max(p.end(),rows.getLast().get("time_s"))/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            for(int tick=0;tick<=ticks;tick++) {
                if(tick==eventTick) {
                    assertTrue(Double.isFinite(exc.getOutput(f.machine())),"Use the existing state-preserving algebraic evaluation");
                    var before=GensaeFieldSmibTest.snapshot(f,tick*p.step());points.add(before);
                    var storage=exc.getNamedStates();exc.setRefPoint(reference+p.increment());
                    double output=exc.getOutput(f.machine());assertTrue(Double.isFinite(output));
                    for(int read=0;read<20;read++)assertEquals(output,exc.getOutput(f.machine()),1e-13,"A settled algebraic output is independent of read count");
                    var after=GensaeFieldSmibTest.snapshot(f,tick*p.step());
                    assertEquals(storage,exc.getNamedStates(),"Reference event cannot advance integration storage");
                    for(String key:CONTINUOUS)assertEquals(before.get(key),after.get(key),0,p.name()+" same-tick "+key);
                    double feed=exc.ta==0?exc.ka*(exc.tb==0?1:exc.tc/exc.tb)*(exc.tb1==0?1:exc.tc1/exc.tb1):0;
                    double closedLoop=feed/(1+feed*exc.kf/exc.tf);
                    assertEquals(closedLoop*p.increment(),after.get("EXC_VA")-before.get("EXC_VA"),1e-11,
                            "Settled algebraic response includes washout feedthrough feedback, not just the forward path");
                }
                points.add(GensaeFieldSmibTest.snapshot(f,tick*p.step()));
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            var history=new EventSideTrajectory(points);var maximum=new LinkedHashMap<String,Double>();
            var text=new StringBuilder("raw_row,time_s,query_time_s,event_side,channel,native_value,coupled_value\n");
            for(int i=0;i<rows.size();i++) {
                var expected=rows.get(i);double time=expected.get("time_s"),query=time;Side side=Side.AFTER;String label="CONTINUOUS";
                if(time<0){query=0;side=Side.BEFORE;label="INITIAL_HISTORY";}
                else if(i==nativeEvent || i==nativeEvent+1){query=eventTick*p.step();side=i==nativeEvent?Side.BEFORE:Side.AFTER;label=side.name();}
                var actual=history.sample(query,side);
                for(String key:expected.keySet())if(!key.equals("time_s")) {
                    assertTrue(Double.isFinite(actual.get(key)));maximum.merge(key,Math.abs(actual.get(key)-expected.get(key)),Math::max);
                    text.append(i).append(',').append(time).append(',').append(query).append(',').append(label).append(',').append(key).append(',').append(expected.get(key)).append(',').append(actual.get(key)).append('\n');
                }
            }
            var coupled=new StringBuilder(String.join(",",points.getFirst().keySet())+"\n");
            for(var row:points){boolean first=true;for(double value:row.values()){if(!first)coupled.append(',');coupled.append(value);first=false;}coupled.append('\n');}
            write("coupled",p.name(),coupled);write("diagnostic",p.name(),text);
            System.out.println("ESST1A explicit event-side diagnostic "+p.name()+" maxima="+maximum);
        }
    }
    private static void write(String kind,String name,StringBuilder text)throws Exception {
        Path path=Path.of("target/esst1a-event-side-"+kind,name+".csv");Files.createDirectories(path.getParent());Files.writeString(path,text);
    }
}
