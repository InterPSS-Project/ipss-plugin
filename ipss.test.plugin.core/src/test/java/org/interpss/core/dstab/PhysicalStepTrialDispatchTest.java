package org.interpss.core.dstab;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.runtime.PhysicalStepTrial;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import java.util.Hashtable;
/** Prepared dispatch preserves primary failure and cleans every pending owner. */
public class PhysicalStepTrialDispatchTest extends org.interpss.CorePluginTestSetup {
    static class Probe extends DynamicBusDeviceImpl implements PhysicalStepTrial {
        boolean open,failBegin,failPublish,failDiscard;int begins,discards,commits;
        Probe(String id){setId(id);setExtendedDeviceId(id);setDeviceType(com.interpss.dstab.device.DynamicBusDeviceType.DYNAMIC_LOAD);}
        public boolean initStates(BaseDStabBus<?,?> b){return true;}
        public boolean nextStep(double h,DynamicSimuMethod m,int stage){return true;}
        public boolean afterStep(double h){return true;} public boolean updateAttributes(boolean b){return true;}
        public Object getOutputObject(){return org.apache.commons.math3.complex.Complex.ZERO;}
        public Hashtable<String,Object> getStates(Object r){var v=new Hashtable<String,Object>();v.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());return v;}
        public void beginPhysicalStepTrial(double t,double h){open=true;begins++;if(failBegin)throw new IllegalStateException("begin");}
        public void publishPhysicalStepTrial(){if(failPublish)throw new IllegalStateException("publish");}
        public void validatePhysicalStepAcceptance(){} public void acceptPhysicalStepTrial(){open=false;commits++;}
        public void discardPhysicalStepTrial(){if(!open)return;open=false;discards++;if(failDiscard)throw new IllegalStateException("discard");}
    }
    static class Solver extends org.interpss.dstab.algo.DefaultDynamicSimulationSolver {
        Solver(com.interpss.dstab.algo.DynamicSimuAlgorithm a){super(a);}
        void begin(){beginPhysicalStepTrials(0,.0005);}void publish(){publishPhysicalStepTrials();}void discard(Throwable e){discardPhysicalStepTrials(e);}
    }
    @Test void partialBeginPublishAndReinitializationCleanAllPreparedOwners() throws Exception {
        var p=Svsmo3t2DeadbandRelockTest.reference().resolve("enabled-a");
        var c=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(p.resolve("smib.raw").toString(),p.resolve("smib.dyr").toString());
        var a=c.getDynSimuAlgorithm();var b=c.getDStabilityNet().getDStabBus("Bus3");
        var first=new Probe("probe1");var last=new Probe("probe2");
        first.setDStabBus(b);last.setDStabBus(b);b.getDynamicBusDeviceList().add(first);b.getDynamicBusDeviceList().add(last);
        a.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        var solver=new Solver(a);a.setSolver(solver);assertTrue(a.initialization());
        last.failBegin=true;var failure=assertThrows(IllegalStateException.class,solver::begin);last.failDiscard=true;solver.discard(failure);
        assertEquals(1,failure.getSuppressed().length);assertFalse(first.open);assertFalse(last.open);assertEquals(1,first.discards);assertEquals(1,last.discards);
        solver.discard(failure);assertEquals(1,first.discards);
        last.failBegin=false;last.failDiscard=false;last.failPublish=true;solver.begin();var publication=assertThrows(IllegalStateException.class,solver::publish);solver.discard(publication);assertFalse(first.open);assertFalse(last.open);
        solver.begin();b.getDynamicBusDeviceList().remove(last);assertTrue(a.initialization());assertFalse(last.open);int oldBegins=last.begins;
        solver.begin();assertEquals(oldBegins,last.begins);solver.discard(null);
    }
}
