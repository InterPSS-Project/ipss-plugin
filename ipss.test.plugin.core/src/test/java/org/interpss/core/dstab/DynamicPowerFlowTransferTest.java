package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EndpointAlignedStateMonitor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.numeric.datatype.LimitType;
import org.junit.jupiter.api.Test;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.aclf.adj.*;
import com.interpss.core.sparse.impl.klu.JKLUSparseEqnComplexImpl;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;

/** Preparation and numerical operator updates preserve the maintained dynamic lifecycle. */
class DynamicPowerFlowTransferTest {
    record Run(List<EndpointAlignedStateMonitor.Frame> frames,List<String> channels,long refills) {}
    private static final class Solver extends org.interpss.dstab.algo.DefaultDynamicSimulationSolver {
        private final boolean reuse;
        Solver(com.interpss.dstab.algo.DynamicSimuAlgorithm algo,boolean reuse) {super(algo);this.reuse=reuse;}
        @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
            ((JKLUSparseEqnComplexImpl)dstabAlgo.getNetwork().getYMatrix()).getJkluSolver().setCscPatternReuseEnabled(reuse);
            return super.networkSolutionStep();
        }
    }
    private static SortedMap<String,Double> states(BaseDStabNetwork<?,?> net) {
        var values=new TreeMap<String,Double>();
        for(var bus:net.getBusList()) {
            String id=bus.getId();values.put(id+"/voltage-real",bus.getVoltage().getReal());
            values.put(id+"/voltage-imaginary",bus.getVoltage().getImaginary());values.put(id+"/frequency",bus.getFreq());
            for(var gen:bus.getContributeGenList())if(gen.getMach()!=null) {
                var machine=gen.getMach();String key=machine.getId();
                values.put(key+"/angle",machine.getAngle());values.put(key+"/speed",machine.getSpeed());
                values.put(key+"/Pe",machine.getPe());values.put(key+"/Pm",machine.getPm());values.put(key+"/Efd",machine.getEfd());
                for(var controller:machine.getControllerList())if(controller instanceof AbstractAnnotateController c)
                    c.getNamedStates().forEach((name,value)->values.put(key+"/"+c.getClass().getName()+"/"+name,value));
            }
        }
        return values;
    }
    static Run run(boolean prepared,boolean cache) throws Exception {
            IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
            String root="testData/adpter/psse/v30/IEEE9Bus/";
            var context=new PSSEMultiFileLoader().loadDStab(root+"ieee9.raw",root+"ieee9_dyn_fullModel_v33.dyr");
            var net=context.getDStabilityNet();var algo=context.getDynSimuAlgorithm();var lf=algo.getAclfAlgorithm();
            var bus=net.getDStabBus("Bus5");
            var shunt=AclfAdjustObjectFactory.createSwitchedShunt(bus);
            shunt.setStatus(true);shunt.setControlStatus(false);shunt.setControlMode(AclfAdjustControlMode.FIXED);
            shunt.setBLimit(new LimitType(.5,-.5));shunt.setBInit(.02);
            var svc=AclfAdjustObjectFactory.createStaticVarCompensator(net.getDStabBus("Bus8"),
                    AclfAdjustControlMode.FIXED,AclfAdjustControlType.POINT_CONTROL).orElseThrow();
            svc.setStatus(true);svc.setControlStatus(false);svc.setBLimit(new LimitType(.5,-.5));svc.setBInit(.01);
            lf.setPreparedNewtonEnabled(prepared);lf.setTolerance(1e-10);lf.getLfAdjAlgo().setApplyAdjustAlgo(false);
            assertTrue(lf.loadflow());assertEquals(prepared,lf.wasPreparedNewtonUsed(),"Positive preparation coverage");
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.005);algo.setTotalSimuTimeSec(.2);
            algo.setOutPutPerSteps(1);
            var solver=new Solver(algo,cache);algo.setSolver(solver);
            solver.setBusFrequencyMode(org.interpss.dstab.algo.DefaultDynamicSimulationSolver.BusFrequencyMode.ALL);
            var monitor=new EndpointAlignedStateMonitor("Bus5");monitor.addBusStdMonitor(new String[]{"Bus5"});
            algo.setSimuOutputHandler(monitor);
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",net,
                    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,Complex.ZERO,null,.05,.05),"TransferFault");
            assertTrue(algo.initialization());assertTrue(net.isDStabNetInitialized());
            assertFalse(com.interpss.core.algo.impl.solver.PreparedPolarJacobian.supports(net));
            var first=(JKLUSparseEqnComplexImpl)net.getYMatrix();int n=net.getNoBus();var initialY=new Complex[n][n];
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)initialY[i][j]=first.getA(i,j);
            var channels=List.copyOf(states(net).keySet());assertTrue(channels.size()>50,"Include controller histories as well as bus/machine outputs");
            monitor.initializeStateCapture(0,()->states(net).values().stream().mapToDouble(Double::doubleValue).toArray());
            for(int tick=0;tick<40;tick++) {
                if(tick==4) {
                    Complex delta=new Complex(0,.01);shunt.setBActual(.03);bus.applyPositiveSequenceShuntAdmittanceChange(delta);
                    first.addToA(delta,bus.getSortNumber(),bus.getSortNumber());net.setYMatrixDirty(true);
                }
                monitor.beginStep(algo.getSimuTime(),.005);assertTrue(algo.solveDEqnStep(true));monitor.endStep(algo.getSimuTime());
            }
            assertEquals(43,monitor.frames().size(),"Keep both event-side samples");
            assertEquals(2,monitor.frames().stream().filter(f->f.kind()==EndpointAlignedStateMonitor.SampleKind.EVENT_PRE).count());
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                var expected=initialY[i][j];if(i==bus.getSortNumber()&&i==j)expected=expected.add(new Complex(0,.01));
                assertEquals(0,net.getYMatrix().getA(i,j).subtract(expected).abs(),1e-10,"Fault clearing preserves physical shunt/cache state");
            }
            return new Run(monitor.frames(),channels,first.getJkluSolver().getCscNumericRefillCount());
    }
    @Test void initializationFaultClearingAndLivePhysicalShuntsMatchTheGenericPath() throws Exception {
        var legacy=run(false,false);var optimized=run(true,true);
        assertEquals(legacy.channels(),optimized.channels());assertEquals(legacy.frames().size(),optimized.frames().size());
        for(int i=0;i<legacy.frames().size();i++) {
            var a=legacy.frames().get(i);var b=optimized.frames().get(i);
            assertEquals(a.time(),b.time(),0);assertEquals(a.solverTime(),b.solverTime(),0);assertEquals(a.kind(),b.kind());
            for(int c=0;c<a.states().length;c++)assertEquals(a.states()[c],b.states()[c],1e-8,"Sample "+i+" "+legacy.channels().get(c));
        }
        assertEquals(0,legacy.refills());assertTrue(optimized.refills()>0,"The dynamic operator must actually use prepared numerical refill");
    }
}
