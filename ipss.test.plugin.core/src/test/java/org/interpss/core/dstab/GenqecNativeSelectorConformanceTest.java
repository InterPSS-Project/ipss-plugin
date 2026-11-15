package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenqecSchemaNativeReference;
import com.interpss.dstab.mach.model.GenqecMachine;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native selector discrimination; standard/wrapper numerical residuals are not complete parity. */
public class GenqecNativeSelectorConformanceTest extends CorePluginTestSetup {
    private static final String HEADER="time_s,EP_Q,EP_D,PSIP_D,PSIP_Q,SPEED_DEV,ANGLE,INTERNAL_VAR,V_BUS1,V_BUS2,MACH_ANGLE,P_PU,Q_PU,EFD,MACH_SPEED,REF_ANGLE,REF_SPEED\n";
    private static GenqecSchemaNativeReference.Profile profile(String name){return GenqecSchemaNativeReference.profiles().stream().filter(p->p.name().equals(name)).findFirst().orElseThrow();}
    private record Fixture(DynamicSimuAlgorithm algorithm,GenqecMachine machine,Machine reference){}
    private static Fixture load(GenqecSchemaNativeReference.Profile p,Path dir)throws Exception{
        Files.createDirectories(dir);Path raw=dir.resolve("network.raw"),dyr=dir.resolve("dynamic.dyr");Files.writeString(raw,p.network());Files.writeString(dyr,p.input());
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=context.getDStabilityNet();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(.3);algorithm.setSimuOutputHandler(new StateMonitor());
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"three-cycle-generator-schema");
        assertTrue(algorithm.initialization());var machine=(GenqecMachine)net.getMachine("Bus1-mach1");
        assertEquals(p.selector()==0?0:1,machine.getGenqecData().satFunc());assertEquals(.035,machine.getGenqecData().kw());assertEquals(0,machine.getGenqecData().accel());
        return new Fixture(algorithm,machine,net.getMachine("Bus2-mach1"));
    }
    private static double[] snapshot(Fixture f,double time){
        var m=f.machine();var state=m.getNamedStates();Complex voltage=m.getDStabBus().getVoltage();Complex current=m.getIgen().subtract(voltage.multiply(m.getYgen()));Complex power=voltage.multiply(current.conjugate());
        return new double[]{time,state.get("E'q"),state.get("E'd"),state.get("Psi'd"),state.get("Psi'q"),m.getSpeed()-1,m.getAngle(),m.getEffectiveSaturationFactor(),m.getDStabBus().getVoltageMag(),f.algorithm().getNetwork().getBus("Bus2").getVoltageMag(),Math.toDegrees(m.getAngle()),power.getReal(),power.getImaginary(),m.getEfd(),m.getSpeed()-1,Math.toDegrees(f.reference().getAngle()),f.reference().getSpeed()-1};
    }
    private static void append(StringBuilder text,double[] row){for(int i=0;i<row.length;i++){assertTrue(Double.isFinite(row[i]));if(i>0)text.append(',');text.append(row[i]);}text.append('\n');}
    private static void save(String name,StringBuilder text)throws Exception{Path output=Path.of("target/genqec-schema-coupled",name+".csv");Files.createDirectories(output.getParent());Files.writeString(output,text);}

    @Test void completeNativeRawHistoriesProveNonzeroSelectorEquivalence(){
        assertEquals(10,GenqecSchemaNativeReference.profiles().size());
        for(var p:GenqecSchemaNativeReference.profiles()){
            var raw=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),raw.size());assertEquals(17,raw.getFirst().size());assertTrue(raw.getFirst().get("time_s")<0);
            assertTrue(raw.stream().flatMap(r->r.values().stream()).allMatch(Double::isFinite));assertFalse(p.input().isBlank());assertFalse(p.network().isBlank());
        }
        for(String scale:List.of("coarse","fine")){
            var reference=profile("standard-1-"+scale);
            assertEquals(reference.body(),profile("standard-2-"+scale).body());assertEquals(reference.body(),profile("standard-negative-"+scale).body());
            assertNotEquals(reference.body(),profile("standard-0-"+scale).body());
            var a=HplntdSwitchingNativeSmibTest.rows(reference.body());var b=HplntdSwitchingNativeSmibTest.rows(profile("wrapper-1-"+scale).body());
            assertEquals(a.size(),b.size());double maximum=0;for(int i=0;i<a.size();i++)maximum=Math.max(maximum,Math.abs(a.get(i).get("EP_D")-b.get(i).get("EP_D")));
            assertTrue(maximum>0,"Native implementation-level wrapper residual is retained, not silently relabeled bit parity");
        }
    }

    @Test void importedNonzeroSelectorsAndWrapperHaveIdenticalCoupledFaultResponses(@TempDir Path dir)throws Exception{
        for(String scale:List.of("coarse","fine")){
            var reference=profile("standard-1-"+scale);var names=List.of("standard-1","standard-2","standard-negative","wrapper-1");var fixtures=new ArrayList<Fixture>();var traces=new ArrayList<StringBuilder>();
            for(String name:names){var p=profile(name+"-"+scale);fixtures.add(load(p,dir.resolve(p.name())));traces.add(new StringBuilder(HEADER));}
            int ticks=(int)Math.round(.3/reference.step());
            for(int tick=0;tick<=ticks;tick++){
                double[] baseline=null;
                for(int i=0;i<fixtures.size();i++){
                    double[] row=snapshot(fixtures.get(i),tick*reference.step());append(traces.get(i),row);
                    if(baseline==null)baseline=row;else assertArrayEquals(baseline,row,1e-12,names.get(i)+" accepted endpoint "+tick);
                }
                if(tick<ticks)for(var fixture:fixtures)assertTrue(fixture.algorithm().solveDEqnStep(true));
            }
            for(int i=0;i<names.size();i++)save(names.get(i)+"-"+scale,traces.get(i));
        }
    }

    @Test void exponentialImportRemainsDistinctAndRunsBothSteps(@TempDir Path dir)throws Exception{
        for(String scale:List.of("coarse","fine")){
            var p=profile("standard-0-"+scale);var exponential=load(p,dir.resolve(p.name()));var quadratic=load(profile("standard-1-"+scale),dir.resolve("quadratic-"+scale));
            assertTrue(Math.abs(exponential.machine().getEffectiveSaturationFactor()-quadratic.machine().getEffectiveSaturationFactor())>1e-4);
            var trace=new StringBuilder(HEADER);int ticks=(int)Math.round(.3/p.step());
            for(int tick=0;tick<=ticks;tick++){append(trace,snapshot(exponential,tick*p.step()));if(tick<ticks)assertTrue(exponential.algorithm().solveDEqnStep(true));}
            save(p.name(),trace);
        }
    }
}
