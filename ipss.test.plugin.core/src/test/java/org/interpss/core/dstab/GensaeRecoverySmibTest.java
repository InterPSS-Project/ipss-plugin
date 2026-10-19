package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GensaeRecoveryReference;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Composed recovery evidence; instantaneous event-side outputs remain separate diagnostics. */
public class GensaeRecoverySmibTest extends CorePluginTestSetup {
    private static final List<String> STATES=List.of("EP_Q","PSIQ_PP","PSIK_D","SPEED_DEV","ANGLE",
            "EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_VA","EXC_FEEDBACK","GOV_VALVE","GOV_STORAGE");
    // The remaining four states are recorded diagnostics, not accepted by
    // broadening a tolerance around a nonshrinking discrepancy.
    private static final List<String> BOUNDED_STATES=List.of("EP_Q","PSIQ_PP","PSIK_D","SPEED_DEV",
            "EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_VA");
    private static LinkedHashMap<String,Double> snapshot(GensaeFieldSmibTest.Fixture f,double time) {
        var values=GensaeFieldSmibTest.snapshot(f,time);
        var governor=assertInstanceOf(PsseTGov1SteamTurGovernor.class,f.machine().getGovernor());
        values.put("GOV_VALVE",governor.getValvePosition());values.put("GOV_STORAGE",governor.getReheaterStorage());
        values.put("GOV_REFERENCE",governor.getRefPoint());return values;
    }
    @Test void allComposedProfilesProduceCompleteUnforcedRecoveryTraces(@TempDir Path directory)throws Exception {
        var errors=new LinkedHashMap<String,List<Double>>();
        for(var p:GensaeRecoveryReference.profiles()) {
            var nativeRows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),nativeRows.size());
            double last=nativeRows.getLast().get("time_s");
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),p.fault(),directory.resolve(p.name()));
            var governor=assertInstanceOf(PsseTGov1SteamTurGovernor.class,f.machine().getGovernor());
            assertEquals(1,governor.ratingScale,0);assertEquals(0,governor.getData().getTrate(),0);
            assertEquals(p.damping(),governor.dt,1e-7);
            assertEquals(p.rating(),f.machine().getRating(UnitType.mVA,f.algorithm().getNetwork().getBaseKva()),1e-12);
            int ticks=(int)Math.ceil(Math.max(p.end(),last)/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var initial=snapshot(f,0);var trace=new StringBuilder(String.join(",",initial.keySet())+"\n");
            var points=new ArrayList<Map<String,Double>>();
            for(int tick=0;tick<=ticks;tick++) {
                var values=snapshot(f,tick*p.step());points.add(values);boolean first=true;
                for(double value:values.values()){assertTrue(Double.isFinite(value));if(!first)trace.append(',');trace.append(value);first=false;}
                trace.append('\n');if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            Path output=Path.of("target/gensae-recovery-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            errors.put(p.name(),compareStateRows(p,nativeRows,points));
            if(p.name().startsWith("valve-limits-")) {
                assertTrue(nativeRows.stream().anyMatch(r->Math.abs(r.get("GOV_VALVE")-governor.vmax)<1e-7));
                assertTrue(points.stream().anyMatch(r->Math.abs(r.get("GOV_VALVE")-governor.vmax)<1e-7));
            }
            if(p.name().startsWith("valve-lower-")) {
                assertTrue(nativeRows.stream().anyMatch(r->Math.abs(r.get("GOV_VALVE")-governor.vmin)<1e-7));
                assertTrue(points.stream().anyMatch(r->Math.abs(r.get("GOV_VALVE")-governor.vmin)<1e-7));
            }
        }
        for(var p:GensaeRecoveryReference.profiles())if(p.fault() && p.name().endsWith("-coarse")) {
            var coarse=errors.get(p.name());var fine=errors.get(p.name().replace("-coarse","-fine"));
            // Dominant electrical/control residuals refine. Small second
            // lead-lag and unresolved angle/feedback/governor floors do not
            // establish whole-stack convergence and are not claimed as such.
            for(String key:List.of("EP_Q","PSIQ_PP","PSIK_D","SPEED_DEV","EXC_VMEAS","EXC_LL1","EXC_VA")) {
                int j=STATES.indexOf(key);if(coarse.get(j)>(key.equals("SPEED_DEV")?1e-7:1e-6))
                    assertTrue(fine.get(j)<.7*coarse.get(j),p.name()+" dominant refinement "+key);
            }
        }
    }
    private static List<Double> compareStateRows(GensaeRecoveryReference.Profile p,List<Map<String,Double>> nativeRows,List<Map<String,Double>> points) {
        assertEquals(BOUNDED_STATES.size(),p.stateBounds().size());
        var maximum=new ArrayList<>(Collections.nCopies(STATES.size(),0.));var at=new double[STATES.size()];
        for(var row:nativeRows) {
            double position=Math.max(0,row.get("time_s"))/p.step();assertTrue(position<=points.size()-1);
            int lower=(int)Math.floor(position),upper=Math.min(points.size()-1,lower+1);double fraction=position-lower;
            for(int j=0;j<STATES.size();j++) {
                String key=STATES.get(j);double actual=points.get(lower).get(key)+fraction*(points.get(upper).get(key)-points.get(lower).get(key));
                double error=Math.abs(actual-row.get(key));if(error>maximum.get(j)){maximum.set(j,error);at[j]=row.get("time_s");}
                int accepted=BOUNDED_STATES.indexOf(key);
                if(accepted>=0)assertTrue(error<=p.stateBounds().get(accepted),p.name()+" "+key+" @"+row.get("time_s")+" error="+error);
            }
        }
        System.out.println("Composed recovery states "+p.name()+" maxima="+maximum+" at="+Arrays.toString(at));return maximum;
    }
    @Test void everyRawHistoryRetainsTheNativeGovernorOutputIdentityAndRecoveryDirections() {
        assertEquals(18,GensaeRecoveryReference.profiles().size());
        for(var p:GensaeRecoveryReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(29,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            double previous=Double.NEGATIVE_INFINITY;int duplicates=0;boolean positive=false,negative=false;float clock=0;
            for(var row:rows) {
                for(double value:row.values())assertTrue(Double.isFinite(value));
                double time=row.get("time_s");assertTrue(time>=previous);
                if(time>0 && time>previous)clock+=(float)p.step();
                if(time>=0)assertEquals((double)clock,time,6e-12,p.name()+" binary32 dispatch clock");
                if(time==previous)duplicates++;previous=time;
                // The native table calls its second state turbine power, but
                // allocation storage is unnormalized. Its exact port identity
                // distinguishes this coordinate from the lead-lag output.
                double output=(.1/.3)*row.get("GOV_VALVE")+row.get("GOV_STORAGE")/.3-p.damping()*row.get("SPEED_DEV");
                assertEquals(row.get("PMECH"),output,4e-7,p.name()+" same-row mechanical output @"+time);
                if(time>.1){positive|=row.get("SPEED_DEV")>1e-5;negative|=row.get("SPEED_DEV")< -1e-5;}
            }
            assertEquals(2,duplicates);assertTrue(previous>=p.end() && previous<p.end()+2*p.step());
            if(p.fault())assertTrue(positive && negative,p.name()+" must contain recovery in both speed directions");
        }
    }
    @Test void nativeInitialStatesAndMachineBasePortsMatchWithoutForcing(@TempDir Path directory)throws Exception {
        for(var p:GensaeRecoveryReference.profiles()) {
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var expected=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();
            var actual=snapshot(f,0);String[] keys=p.body().lines().findFirst().orElseThrow().split(",");
            assertEquals(keys.length-1,p.initialBounds().size());
            for(int j=1;j<keys.length;j++)
                assertEquals(expected.get(keys[j]),actual.get(keys[j]),p.initialBounds().get(j-1),p.name()+" initial "+keys[j]);
            assertEquals(50.001/p.rating(),f.machine().getPm(),2e-7);
            var governor=assertInstanceOf(PsseTGov1SteamTurGovernor.class,f.machine().getGovernor());
            var named=governor.getNamedStates();assertEquals(.3*named.get("t2t3FilterBlock"),governor.getReheaterStorage(),1e-15);
            assertEquals(named,governor.getNamedStates());
        }
    }
    @Test void governorAlgebraicPortReplaysEveryRecordedStateAndCurrentSpeed(@TempDir Path directory)throws Exception {
        for(var p:GensaeRecoveryReference.profiles()) {
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var m=f.machine();var governor=assertInstanceOf(PsseTGov1SteamTurGovernor.class,m.getGovernor());
            var blocks=governor.getNamedStateBlocks();var valve=blocks.get("t1DelayBlock");var filter=blocks.get("t2t3FilterBlock");
            var trace=new StringBuilder("time_s,expected_pm,actual_pm\n");double maximum=0;
            for(var row:HplntdSwitchingNativeSmibTest.rows(p.body())) {
                m.setSpeed(1+row.get("SPEED_DEV"));valve.setStateX(row.get("GOV_VALVE"));
                filter.setStateX(row.get("GOV_STORAGE")/governor.t3);
                // Set the recorded algebraic input with no state advance.
                double before=filter.getStateX();filter.eulerStep1(row.get("GOV_VALVE"),0);assertEquals(before,filter.getStateX(),0);
                double actual=governor.getOutput(m),error=Math.abs(actual-row.get("PMECH"));maximum=Math.max(maximum,error);
                assertEquals(row.get("PMECH"),actual,p.governorPortBound(),p.name()+" recorded governor port @"+row.get("time_s"));
                trace.append(row.get("time_s")).append(',').append(row.get("PMECH")).append(',').append(actual).append('\n');
            }
            Path output=Path.of("target/gensae-recovery-governor-port",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Composed governor port "+p.name()+" maximum="+maximum);
        }
    }
    @Test void recordedSpeedReplaySeparatesGovernorStorageFromCoupledMachineErrors(@TempDir Path directory)throws Exception {
        for(var p:GensaeRecoveryReference.profiles()) {
            assertEquals(3,p.replayBounds().size());
            var f=GensaeFieldSmibTest.load(p.network(),p.input(),p.step(),false,directory.resolve(p.name()));
            var m=f.machine();var governor=assertInstanceOf(PsseTGov1SteamTurGovernor.class,m.getGovernor());
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var previous=rows.getFirst();
            var trace=new StringBuilder("time_s,expected_valve,actual_valve,expected_storage,actual_storage,expected_pm,actual_pm\n");
            double[] maximum=new double[3];
            for(var row:rows) {
                double time=row.get("time_s");governor.setRefPoint(row.get("GOV_REFERENCE"));
                if(time>0 && time>previous.get("time_s")) {
                    m.setSpeed(1+previous.get("SPEED_DEV"));assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,m,0));
                    m.setSpeed(1+row.get("SPEED_DEV"));assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,m,1));
                }
                m.setSpeed(1+row.get("SPEED_DEV"));
                double[] actual={governor.getValvePosition(),governor.getReheaterStorage(),governor.getOutput(m)};
                double[] expected={row.get("GOV_VALVE"),row.get("GOV_STORAGE"),row.get("PMECH")};trace.append(time);
                for(int j=0;j<3;j++){
                    double error=Math.abs(actual[j]-expected[j]);maximum[j]=Math.max(maximum[j],error);
                    assertTrue(error<=p.replayBounds().get(j),p.name()+" recorded-speed channel "+j+" @"+time+" error="+error);
                    trace.append(',').append(expected[j]).append(',').append(actual[j]);
                }
                trace.append('\n');previous=row;
            }
            Path output=Path.of("target/gensae-recovery-governor-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("Composed governor replay "+p.name()+" maxima="+Arrays.toString(maximum));
        }
    }
}
