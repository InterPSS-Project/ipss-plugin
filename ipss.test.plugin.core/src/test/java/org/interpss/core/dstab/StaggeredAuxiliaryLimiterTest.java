package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Locale;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.controller.runtime.StaggeredBusParticipant;

/** Bus auxiliaries retain physical input sampling and host ownership independently of load lists. */
public class StaggeredAuxiliaryLimiterTest {
    @ParameterizedTest @ValueSource(strings={"OEL2CU1","UEL2CU1","OEL3C","UEL1"})
    void heldInputsAndStatesRemainPureBeforeCoupledQuarterCycleStepping(String family,@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/SMIB");
        Path dyr=root.resolve("SMIB_v33_genrou_st1c_"+family.toLowerCase(Locale.ROOT)+".dyr");
        if(family.equals("OEL3C")) {
            // A synthetic coupled profile explicitly selects the compatible summation port.
            String entered=java.nio.file.Files.readString(root.resolve("SMIB_v33_genrou_st6c.dyr"));
            assertTrue(entered.contains("'ST6C' 1 4 2 0 1"));
            entered=entered.replace("'ST6C' 1 4 2 0 1","'ST6C' 1 1 2 0 1");
            dyr=directory.resolve("st6c-oel3c.dyr");
            java.nio.file.Files.writeString(dyr,entered+"\n1 'OEL3C' '1' 1 1 4.47 .3333 .02 .1 .1 1 0 -1 0 -1 /\n");
        }
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33.raw").toString(),dyr.toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        var m=a.getNetwork().getMachine("Bus1-mach1");var bus=m.getDStabBus();
        var p=bus.getDynamicBusDeviceList().stream().filter(StaggeredBusParticipant.class::isInstance)
                .map(StaggeredBusParticipant.class::cast).findFirst().orElseThrow();
        assertTrue(p.isStaggeredAttachmentIntact());assertTrue(p.isStaggeredOperational());assertTrue(p.staggeredLocalStepCount()>=1);
        double[] x=new double[p.staggeredStateCount()],before=new double[x.length],after=new double[x.length];p.readStaggeredState(x,0);
        double[] original=x.clone();
        if(p instanceof com.interpss.dstab.controller.uel.uel1.Uel1UnderExcitationLimiter uel) {
            // Exercise a feasible unsaturated PI point, so input freshness cannot
            // be hidden by the fixture's inactive lower-output clamp.
            var d=uel.getData();x[0]=.5*(d.vuimax()+d.vuimin())-d.kul()*uel.getError();
            p.applyStaggeredState(x,0,1./240);p.readStaggeredState(x,0);
        }

        var voltage=bus.getVoltage();double field=m.getEfd();
        p.beginStaggeredStep(1./240);p.evaluateStaggered(1./240,before,0);
        bus.setVoltage(voltage.multiply(.9));m.setEfd(field+.2);p.evaluateStaggered(1./240,after,0);
        assertArrayEquals(before,after,0.,"Local rates must retain the solved left-endpoint inputs");
        double[] observed=new double[x.length];p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.,"Rate probes cannot integrate limiter states or timers");
        p.completeStaggeredStep(1./240);p.evaluateStaggered(1./240,after,0);
        boolean changed=false;for(int i=0;i<before.length;i++)changed|=Math.abs(before[i]-after[i])>1e-10;
        assertTrue(changed,"After completing the interval, current physical inputs must be observed");
        bus.setVoltage(voltage);m.setEfd(field);p.applyStaggeredState(original,0,1./240);
        for(int i=0;i<24;i++)assertTrue(a.solveDEqnStep(true));
        p.readStaggeredState(observed,0);for(double value:observed)assertTrue(Double.isFinite(value));
        m.getParentGen().setStatus(false);assertFalse(p.isStaggeredOperational());assertTrue(p.isStaggeredAttachmentIntact());
        m.getParentGen().setStatus(true);
    }
}
