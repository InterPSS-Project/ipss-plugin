package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.controller.runtime.PreparedStaggeredController;

/** All missing governor families retain their own equations, bounds and committed histories. */
public class StaggeredGovernorSupportTest {
    private static Map<String,Object> committed(Object model)throws Exception {
        var result=new TreeMap<String,Object>();
        for(Class<?> c=model.getClass();c!=null;c=c.getSuperclass())for(var f:c.getDeclaredFields()) {
            String name=f.getName();
            if(name.equals("state")||name.equals("oldState")||name.equals("oldDerivatives")||name.equals("predictor")
                    ||name.startsWith("committed")||name.equals("deadbandMode")||name.equals("speedDeadbandMode")||name.equals("movingPref")) {
                f.setAccessible(true);result.put(name,f.get(model));
            }
        }
        return result;
    }
    @ParameterizedTest @ValueSource(strings={"H6E","HYGOVR1","PIDGOVD","WSHYDD","WSHYGP","IEEEG3"})
    void pureHeldInputRatesPreserveHistoryAndTheCoupledGovernorRemainsActive(String family,@org.junit.jupiter.api.io.TempDir Path dir)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/SMIB");
        Path dyr=root.resolve("SMIB_v33_genrou_"+family.toLowerCase(Locale.ROOT)+(family.equals("H6E")?"_independent":"")+".dyr");
        if(family.startsWith("WSHY")) {
            double[] d={.002,.0003,.08,1.7,.06,.15,4.2,.045,.12,.9,.15,.20,.18,1.10,0,.001,
                    0,.02,.23,.20,.47,.48,.76,.78,1.05,1.08,-.30,.55,1.30,0};
            if(family.equals("WSHYGP")){d[3]=5.5;d[5]=.12;d[6]=8.5;}
            var record=new StringBuilder("\n1 '"+family+"' '1'");for(double value:d)record.append(' ').append(value);
            dyr=dir.resolve("synthetic-hydro.dyr");
            Files.writeString(dyr,Files.readString(root.resolve("SMIB_v33_genrou.dyr"))+record.append(" /\n"));
        }
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33.raw").toString(),dyr.toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization(),family);
        var m=a.getNetwork().getMachine("Bus1-mach1");var g=m.getGovernor();assertTrue(g.isActive());
        var p=assertInstanceOf(PreparedStaggeredController.class,g);assertTrue(p.staggeredLocalStepCount()>=1);
        double speed=m.getSpeed(),power=m.getPe();double[] x=new double[p.staggeredStateCount()],before=new double[x.length],after=new double[x.length];
        p.beginStaggeredStep(1./240);p.readStaggeredState(x,0);var history=committed(g);p.evaluateStaggered(1./240,before,0);
        m.setSpeed(speed+.01);m.setPe(power+.05);p.evaluateStaggered(1./240,after,0);
        assertArrayEquals(before,after,0.,"Local rates retain left speed/power inputs");assertEquals(history,committed(g),"RHS probes cannot commit predictor or gate/deadband histories");
        double[] observed=new double[x.length];p.readStaggeredState(observed,0);assertArrayEquals(x,observed,0.);
        p.completeStaggeredStep(1./240);m.setSpeed(speed);m.setPe(power);
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true),family);
        p.readStaggeredState(observed,0);for(double value:observed)assertTrue(Double.isFinite(value));
        assertTrue(g.isActive());assertTrue(Double.isFinite(m.getPm()));
        m.setSpeed(speed+.002);
        for(int i=0;i<120;i++)assertTrue(a.solveDEqnStep(true),family+" speed disturbance");
        assertTrue(Double.isFinite(m.getPm()));
    }
}
