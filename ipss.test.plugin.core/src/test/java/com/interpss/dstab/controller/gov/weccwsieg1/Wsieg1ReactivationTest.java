package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.algo.dynamic.MachineReactivationLifecycle;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.gov.weccwsieg1.Wsieg1Governor;

/** Opt-in shared turbine lifecycle; independent full traces remain strict qualification gates. */
public class Wsieg1ReactivationTest {
    static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/wsieg1-reactivation-contract-20261104/captures.zip");
    static final Set<String> PAIR=Set.of("Bus1-mach1","Bus2-mach1");
    @BeforeAll static void setup()throws Exception {
        Wsieg1SharedSmibTest.setup();
        assertEquals("147a27ef561e1557886dfb165353ade60e125778dd90cb43020a68dcf45b3e11",Wsieg1ReferenceFixture.sha(Files.readAllBytes(ARCHIVE)));
    }
    @org.junit.jupiter.api.Test void coldOwnedChannelsAndPrimaryReferenceContract()throws Exception {
        int pairs=0,unownedDifferences=0;
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(var entry:Collections.list(z.entries()))if(entry.getName().endsWith("-a/native.csv")) {
                String other=entry.getName().replace("-a/native.csv","-b/native.csv");
                var a=Wsieg1StateConformanceTest.rows(z.getInputStream(entry).readAllBytes());
                var b=Wsieg1StateConformanceTest.rows(z.getInputStream(z.getEntry(other)).readAllBytes());
                assertEquals(a.size(),b.size());boolean primary=entry.getName().contains("primary-trip"),different=false;
                for(int i=0;i<a.size();i++)for(String key:a.get(i).keySet()) {
                    if(key.equals("GREF_LP")){different|=!a.get(i).get(key).equals(b.get(i).get(key));continue;}
                    assertEquals(a.get(i).get(key),b.get(i).get(key),key);
                    if(key.equals("GREF")&&a.get(i).get("time_s")>.051)assertEquals(primary?0.:.34,a.get(i).get(key),5e-8);
                }
                if(different)unownedDifferences++;pairs++;
            }
        }
        assertEquals(4,pairs);assertEquals(1,unownedDifferences,"Retained unowned memory discrepancy is visible");
    }
    static Stream<Arguments> cases(){return Stream.of(false,true).flatMap(m->Stream.of("primary-trip","secondary-trip").flatMap(e->Stream.of(.0005,1./240).map(h->Arguments.of(m,e,h))));}
    static Path stage(String event,double h)throws Exception {
        String tag="gref-status-reenable-dual-"+event+"-r0p1-h"+(h<.001?"0p0005":"0p00416666666667")+"-t0p5-a/";
        Path out=Files.createTempDirectory(Path.of("target"),"wsieg1-reentry-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {for(String file:List.of("network.raw","dynamic.dyr","manifest.json","native.csv"))
            try(var in=z.getInputStream(z.getEntry(tag+file))){Files.copy(in,out.resolve(file));}}
        return out;
    }
    @org.junit.jupiter.api.Test void independentFirstEventIncrementsUseEventAverageAndOlderRate()throws Exception {
        for(double h:List.of(.0005,1./240)) {
            Path input=stage("primary-trip",h);
            var rows=Wsieg1StateConformanceTest.rows(Files.readAllBytes(input.resolve("native.csv")));
            var ordinary=simulate(input,false,"primary-trip",h,(r,s,l,t)->{
                if(Math.abs(t-.1)<1e-9){r.enabled=false;try{s.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",t);}finally{r.enabled=true;}}
            });
            var staggered=simulate(input,true,"primary-trip",h,(r,s,l,t)->{
                if(Math.abs(t-.1)<1e-9){r.enabled=false;try{s.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",t);}finally{r.enabled=true;}}
            });
            for(double event:List.of(.025,.1)) {
                var atEvent=rows.stream().filter(r->Math.abs(r.get("time_s")-event)<1e-6)
                        .reduce((left,right)->right).orElseThrow();
                var after=rows.stream().filter(r->r.get("time_s")>event+1e-6)
                        .findFirst().orElseThrow();
                double leftRate=event<.05?0.:.2;
                double rightRate=event<.05?.2:-.2;
                double olderRate=leftRate;
                double eventRate=.5*(leftRate+rightRate);
                double observed=after.get("STATE1")-atEvent.get("STATE1");
                double expected=h*(1.5*eventRate-.5*olderRate);
                assertEquals(expected,observed,1e-7,"Independent first interval at "+event+" s");
                assertTrue(Math.abs(observed-h*rightRate)>.1*h*Math.abs(rightRate),
                        "A full right-side modified-Euler rate must remain distinguishable");
                for(boolean shared:List.of(false,true)) {
                    var frames=shared?staggered.frames:ordinary.frames;
                    var left=frames.stream().filter(r->Math.abs(r.time()-event)<1e-6)
                            .reduce((a,b)->b).orElseThrow();
                    var right=frames.stream().filter(r->r.time()>event+1e-6)
                            .findFirst().orElseThrow();
                    double increment=right.values().get("PUBLISHED1")-left.values().get("PUBLISHED1");
                    assertEquals(shared?expected:h*rightRate,increment,1e-7,
                            (shared?"Staggered":"Modified Euler")+" first interval at "+event+" s");
                }
            }
        }
    }
    interface Boundary {void run(Wsieg1SharedSmibTest.Recording r,com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase s,MachineReactivationLifecycle life,double t)throws DStabSimuException;}
    static Wsieg1SharedSmibTest.Recording simulate(Path input,boolean shared,String event,double h,Boundary boundary)throws Exception {
        return simulate(input,shared,event,h,boundary,new boolean[1]);
    }
    static Wsieg1SharedSmibTest.Recording simulate(Path input,boolean shared,String event,double h,Boundary boundary,boolean[] reject)throws Exception {
        return Wsieg1SharedSmibTest.simulate(input.resolve("network.raw"),input.resolve("dynamic.dyr"),shared,event,h,.5,
            List.of(new Wsieg1SharedSmibTest.ReferenceEvent(.025,.1)),a->{
                if(shared) {
                    var r=((Wsieg1SharedSmibTest.SharedRecorder)a.getSolver()).recording;
                    a.setSolver(new Wsieg1SharedSmibTest.SharedRecorder(a,r){
                        {setHotReactivationMachineIds(PAIR);}
                        @Override public void beforeStep(double t)throws DStabSimuException{super.beforeStep(t);boundary.run(r,this,reactivation,t);}
                        @Override public boolean networkSolutionStep()throws DStabSimuException{if(reject[0]){a.getNetwork().getDStabBus("Bus1").setVoltage(Complex.ONE);return false;}return super.networkSolutionStep();}
                    });
                } else {
                    var r=((Wsieg1SharedSmibTest.OrdinaryRecorder)a.getSolver()).recording;
                    a.setSolver(new Wsieg1SharedSmibTest.OrdinaryRecorder(a,r){
                        {setHotReactivationMachineIds(PAIR);}
                        @Override public void beforeStep(double t)throws DStabSimuException{super.beforeStep(t);boundary.run(r,this,reactivation,t);}
                        @Override public boolean networkSolutionStep()throws DStabSimuException{if(reject[0]){a.getNetwork().getDStabBus("Bus1").setVoltage(Complex.ONE);return false;}return super.networkSolutionStep();}
                    });
                }
            },0,true);
    }
    @ParameterizedTest @MethodSource("cases")
    void completeIndependentReactivationTrajectory(boolean shared,String event,double h)throws Exception {
        Path out=stage(event,h);String id=event.equals("primary-trip")?"Bus1-mach1":"Bus2-mach1";
        var actual=simulate(out,shared,event,h,(r,s,l,t)->{if(Math.abs(t-.1)<1e-9){r.enabled=false;try{s.reactivateMachineAtAcceptedEndpoint(id,t);}finally{r.enabled=true;}}});
        Wsieg1SharedSmibTest.write(out.resolve("actual.csv.gz"),actual);
        var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(out.resolve("native.csv")));
        var maximum=new LinkedHashMap<String,Double>();var squares=new LinkedHashMap<String,Double>();var lo=new LinkedHashMap<String,Double>();var hi=new LinkedHashMap<String,Double>();int frame=0;long tick=0;float clock=0;
        for(var row:expected) {
            var observed=row.get("time_s")<0?actual.initial:actual.frames.get(frame++);
            if(row.get("time_s")>=0){long target=Math.round(observed.time()/h);assertTrue(target>=tick&&target-tick<=1);while(tick<target){clock+=(float)h;tick++;}assertEquals((double)clock,row.get("time_s"),1e-10);}
            for(var entry:row.entrySet()) {
                String k=entry.getKey();if(k.equals("time_s")||k.equals("VAR2")||k.equals("GREF_LP"))continue;
                String mapped=k.startsWith("STATE")?"PUBLISHED"+k.substring(5):k.equals("GREF")?"REFERENCE_INPUT":k;
                assertTrue(observed.values().containsKey(mapped),k);
                double d=entry.getValue()-observed.values().get(mapped);maximum.merge(k,Math.abs(d),Math::max);squares.merge(k,d*d,Double::sum);lo.merge(k,entry.getValue(),Math::min);hi.merge(k,entry.getValue(),Math::max);
            }
        }
        assertEquals(actual.frames.size(),frame,"Every event-side observation retained");
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),Map.of("shared",shared,"event",event,"step",h,"maximum",maximum,"sum_squared",squares,"reference_min",lo,"reference_max",hi,"samples",expected.size(),"unowned_diagnostics",List.of("VAR2","GREF_LP")));
        Files.writeString(out.resolve("errors.txt"),maximum.toString());System.out.println("WSIEG1 reentry "+shared+" "+event+" "+h+" "+out+" "+maximum);
        var gates=new ArrayList<org.junit.jupiter.api.function.Executable>();
        double[] bounds=Wsieg1StateConformanceTest.stateBounds(shared,h);
        boolean acceptedPrimaryMethodDifference=!shared&&event.equals("primary-trip");
        if(acceptedPrimaryMethodDifference){
            if(h<.001){bounds[1]=6e-5;bounds[2]=1.1e-5;}
            else{bounds[1]=5e-4;bounds[2]=7.5e-5;bounds[3]=2e-5;}
        }
        for(int i=0;i<6;i++){String k="STATE"+i;double bound=bounds[i];gates.add(()->assertTrue(maximum.get(k)<bound,k+" maximum "+maximum.get(k)+" > "+bound));}
        for(String k:maximum.keySet()) {
            double rmse=Math.sqrt(squares.get(k)/expected.size());
            if(k.startsWith("ANGLE_DEG"))gates.add(()->assertTrue(rmse<.01,k+" RMSE "+rmse));
            if(k.matches("V[0-9]+")||k.startsWith("P_PU")||k.startsWith("Q_PU")){
                double range=hi.get(k)-lo.get(k);
                double limit=acceptedPrimaryMethodDifference&&h>=.001&&k.equals("P_PU_LP")?.0012:.001;
                if(range>0)gates.add(()->assertTrue(rmse/range<limit,k+" NRMSE "+rmse/range+" > "+limit));
            }
        }
        assertAll(gates);
    }
    static Stream<Arguments> owners(){return Stream.of(false,true).flatMap(m->Stream.of("primary-trip","secondary-trip").map(e->Arguments.of(m,e)));}
    @ParameterizedTest @MethodSource("owners")
    void rejectedRequestsTrialsAndNetworkSolvePreserveBothOwners(boolean shared,String event)throws Exception {
        Path out=stage(event,.0005);String id=event.equals("primary-trip")?"Bus1-mach1":"Bus2-mach1";
        boolean[] reject={false};int[] checked={0};
        var simulation=simulate(out,shared,event,.0005,(r,s,l,t)-> {
            if(Math.abs(t-.1)>1e-9)return;
            var network=r.algorithm.getNetwork();var m=network.getMachine(id);
            var primary=(Wsieg1Governor)network.getMachine("Bus1-mach1").getGovernor();
            var states=primary.getNamedStates();var before=r.capture(t,"BEFORE").values();double reference=primary.getRefPoint();
            l.begin(0);l.commit();assertEquals(before,r.capture(t,"AFTER").values());
            assertThrows(IllegalArgumentException.class,()->l.begin(-.001));assertThrows(IllegalArgumentException.class,()->l.begin(Double.NaN));
            l.begin(.0005);assertThrows(IllegalStateException.class,()->s.reactivateMachineAtAcceptedEndpoint(id,t));l.rollback();
            assertEquals(before,r.capture(t,"AFTER").values());assertEquals(states,primary.getNamedStates());
            var governor=m.getGovernor();m.getControllerList().remove(governor);
            try{assertThrows(IllegalStateException.class,()->s.reactivateMachineAtAcceptedEndpoint(id,t));}finally{m.getControllerList().add(governor);}
            assertEquals(before,r.capture(t,"AFTER").values());
            var ones=new Complex[network.getNoBus()];Arrays.fill(ones,Complex.ONE);var currents=network.getYMatrix().multiply(ones);var cache=m.getDStabBus().captureSequenceEquivalentState();
            r.enabled=false;reject[0]=true;
            try{assertThrows(DStabSimuException.class,()->s.reactivateMachineAtAcceptedEndpoint(id,t));}finally{reject[0]=false;r.enabled=true;}
            assertFalse(m.isActive());assertFalse(m.getParentGen().isActive());assertArrayEquals(currents,network.getYMatrix().multiply(ones));assertEquals(cache,m.getDStabBus().captureSequenceEquivalentState());
            assertEquals(before,r.capture(t,"AFTER").values());assertEquals(reference,primary.getRefPoint());
            r.enabled=false;try{s.reactivateMachineAtAcceptedEndpoint(id,t);}finally{r.enabled=true;}
            var after=r.capture(t,"AFTER").values();
            Complex sum=Complex.ZERO;for(var gen:m.getDStabBus().getContributeGenList())if(gen.isActive())sum=sum.add(Complex.ONE.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
            assertEquals(Complex.ONE.divide(sum),m.getDStabBus().getScGenZ1());
            var energized=network.getYMatrix().multiply(ones);for(int i=0;i<currents.length;i++)assertTrue(energized[i].subtract(currents[i]).subtract(i==m.getDStabBus().getSortNumber()?m.getYgen():Complex.ZERO).abs()<1e-12,"Only selected machine Norton stamp changes");
            for(String suffix:List.of("","_LP"))for(String key:List.of("GEN_EP_Q","GEN_EP_D","GEN_PSIK_D","GEN_PSIK_Q","GEN_SPEED_DEV","GEN_ANGLE"))assertEquals(before.get(key+suffix),after.get(key+suffix));
            for(int i=0;i<5;i++)assertEquals(before.get("STATE"+i),after.get("STATE"+i));
            assertEquals(reference,primary.getRefPoint());assertEquals(0.,m.getEfd());
            s.reactivateMachineAtAcceptedEndpoint(id,t);assertEquals(after,r.capture(t,"AFTER").values());checked[0]++;
        },reject);
        assertEquals(1,checked[0]);
        var states=simulation.capture(.5,"FINAL").values();
        var rejected=new MachineReactivationLifecycle();
        assertThrows(IllegalArgumentException.class,()->rejected.prepare(simulation.algorithm,Set.of("Bus1-mach1")));
        var network=simulation.algorithm.getNetwork();network.setPositiveSeqDataOnly(false);
        try{assertThrows(IllegalArgumentException.class,()->rejected.prepare(simulation.algorithm,PAIR));}finally{network.setPositiveSeqDataOnly(true);}
        assertFalse(rejected.enabled());assertEquals(states,simulation.capture(.5,"FINAL").values());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void pairedDisconnectedRequestsRejectInBothOrdersWithoutMutation(boolean shared)throws Exception {
        Path out=stage("primary-trip",.0005);int[] checks={0};
        simulate(out,shared,"primary-trip",.0005,(r,s,l,t)-> {
            if(Math.abs(t-.1)>1e-9)return;
            var second=r.algorithm.getNetwork().getMachine("Bus2-mach1");
            second.setStatus(false);second.getParentGen().setStatus(false);l.acceptStatusChanges();
            var before=r.capture(t,"BOUNDARY").values();
            for(String id:List.of("Bus1-mach1","Bus2-mach1","Bus2-mach1","Bus1-mach1")) {
                assertThrows(IllegalStateException.class,()->s.reactivateMachineAtAcceptedEndpoint(id,t));
                assertEquals(before,r.capture(t,"BOUNDARY").values());
            }
            // Restore the artificial second-host disconnection for the remaining run.
            second.setStatus(true);second.getParentGen().setStatus(true);
            l.finishReactivation(second,true);checks[0]++;
        });
        assertEquals(1,checks[0]);
    }

}
