package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;
import com.interpss.dstab.mach.Machine;

/** Existing device RHS and predictor histories remain the owners of physical equations. */
public class ArrayExciterStaggeredContractTest {
    private static Field field(Object o,String name)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static Method rhs(Object o)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod("derivatives",double[].class,double[].class,Machine.class);m.setAccessible(true);return m;}catch(NoSuchMethodException absent){}
        throw new NoSuchMethodException("derivatives");
    }
    @ParameterizedTest
    @ValueSource(strings={"ST1C","ESAC5A","AC5C","DC3A","DC4B","DC4C","ESAC4A","ESST2A","EXAC4","ESST3A","IEEET4","IEEEX1","REXSYS","ST4C","ST5B","ST6B","ST6C","DC1C","DC2C","IEEEX2","EXDC2","EXST2","ST5C"})
    void existingRhsCoordinatesAndPredictorHistoryRemainCoherent(String family)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path root=Path.of("testData/adpter/psse/v33/SMIB");
        var c=new PSSEMultiFileLoader().loadDStab(root.resolve("SMIB_v33.raw").toString(),
                root.resolve("SMIB_v33_genrou_"+family.toLowerCase(Locale.ROOT)+".dyr").toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization(),family);
        var m=a.getNetwork().getMachine("Bus1-mach1");var e=m.getExciter();assertTrue(e.isActive());
        var p=assertInstanceOf(StaggeredEulerParticipant.class,e);
        double[] x=((double[])field(e,"state").get(e)).clone();assertEquals(x.length,p.staggeredStateCount());
        double[] old=((double[])field(e,"oldDerivative").get(e)).clone(),trial=((double[])field(e,"trial").get(e)).clone();
        Object active=field(e,"active").get(e);double[] expected=new double[x.length];rhs(e).invoke(e,x.clone(),expected,m);
        double[] rates=new double[x.length+2];Arrays.fill(rates,123.);p.evaluateStaggered(1./240,rates,1);p.evaluateStaggered(1./240,rates,1);
        assertArrayEquals(expected,Arrays.copyOfRange(rates,1,1+x.length),0.);assertEquals(123.,rates[0]);assertEquals(123.,rates[rates.length-1]);
        assertArrayEquals(x,(double[])field(e,"state").get(e),0.);assertArrayEquals(old,(double[])field(e,"oldDerivative").get(e),0.);
        assertArrayEquals(trial,(double[])field(e,"trial").get(e),0.);assertSame(active,field(e,"active").get(e));
        double[] read=new double[x.length];p.readStaggeredState(read,0);assertArrayEquals(x,read,0.);
        p.applyStaggeredState(x,0,1./240);p.readStaggeredState(read,0);assertArrayEquals(x,read,1e-12,"Feasible initialized coordinates remain feasible");
        for(int i=0;i<24;i++)assertTrue(a.solveDEqnStep(true),family);
        p.readStaggeredState(read,0);for(double value:read)assertTrue(Double.isFinite(value));assertTrue(Double.isFinite(m.getEfd()));
    }
}
