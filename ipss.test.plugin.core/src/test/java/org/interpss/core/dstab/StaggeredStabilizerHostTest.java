package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Stabilizer coordinates share the ordinary machine excitation ownership rule. */
class StaggeredStabilizerHostTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void orphanStabilizerIsNotInitializedAndHostActivationRequiresReinitialization(boolean missing)throws Exception {
        var a=load();var m=a.getNetwork().getMachine("Bus1-mach1");
        var exciter=m.getExciter();var pss=(AbstractAnnotateController)m.getStabilizer();
        if(missing)m.getControllerList().remove(exciter);else exciter.setStatus(false);
        assertTrue(a.initialization());assertTrue(pss.isActive());
        assertNull(pss.getFieldWrapperList(),"Ordinary initialization leaves orphan PSS metadata untouched");
        for(int i=0;i<4;i++)assertTrue(a.solveDEqnStep(true));
        assertNull(pss.getFieldWrapperList());
        if(missing)m.getControllerList().add(exciter);else exciter.setStatus(true);
        assertThrows(IllegalStateException.class,()->((StaggeredEulerSolver)a.getSolver()).nextStep(
                a.getSimuTime(),a.getSimuStepSec(),com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER));
        a.getNetwork().setDStabNetInitialized(false);
        assertTrue(a.initialization());assertNotNull(pss.getFieldWrapperList());
        assertTrue(a.solveDEqnStep(true));assertTrue(pss.isActive());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void hostedStabilizerIsBoundAndRemainsActive(boolean staggered)throws Exception {
        var a=load();if(!staggered)a.setSolver(new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(a));
        assertTrue(a.initialization());var m=a.getNetwork().getMachine("Bus1-mach1");
        var pss=(AbstractAnnotateController)m.getStabilizer();assertNotNull(pss.getFieldWrapperList());
        for(int i=0;i<20;i++)assertTrue(a.solveDEqnStep(true));
        assertTrue(pss.isActive());assertTrue(Double.isFinite(pss.getOutput(m)));
    }

    private com.interpss.dstab.algo.DynamicSimuAlgorithm load()throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        String base="testData/adpter/psse/v33/SMIB/";
        var a=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst1a_pss2a.dyr").getDynSimuAlgorithm();
        assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(1./240);a.setTotalSimuTimeSec(.1);
        a.setSimuOutputHandler(new StateMonitor());a.setSolver(new StaggeredEulerSolver(a));return a;
    }
}
