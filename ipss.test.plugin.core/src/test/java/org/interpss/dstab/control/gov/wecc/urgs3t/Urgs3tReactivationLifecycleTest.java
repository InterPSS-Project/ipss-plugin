package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.common.DStabSimuException;
import org.interpss.dstab.algo.MachineReactivationLifecycle;
import org.interpss.fadapter.psse.PsseDStabSolver;

/** Atomic accepted-state controls, separate from strict independent trajectory gates. */
public class Urgs3tReactivationLifecycleTest {
    @BeforeAll static void setup()throws Exception{Urgs3tReactivationReferenceTest.setup();}
    private static void exercise(Urgs3tReferenceTest.Recording recording,PsseDStabSolver solver,
            MachineReactivationLifecycle lifecycle,boolean[] reject,double time)throws Exception {
        var machine=recording.algorithm.getNetwork().getMachine("Bus1-mach1");
        var governor=(Urgs3tGovernor)machine.getGovernor();
        var before=recording.capture(time);var histories=governor.getIntegrationHistories();
        double[] held=new double[5];lifecycle.readHeldRates(governor,held,0);
        assertFalse(machine.isActive());
        lifecycle.begin(0);lifecycle.commit();assertEquals(before,recording.capture(time));
        assertThrows(IllegalArgumentException.class,()->lifecycle.begin(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->lifecycle.begin(-.001));
        lifecycle.begin(.0005);
        assertEquals(before.get("STATE0")+.0005*held[0],recording.capture(time).get("STATE0"),1e-14);
        assertThrows(IllegalStateException.class,()->solver.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",time));
        lifecycle.rollback();assertEquals(before,recording.capture(time));
        assertEquals(histories,governor.getIntegrationHistories());
        assertThrows(IllegalArgumentException.class,()->solver.reactivateMachineAtAcceptedEndpoint("missing",time));
        assertThrows(IllegalArgumentException.class,()->solver.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",time+.001));
        var network=recording.algorithm.getNetwork();var ones=new Complex[network.getNoBus()];Arrays.fill(ones,Complex.ONE);
        var current=network.getYMatrix().multiply(ones);
        reject[0]=true;
        assertThrows(DStabSimuException.class,()->solver.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",time));
        reject[0]=false;
        assertEquals(before,recording.capture(time));assertFalse(machine.getParentGen().isActive());
        assertArrayEquals(current,network.getYMatrix().multiply(ones));
        assertEquals(histories,governor.getIntegrationHistories());
        solver.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",time);
        assertTrue(machine.isActive());assertTrue(machine.getParentGen().isActive());
        var after=recording.capture(time);
        for(int k=0;k<5;k++)assertEquals(before.get("STATE"+k),after.get("STATE"+k));
        for(int k=0;k<6;k++)assertEquals(before.get("GEN1_STATE"+k),after.get("GEN1_STATE"+k));
        assertEquals(0.,governor.getRefPoint(),0.);assertEquals(0.,machine.getEfd(),0.);
        assertTrue(after.get("P_PU")>.5);
        solver.reactivateMachineAtAcceptedEndpoint("Bus1-mach1",time);assertEquals(after,recording.capture(time));
    }
    private static void checkFirstInactive(Urgs3tReferenceTest.Recording recording,
            MachineReactivationLifecycle lifecycle,double time,double[] older) {
        var governor=(Urgs3tGovernor)recording.algorithm.getNetwork().getMachine("Bus1-mach1").getGovernor();
        if(Math.abs(time-.1495)<1e-9)lifecycle.readHeldRates(governor,older,0);
        if(Math.abs(time-.15)<1e-9) {
            double[] last=new double[5];lifecycle.readHeldRates(governor,last,0);
            var before=recording.capture(time);lifecycle.begin(.0005);
            var trial=recording.capture(time);
            for(int k=0;k<5;k++)assertEquals(before.get("STATE"+k)+.0005*(1.5*last[k]-.5*older[k]),trial.get("STATE"+k),1e-14);
            lifecycle.rollback();assertEquals(before,recording.capture(time));
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rejectedTrialsAndRequestsPreserveTheAcceptedBoundary(boolean shared)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-reactivation-atomic-");
        Path archive=Path.of("testData/private/verification-artifacts/urgs3t-reactivation-contract-20261104/captures.zip");
        try(var z=new ZipFile(archive.toFile())) {
            for(String file:List.of("manifest.json","native.csv","network.raw","dynamic.dyr"))
                try(var in=z.getInputStream(z.getEntry("reenable-standard-h0p0005-t0p5-trip1-machines-a/"+file))){Files.copy(in,out.resolve(file));}
        }
        boolean[] reject={false};int[] tested={0};double[] older=new double[5];
        var result=Urgs3tReferenceTest.simulate(out,shared,1e-6,(algorithm,recording)-> {
            if(shared)return new Urgs3tReferenceTest.Shared(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    super.beforeStep(t);checkFirstInactive(recording,reactivation,t,older);if(Math.abs(t-.2)<1e-9)try{exercise(recording,this,reactivation,reject,t);tested[0]++;}
                    catch(DStabSimuException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
                }
                @Override public boolean networkSolutionStep()throws DStabSimuException {
                    if(reject[0]){algorithm.getNetwork().getDStabBus("Bus1").setVoltage(Complex.ONE);return false;}
                    return super.networkSolutionStep();
                }
            };
            return new Urgs3tReferenceTest.Ordinary(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    super.beforeStep(t);checkFirstInactive(recording,reactivation,t,older);if(Math.abs(t-.2)<1e-9)try{exercise(recording,this,reactivation,reject,t);tested[0]++;}
                    catch(DStabSimuException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
                }
                @Override public boolean networkSolutionStep()throws DStabSimuException {
                    if(reject[0]){algorithm.getNetwork().getDStabBus("Bus1").setVoltage(Complex.ONE);return false;}
                    return super.networkSolutionStep();
                }
            };
        });
        assertEquals(1,tested[0]);assertTrue(result.governor().getMachine().isActive());
        var unprepared=new MachineReactivationLifecycle();
        var states=result.governor().getIntegrationStates();
        assertThrows(IllegalArgumentException.class,()->unprepared.prepare(result.recording().algorithm,Set.of("Bus2-mach1")));
        assertThrows(IllegalArgumentException.class,()->unprepared.prepare(result.recording().algorithm,Set.of("Bus1-mach1","missing")));
        assertFalse(unprepared.enabled());assertEquals(states,result.governor().getIntegrationStates());
        var survivor=result.recording().algorithm.getNetwork().getMachine("Bus2-mach1");
        var added=new org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor();
        try {
            survivor.getControllerList().add(added);
            assertThrows(IllegalArgumentException.class,()->unprepared.prepare(result.recording().algorithm,Set.of("Bus1-mach1")));
        } finally {survivor.getControllerList().remove(added);}
        assertFalse(unprepared.enabled());assertEquals(states,result.governor().getIntegrationStates());
    }
}
