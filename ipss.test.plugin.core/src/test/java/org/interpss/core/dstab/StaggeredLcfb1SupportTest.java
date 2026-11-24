package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.controller.gov.lcfb1.Lcfb1PrefController;
import org.junit.jupiter.api.Test;

/** Secondary reference control shares integration without recapturing its own bias. */
class StaggeredLcfb1SupportTest {
    @Test void heldRatesPreserveReferenceAndNativeHistoriesAndRunCoupled() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33.raw").toString(),
                root.resolve("SMIB_v33_genrou_tgov1_lcfb1.dyr").toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new PsseStaggeredEulerSolver(a));a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        var m=a.getNetwork().getMachine("Bus1-mach1");
        var p=m.getDStabBus().getDynamicBusDeviceList().stream()
                .filter(Lcfb1PrefController.class::isInstance).map(Lcfb1PrefController.class::cast).findFirst().orElseThrow();
        double[] x=new double[2],r=new double[2],observed=new double[2];
        double speed=m.getSpeed(),pe=m.getPe(),reference=((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)m.getGovernor()).getRefPoint();
        p.beginStaggeredStep(1./240);p.readStaggeredState(x,0);
        Map<String,Object> history=new TreeMap<>();
        for(var f:Lcfb1PrefController.class.getDeclaredFields())if(f.getName().startsWith("old")||f.getName().equals("governorBaseReference")) {
            f.setAccessible(true);history.put(f.getName(),f.get(p));
        }
        p.evaluateStaggered(1./240,r,0);m.setSpeed(speed+.01);m.setPe(pe+.1);
        p.evaluateStaggered(1./240,observed,0);assertArrayEquals(r,observed,0.);
        p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        for(var f:Lcfb1PrefController.class.getDeclaredFields())if(history.containsKey(f.getName())) {
            f.setAccessible(true);assertEquals(history.get(f.getName()),f.get(p),f.getName());
        }
        p.completeStaggeredStep(1./240);m.setSpeed(speed);m.setPe(pe);
        for(int i=0;i<240;i++)assertTrue(a.solveDEqnStep(true));
        assertEquals(reference,((com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)m.getGovernor()).getRefPoint(),1e-7);
        m.setSpeed(speed-.002);((PsseStaggeredEulerSolver)a.getSolver()).notifyDiscontinuity();
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true));
        assertTrue(p.isStaggeredOperational());p.readStaggeredState(observed,0);
        for(double value:observed)assertTrue(Double.isFinite(value));
        assertTrue(Math.abs(p.getReferenceBias())<=p.getData().maximumReferenceBias());
    }
}
