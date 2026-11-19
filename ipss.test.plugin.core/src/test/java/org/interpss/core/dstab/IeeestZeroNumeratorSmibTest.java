package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.IeeestInputReference;
import org.interpss.core.dstab.reference.IeeestZeroNumeratorReference;
import org.interpss.core.dstab.reference.EventSideTrajectory;
import org.interpss.core.dstab.reference.EventSideTrajectory.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** The documented zero numerator selects a lag, not a disabled stabilizer. */
public class IeeestZeroNumeratorSmibTest extends CorePluginTestSetup {
    static String zeroInput(String input,boolean isolateLag) {
        var lines=new ArrayList<>(input.lines().toList());
        for(int i=0;i<lines.size();i++)if(lines.get(i).contains("'IEEEST'")) {
            var fields=lines.get(i).trim().split("\\s+");fields[15]="0";
            if(isolateLag){for(int index=5;index<=14;index++)fields[index]="0";fields[16]=".2";fields[17]="1";}
            lines.set(i,String.join(" ",fields));
        }
        return String.join("\n",lines)+"\n";
    }
    @Test void zeroNumeratorRetainsThePhysicalDcInputAtEquilibrium(@TempDir Path directory)throws Exception {
        var p=IeeestInputReference.profiles().stream().filter(x->x.name().equals("electrical-flat-coarse")).findFirst().orElseThrow();
        var f=IeeestPowerInputSmibTest.load(p.network(),zeroInput(p.input(),false),p.step(),directory);
        var m=f.machine();var pss=f.stabilizer();double voltage=m.getDStabBus().getVoltageMag(),speed=m.getSpeed();
        assertEquals(.05,pss.getOutput(m),1e-14,"A unity-DC lag must not remove the steady electrical-power input");
        for(int step=0;step<1000;step++)assertTrue(f.algorithm().solveDEqnStep(true));
        assertEquals(voltage,m.getDStabBus().getVoltageMag(),1e-9);assertEquals(speed,m.getSpeed(),1e-10);
        assertEquals(.05,pss.getOutput(m),1e-14);
    }
    @Test void isolatedZeroNumeratorFollowsTheExplicitLagRecurrence(@TempDir Path directory)throws Exception {
        var p=IeeestInputReference.profiles().stream().filter(x->x.name().equals("accelerating-flat-coarse")).findFirst().orElseThrow();
        var f=IeeestPowerInputSmibTest.load(p.network(),zeroInput(p.input(),true),p.step(),directory);
        var m=f.machine();var pss=f.stabilizer();m.setPm(m.getPm()+.02);
        double z=p.step()/.2,r=1-z+z*z/2,expected=0;
        for(int step=0;step<1000;step++) {
            assertTrue(pss.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,m,0));
            assertTrue(pss.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,m,1));expected=.02+(expected-.02)*r;
            assertEquals(expected,pss.getOutput(m),2e-14,"Exact modified-Euler lag, not a zero-gain washout");
        }
        assertTrue(pss.getOutput(m)>.01);
    }
    @Test void ordinaryWashoutKeepsTheExistingStateKeysAndZeroDcOutput(@TempDir Path directory)throws Exception {
        var p=IeeestInputReference.profiles().stream().filter(x->x.name().equals("electrical-flat-coarse")).findFirst().orElseThrow();
        var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),directory);
        assertEquals(Set.of("secondOrderLag","secondOrderLeadLag","leadLag1","leadLag2","washout"),f.stabilizer().getNamedStates().keySet());
        assertEquals(0,f.stabilizer().getOutput(f.machine()),1e-12);
    }
    @Test void nativeTerminalStateIsTheUnclippedLagOutput() {
        assertEquals(4,IeeestZeroNumeratorReference.profiles().size());
        for(var p:IeeestZeroNumeratorReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());assertEquals(35,rows.getFirst().size());
            assertEquals(4,p.mode());assertTrue(rows.getFirst().get("time_s")<0);assertEquals((double)(float)p.step(),p.nativeStep(),0);
            int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
            for(var row:rows) {
                assertTrue(row.get("time_s")>=previous);if(row.get("time_s")==previous)duplicates++;previous=row.get("time_s");
                for(double value:row.values())assertTrue(Double.isFinite(value));
                double expected=Math.max((double)(float)-.05,Math.min((double)(float).05,row.get("PSS_LAST")));
                assertEquals(expected,row.get("VOTHSG"),5e-13,"Native T5=0 terminal state is output, not ordinary washout memory");
            }
            assertEquals(2,duplicates);assertTrue(Math.abs(previous-p.end())<p.step());
        }
    }
    @Test void everyRecordedInputReplaysTheTerminalStateAndOutput(@TempDir Path directory)throws Exception {
        var errors=new LinkedHashMap<String,Double>();
        for(var p:IeeestZeroNumeratorReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),directory.resolve(p.name()));
            var m=f.machine();var pss=f.stabilizer();var initial=rows.getFirst();
            m.setPe(initial.get("P_PU"));m.setPm(initial.get("PMECH"));assertTrue(pss.initStates(m.getDStabBus(),m));
            var previous=initial;double previousTime=0,stateMaximum=0,outputMaximum=0;
            var text=new StringBuilder("raw_row,time_s,native_state,replayed_state,native_vstab,replayed_vstab\n");
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s"),dt=time>previousTime?p.nativeStep():0;
                var storage=pss.getNamedStates();m.setPe(previous.get("P_PU"));m.setPm(previous.get("PMECH"));
                assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));
                m.setPe(row.get("P_PU"));m.setPm(row.get("PMECH"));assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));
                if(dt==0)for(var entry:storage.entrySet())assertEquals(entry.getValue(),pss.getNamedStates().get(entry.getKey()),0);
                var before=pss.getNamedStates();double state=pss.getUnclippedOutput(),output=pss.getOutput(m);
                assertEquals(before,pss.getNamedStates(),"Observing terminal output cannot change integration storage");
                assertTrue(Double.isFinite(state));assertTrue(Double.isFinite(output));
                double stateError=Math.abs(state-row.get("PSS_LAST")),outputError=Math.abs(output-row.get("VOTHSG"));
                stateMaximum=Math.max(stateMaximum,stateError);outputMaximum=Math.max(outputMaximum,outputError);
                double bound=p.fault()?(p.step()==.0005?1.4e-5:3.5e-6):1e-12;
                assertTrue(stateError<=bound,p.name()+" terminal state @"+index+" error="+stateError);
                assertTrue(outputError<=bound,p.name()+" Vstab @"+index+" error="+outputError);
                text.append(index).append(',').append(time).append(',').append(row.get("PSS_LAST")).append(',').append(state).append(',').append(row.get("VOTHSG")).append(',').append(output).append('\n');
                if(time>=0){previousTime=time;previous=row;}
            }
            errors.put(p.name(),Math.max(stateMaximum,outputMaximum));
            write("playback",p.name(),text);System.out.println("IEEEST zero-numerator playback "+p.name()+" state="+stateMaximum+" output="+outputMaximum);
        }
        assertTrue(errors.get("accelerating-fault-fine")<.35*errors.get("accelerating-fault-coarse"),"Terminal-state and output errors must refine with the method, not just fit an output envelope");
    }
    @Test void coupledLagProfilesRetainAllDiagnosticRows(@TempDir Path directory)throws Exception {
        for(var p:IeeestZeroNumeratorReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),p.fault(),directory.resolve(p.name()));
            int ticks=(int)Math.ceil(Math.max(p.end(),rows.getLast().get("time_s"))/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var points=new ArrayList<Map<String,Double>>();var trace=new StringBuilder("time_s,V_BUS1,SPEED_DEV,VOTHSG,TERMINAL_STATE\n");
            for(int tick=0;tick<=ticks;tick++) {
                var m=f.machine();var pss=f.stabilizer();var row=new LinkedHashMap<String,Double>();
                row.put("time_s",tick*p.step());row.put("V_BUS1",m.getDStabBus().getVoltageMag());row.put("SPEED_DEV",m.getSpeed()-1);
                row.put("VOTHSG",pss.getOutput(m));row.put("TERMINAL_STATE",pss.getUnclippedOutput());points.add(row);
                boolean first=true;for(double value:row.values()){assertTrue(Double.isFinite(value));if(!first)trace.append(',');trace.append(value);first=false;}trace.append('\n');
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            var history=new EventSideTrajectory(points);var diagnostic=new StringBuilder("raw_row,time_s,native_state,coupled_state,native_vstab,coupled_vstab\n");
            double maximum=0;
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s");var actual=history.sample(time<0?0:time,Side.AFTER);
                maximum=Math.max(maximum,Math.abs(actual.get("TERMINAL_STATE")-row.get("PSS_LAST")));
                diagnostic.append(index).append(',').append(time).append(',').append(row.get("PSS_LAST")).append(',').append(actual.get("TERMINAL_STATE")).append(',').append(row.get("VOTHSG")).append(',').append(actual.get("VOTHSG")).append('\n');
            }
            write("coupled",p.name(),trace);write("diagnostic",p.name(),diagnostic);
            System.out.println("IEEEST zero-numerator coupled diagnostic "+p.name()+" state="+maximum);
        }
    }
    private static void write(String kind,String name,StringBuilder text)throws Exception {
        Path path=Path.of("target/ieeest-zero-"+kind,name+".csv");Files.createDirectories(path.getParent());Files.writeString(path,text);
    }
}
