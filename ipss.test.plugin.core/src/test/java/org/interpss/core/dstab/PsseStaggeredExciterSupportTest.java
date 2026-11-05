package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Optimized integration contracts for the complete large-case exciter stack. */
class PsseStaggeredExciterSupportTest {
    private static final Path CASE=Path.of("testData/adpter/psse/v33/SMIB");

    private DynamicSimuAlgorithm load(String model,Path directory,boolean optimized)throws Exception {
        return load(model,directory,optimized,solver->{});
    }
    private DynamicSimuAlgorithm load(String model,Path directory,boolean optimized,
            java.util.function.Consumer<PsseStaggeredEulerSolver> configure)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path dyr=CASE.resolve("SMIB_v33_genrou_"+model.toLowerCase(Locale.ROOT)+".dyr");
        if(model.equals("EXPIC1")) {
            dyr=directory.resolve("expic1.dyr");
            String machine=Files.readString(CASE.resolve("SMIB_v33_genrou.dyr"));
            Files.writeString(dyr,machine+"\n1 'EXPIC1' '1' 0 3.5 1 1 -.87 0 0 0 1 -.87 0 1 0 7.5 0 0 0 1 0 1.2 0 6.4 0 .08 /\n");
        }
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33.raw").toString(),dyr.toString());
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if(optimized) {
            var solver=new PsseStaggeredEulerSolver(algorithm);solver.setNetworkSolutionTolerance(1e-6);
            configure.accept(solver);
            solver.setReuseNetworkEndpoint(true);algorithm.setSolver(solver);
        }
        assertTrue(algorithm.initialization(),model);return algorithm;
    }

    @ParameterizedTest @ValueSource(strings={"EXAC1","EXAC2","ESAC1A","ESAC6A","ESDC1A","ESDC2A","EXPIC1","SCRX"})
    void automaticResolutionPreservesInitializationAndExplicitCountsAreImmutable(String model,@TempDir Path directory)throws Exception {
        var optimized=load(model,directory,true);
        var ordinary=load(model,directory,false);
        var a=(com.interpss.dstab.controller.cml.ICMLStateProvider)optimized.getNetwork().getMachine("Bus1-mach1").getExciter();
        var b=(com.interpss.dstab.controller.cml.ICMLStateProvider)ordinary.getNetwork().getMachine("Bus1-mach1").getExciter();
        var actual=a.getNamedStates();var expected=b.getNamedStates();assertEquals(expected.keySet(),actual.keySet());
        for(String name:expected.keySet())assertEquals(expected.get(name),actual.get(name),1e-10,model+"/"+name);
        // A separate load exercises the local path even when the automatic policy selects one.
        var explicit=load(model,directory,true,solver->solver.setExciterSubsteps(model,4));
        var solver=(PsseStaggeredEulerSolver)explicit.getSolver();
        assertEquals(4,solver.getResolvedExciterSubsteps().get("Bus1-mach1"));
        assertThrows(IllegalStateException.class,()->solver.setExciterSubsteps(model,2));
        assertThrows(IllegalStateException.class,()->solver.setAutomaticExciterSubsteps(false));
        var machine=explicit.getNetwork().getMachine("Bus1-mach1");double field=machine.getEfd();
        for(int i=0;i<240;i++)assertTrue(explicit.solveDEqnStep(true));
        assertEquals(field,machine.getEfd(),1e-7,model);
    }

    @ParameterizedTest @ValueSource(strings={"ESAC6A","ESDC1A","ESDC2A","EXPIC1"})
    void inactiveLocalParticipantsHoldStatesAndCanResume(String model,@TempDir Path directory)throws Exception {
        var algorithm=load(model,directory,true,solver->solver.setExciterSubsteps(model,4));
        var machine=algorithm.getNetwork().getMachine("Bus1-mach1");var exciter=machine.getExciter();
        var participant=(StaggeredEulerParticipant)exciter;double[] before=new double[participant.staggeredStateCount()],after=before.clone();
        var annotated=(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)exciter;
        participant.readStaggeredState(before,0);annotated.setStatus(false);
        for(int i=0;i<24;i++)assertTrue(algorithm.solveDEqnStep(true));
        participant.readStaggeredState(after,0);assertArrayEquals(before,after,0.);
        annotated.setStatus(true);
        annotated.setRefPoint(annotated.getRefPoint()+.01);
        ((PsseStaggeredEulerSolver)algorithm.getSolver()).notifyDiscontinuity();
        for(int i=0;i<24;i++)assertTrue(algorithm.solveDEqnStep(true));
        participant.readStaggeredState(after,0);assertFalse(Arrays.equals(before,after));
        for(double value:after)assertTrue(Double.isFinite(value));
    }

    @ParameterizedTest @ValueSource(strings={"EXAC1","EXAC2","ESAC1A","ESAC6A","ESDC1A","ESDC2A","EXPIC1","SCRX"})
    void holdsEquilibriumWithoutDisablingTheExciter(String model,@TempDir Path directory)throws Exception {
        var algorithm=load(model,directory,true);var machine=algorithm.getNetwork().getMachine("Bus1-mach1");
        double voltage=machine.getDStabBus().getVoltageMag(),angle=machine.getAngle(),field=machine.getEfd();
        assertTrue(machine.getExciter().isActive());
        for(int i=0;i<240;i++)assertTrue(algorithm.solveDEqnStep(true),model+" at "+algorithm.getSimuTime());
        assertTrue(machine.getExciter().isActive());assertEquals(voltage,machine.getDStabBus().getVoltageMag(),1e-7,model);
        assertEquals(angle,machine.getAngle(),1e-7,model);assertEquals(field,machine.getEfd(),1e-7,model);
    }

    @ParameterizedTest @ValueSource(strings={"ESAC6A","ESDC1A","ESDC2A","EXPIC1"})
    void customRateEvaluationDoesNotAdvanceStateAndUsesTheExistingEquations(String model,@TempDir Path directory)throws Exception {
        var first=load(model,directory,true);var second=load(model,directory,false);
        Machine a=first.getNetwork().getMachine("Bus1-mach1"),b=second.getNetwork().getMachine("Bus1-mach1");
        var participant=assertInstanceOf(StaggeredEulerParticipant.class,a.getExciter());
        var original=assertInstanceOf(StaggeredEulerParticipant.class,b.getExciter());
        int size=participant.staggeredStateCount();double[] before=new double[size],after=new double[size],rates=new double[size];
        participant.readStaggeredState(before,0);original.readStaggeredState(after,0);assertArrayEquals(before,after,1e-10);
        var ca=(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)a.getExciter();
        var cb=(com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)b.getExciter();
        ca.setRefPoint(ca.getRefPoint()+.02);cb.setRefPoint(cb.getRefPoint()+.02);
        double h=1e-5;participant.evaluateStaggered(h,rates,0);participant.readStaggeredState(after,0);
        assertArrayEquals(before,after,0.,"Derivative evaluation must not advance any coordinate");
        double[] candidate=before.clone();for(int i=0;i<size;i++)candidate[i]+=h*rates[i];
        participant.applyStaggeredState(candidate,0,h);
        assertTrue(b.getExciter().nextStep(h,DynamicSimuMethod.MODIFIED_EULER,b,0));
        participant.readStaggeredState(after,0);original.readStaggeredState(candidate,0);
        assertArrayEquals(candidate,after,1e-10,model+" must retain the existing RHS and projection");
    }
}
