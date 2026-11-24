package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.runtime.StaggeredBusParticipant;
import com.interpss.dstab.algo.dynamic.ControllerStepStability;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import org.apache.commons.math3.complex.Complex;

/** The automatic stability selector and runtime accept the same local resolution. */
class StaggeredLocalResolutionTest {
    @Test void fastAuxiliaryUsesMoreThan128LocalStepsWithoutExtraPhysicalCommits()throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        String root="testData/adpter/psse/v33/SMIB/";
        var a=new PSSEMultiFileLoader().loadDStab(root+"SMIB_v33.raw",root+"SMIB_v33_genrou.dyr").getDynSimuAlgorithm();
        assertTrue(a.getAclfAlgorithm().loadflow());
        var bus=a.getNetwork().getDStabBus("Bus1");var fast=new FastAuxiliary(bus);
        bus.getDynamicBusDeviceList().add(fast);
        var solver=new StaggeredEulerSolver(a);a.setSolver(solver);a.setSimuStepSec(1./240);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.initialization());
        assertTrue(fast.count>128);assertTrue(fast.count<=ControllerStepStability.MAXIMUM_LOCAL_STEPS);
        fast.target=.2;solver.notifyDiscontinuity();
        int oldLocal=fast.localCommits,oldPhysical=fast.physicalCommits;
        for(int i=0;i<4;i++)assertTrue(a.solveDEqnStep(true));
        assertEquals(oldLocal+4*fast.count,fast.localCommits);
        assertEquals(oldPhysical+4,fast.physicalCommits);
        assertEquals(.2,fast.x,1e-8,"Stable lag reaches its unchanged physical equilibrium");
        assertTrue(fast.isActive());
    }

    private static final class FastAuxiliary extends DynLoadModelImpl implements StaggeredBusParticipant {
        double x,target;int count=1,localCommits,physicalCommits;
        FastAuxiliary(BaseDStabBus<?,?> bus) {
            setId("fast-local");setExtendedDeviceId("fast-local@"+bus.getId());setDStabBus(bus);
            setLoadPercent(0);setLoadFactor(0);setLoadPQ(Complex.ZERO);setInitLoadPQ(Complex.ZERO);
            setEquivY(Complex.ZERO);setNortonCurInj(Complex.ZERO);setCompensateShuntY(Complex.ZERO);setCurrInj2Net(Complex.ZERO);
        }
        @Override public boolean initStates(BaseDStabBus<?,?> bus){x=target=0;localCommits=physicalCommits=0;return true;}
        @Override public boolean initStates(){return initStates(getDStabBus());}
        @Override public boolean initStates(BaseDStabBus<?,?> b,com.interpss.dstab.mach.Machine m){return initStates(b);}
        @Override public boolean initStates(BaseDStabBus<?,?> b,com.interpss.core.net.Network<?,?> n){return initStates(b);}
        @Override public Object getOutputObject(){return Complex.ZERO;}
        @Override public java.util.Hashtable<String,Object> getStates(Object ref){return new java.util.Hashtable<>(java.util.Map.of("BusDeviceId",getExtendedDeviceId()));}
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){assertEquals(0,dt,"No ordinary positive-time fallback");return true;}
        @Override public boolean updateAttributes(boolean changed){return true;}
        @Override public boolean afterStep(double step){return true;}
        @Override public boolean isStaggeredAttachmentIntact(){return getDStabBus()!=null;}
        @Override public boolean isStaggeredOperational(){return true;}
        @Override public void prepareStaggeredIntegration(double step){count=ControllerStepStability.substeps(new double[][]{{-50000}},step);}
        @Override public int staggeredLocalStepCount(){return count;}
        @Override public int staggeredStateCount(){return 1;}
        @Override public void readStaggeredState(double[] values,int offset){values[offset]=x;}
        @Override public void applyStaggeredState(double[] values,int offset,double step){x=values[offset];}
        @Override public void evaluateStaggered(double step,double[] rates,int offset){rates[offset]=50000*(target-x);}
        @Override public void completeStaggeredLocalStep(double step){localCommits++;}
        @Override public void completeStaggeredStep(double step){physicalCommits++;}
    }
}
