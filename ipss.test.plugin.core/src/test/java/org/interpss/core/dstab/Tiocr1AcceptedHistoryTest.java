package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.relay.Tiocr1Data;
import org.interpss.dstab.relay.Tiocr1RelayModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Accepted relay history is independent of trial integration and observation. */
public class Tiocr1AcceptedHistoryTest {
    private static final Path CASE=Path.of("testData/adpter/psse/v33/HspModels");

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void movingElementUsesLeftCurrentWithoutAdvancingTrialHistory(boolean staggered)throws Exception {
        var a=setup(staggered);var branch=a.getNetwork().getBranchList().getFirst();
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,1,1.5,1,2,1,3,1,5,1,.01,0});
        var relay=new Tiocr1RelayModel(branch,true,2,false,a.getNetwork().getDStabBus("Bus2"),null,
                new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(a.initialization());var before=relay.getIntegrationHistories();
        assertTrue(relay.nextStep(.1,DynamicSimuMethod.MODIFIED_EULER,0));
        branch.getFromAclfBus().setVoltage(Complex.ZERO);branch.getToAclfBus().setVoltage(Complex.ZERO);
        assertTrue(relay.nextStep(.1,DynamicSimuMethod.MODIFIED_EULER,1));
        assertEquals(before,relay.getIntegrationHistories());
        assertEquals(0,relay.currentMagnitude(),0.);
        assertTrue(relay.afterStep(.1));assertEquals(.1,relay.getPosition(),1e-14);
        assertTrue(relay.nextStep(.1,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(relay.afterStep(.1));assertEquals(0,relay.getPosition(),0.);
    }

    @ParameterizedTest
    @CsvSource({"false,false","false,true","true,false","true,true"})
    void zeroDurationCannotResetMotionAndCurrentMatchesBothTerminals(boolean staggered,boolean fromEnd) throws Exception {
        var a=setup(staggered);
        var branch=a.getNetwork().getBranchList().getFirst();
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,1,1.5,1,2,1,3,1,5,1,.01,0});
        var relay=new Tiocr1RelayModel(branch,fromEnd,2,false,a.getNetwork().getDStabBus("Bus2"),null,
                new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(a.initialization());
        var vf=branch.getFromAclfBus().getVoltage();var vt=branch.getToAclfBus().getVoltage();
        for(int i=0;i<30;i++) {
            branch.getFromAclfBus().setVoltage(new Complex(.2+i*.04,-.3+i*.013));
            branch.getToAclfBus().setVoltage(new Complex(1.1-i*.02,.2-i*.021));
            var f=branch.getFromAclfBus().getVoltage();var t=branch.getToAclfBus().getVoltage();
            double expected=(fromEnd?branch.yff().multiply(f).add(branch.yft().multiply(t))
                    :branch.ytf().multiply(f).add(branch.ytt().multiply(t))).abs();
            assertEquals(expected,relay.currentMagnitude(),2e-14*Math.max(1,expected));
        }
        branch.getFromAclfBus().setVoltage(vf);branch.getToAclfBus().setVoltage(vt);
        assertTrue(relay.afterStep(.1));assertTrue(relay.getPosition()>0 && relay.getPosition()<1);
        branch.setStatus(false);var history=relay.getIntegrationHistories();
        assertTrue(relay.afterStep(0));assertEquals(history,relay.getIntegrationHistories());
        assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));
        assertEquals(history,relay.getIntegrationHistories());
        assertFalse(relay.afterStep(Double.NaN));assertFalse(relay.afterStep(-1));
        assertEquals(history,relay.getIntegrationHistories());
        assertTrue(relay.getIntegrationStates().isEmpty());
        assertSame(relay.getStateDerivativeSnapshot(),relay.getStateDerivativeSnapshot());
        assertEquals(history,relay.getNamedStates());assertEquals(0,relay.currentMagnitude());
        assertTrue(relay.afterStep(.001));assertEquals(0,relay.getPosition());
        branch.setStatus(true);
    }

    @ParameterizedTest
    @CsvSource({"false,false","false,true","true,false","true,true"})
    void coupledFaultAdvancesAcceptedProtectionUnderBothMethods(boolean staggered,boolean fromEnd) throws Exception {
        var a=setup(staggered);var net=a.getNetwork();var branch=net.getBranchList().getFirst();
        var curve=new Tiocr1Data(new double[]{.4,.1,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,0});
        var relay=new Tiocr1RelayModel(branch,fromEnd,2,false,net.getDStabBus("Bus2"),null,
                new com.interpss.core.aclf.AclfBranch[3],curve);
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.05,.05),"Fault");
        assertTrue(a.initialization());
        while(a.getSimuTime()<.12-.00025) {
            assertTrue(a.solveDEqnStep(true));var before=relay.getIntegrationHistories();
            relay.getStates(null);relay.currentMagnitude();relay.getStateDerivativeSnapshot();
            assertEquals(before,relay.getIntegrationHistories());
        }
        assertTrue(relay.isOperated());assertTrue(branch.isActive(),"Monitoring alone cannot open a branch");
        assertEquals(0,relay.getShedFraction());
        assertEquals(0,relay.getNetworkActionVersion(),"Monitoring cannot request a network-history rebase");
    }

    @ParameterizedTest
    @CsvSource({"false,CONST_P","true,CONST_P","false,CONST_I","true,CONST_I","false,CONST_Z","true,CONST_Z"})
    void remoteShedCurrentSurvivesRebuildAndRefreshesWithVoltage(boolean staggered,
            com.interpss.dstab.StaticLoadModel model) throws Exception {
        var a=setup(staggered);var net=a.getNetwork();var bus=net.getDStabBus("Bus2");
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(model);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,.001,1.5,.001,2,.001,3,.001,5,.001,0,.25});
        var relay=new Tiocr1RelayModel(net.getBranchList().getFirst(),true,2,true,bus,"1",
                new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(a.initialization());
        var port=bus.getDynamicBusDeviceList().stream()
                .filter(d->d.getId().contains(relay.getId()+"_LOAD")).findFirst().orElseThrow();
        var initialPower=bus.getContributeLoad("1").getLoad(bus.getVoltageMag());
        double v0=bus.getInitVoltMag();int row=bus.getSortNumber();
        var before=net.getYMatrix().getA(row,row);
        assertTrue(relay.afterStep(.002));assertEquals(.25,relay.getShedFraction(),1e-12);
        assertTrue(net.getBranchList().getFirst().isActive());
        assertEquals(1.,relay.getPosition(),0.,"Remote shedding leaves the monitored relay latched");
        assertEquals(1,relay.getNetworkActionVersion());
        var delta=net.getYMatrix().getA(row,row).subtract(before);
        var accepted=net.getYMatrix().getA(row,row);
        for(int rebuild=0;rebuild<3;rebuild++) {
            if(rebuild>0)net.formYMatrix4DStab();
            assertTrue(port.updateAttributes(true));assertTrue(port.updateAttributes(true));
            assertEquals(0,accepted.subtract(net.getYMatrix().getA(row,row)).abs(),1e-12);
            for(double magnitude:new double[]{.85,1.05,.95}) {
                var voltage=new Complex(magnitude*Math.cos(.13),magnitude*Math.sin(.13));bus.setVoltage(voltage);
                double ratio=magnitude/v0;
                double active=model==com.interpss.dstab.StaticLoadModel.CONST_P?1:
                        model==com.interpss.dstab.StaticLoadModel.CONST_I?ratio:ratio*ratio;
                var removed=new Complex(initialPower.getReal()*active,initialPower.getImaginary()*ratio*ratio).multiply(.25);
                var measured=((Complex)port.getOutputObject()).subtract(delta.multiply(voltage));
                assertEquals(0,removed.subtract(voltage.multiply(measured.conjugate())).abs(),2e-12,
                        "Matrix removal plus live correction must remove the requested physical power");
                assertEquals(1,relay.getNetworkActionVersion());
            }
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void targetsCannotCrossNetworksAndReparentingCannotAcceptProtection(boolean staggered)throws Exception {
        var a=setup(staggered);var other=setup(staggered);var net=a.getNetwork();var branch=net.getBranchList().getFirst();
        var foreign=other.getNetwork();var host=net.getDStabBus("Bus2");int count=host.getDynamicBusDeviceList().size();
        int ownerCount=net.getDStabBus(branch.getFromBus().getId()).getDynamicBusDeviceList().size();
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,.001,1.5,.001,2,.001,3,.001,5,.001,0,.25});
        assertThrows(IllegalArgumentException.class,()->new Tiocr1RelayModel(branch,true,2,true,foreign.getDStabBus("Bus2"),"1",
                new com.interpss.core.aclf.AclfBranch[3],curve));
        assertThrows(IllegalArgumentException.class,()->new Tiocr1RelayModel(branch,true,2,true,host,null,
                new com.interpss.core.aclf.AclfBranch[]{branch,foreign.getBranchList().getFirst(),null},curve));
        assertTrue(branch.isActive());assertTrue(foreign.getBranchList().getFirst().isActive());
        assertEquals(ownerCount,net.getDStabBus(branch.getFromBus().getId()).getDynamicBusDeviceList().size());
        assertEquals(count,host.getDynamicBusDeviceList().size(),"Rejected targets must not attach partial relay ports");
        var relay=new Tiocr1RelayModel(branch,true,2,true,host,null,new com.interpss.core.aclf.AclfBranch[]{branch,null,null},curve);
        assertTrue(a.initialization());var original=relay.getDStabBus();var history=relay.getIntegrationHistories();
        relay.setDStabBus(foreign.getDStabBus("Bus1"));
        assertFalse(relay.initStates(relay.getDStabBus()));assertFalse(relay.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        assertFalse(relay.afterStep(.01));assertFalse(relay.updateAttributes(true));
        assertEquals(history,relay.getIntegrationHistories());assertTrue(branch.isActive());assertEquals(0,relay.getNetworkActionVersion());
        relay.setDStabBus(original);assertTrue(relay.afterStep(.01));assertFalse(branch.isActive());assertEquals(1,relay.getNetworkActionVersion());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void removedLaterTargetRejectsEntireAcceptedAction(boolean staggered)throws Exception {
        var a=setup(staggered);var net=a.getNetwork();var first=net.getBranchList().get(0);var second=net.getBranchList().get(1);
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,.001,1.5,.001,2,.001,3,.001,5,.001,0,.25});
        var relay=new Tiocr1RelayModel(first,true,2,true,net.getDStabBus("Bus2"),"1",
                new com.interpss.core.aclf.AclfBranch[]{first,second,null},curve);
        assertTrue(a.initialization());var history=relay.getIntegrationHistories();
        assertTrue(net.removeBranch(second.getId()));
        var matrix=net.getYMatrix().getA(first.getFromBus().getSortNumber(),first.getToBus().getSortNumber());
        assertFalse(relay.afterStep(.01));assertFalse(relay.initStates(relay.getDStabBus()));
        assertTrue(first.isActive());assertEquals(history,relay.getIntegrationHistories());
        assertEquals(0,relay.getNetworkActionVersion());assertEquals(0,relay.getShedFraction());
        assertEquals(matrix,net.getYMatrix().getA(first.getFromBus().getSortNumber(),first.getToBus().getSortNumber()));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void reinitializedDynamicTargetDoesNotRetainStaticSheddingStamp(boolean staggered)throws Exception {
        var a=setup(staggered);var net=a.getNetwork();var bus=net.getDStabBus("Bus2");
        var curve=new Tiocr1Data(new double[]{.01,0,1.1,.001,1.5,.001,2,.001,3,.001,5,.001,0,.25});
        var relay=new Tiocr1RelayModel(net.getBranchList().getFirst(),true,2,true,bus,"1",
                new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(a.initialization());
        var port=bus.getDynamicBusDeviceList().stream()
                .filter(d->d.getId().contains(relay.getId()+"_LOAD")).findFirst().orElseThrow();
        // A fresh preparation can replace a static source with a dynamic load.
        var load=new org.interpss.dstab.dynLoad.impl.IeelLoadModel("IEEL",bus,bus.getContributeLoad("1"),
                new org.interpss.dstab.dynLoad.IeelLoadData(1,0,0,1,0,0,0,0,1,0,0,2,0,0));
        assertTrue(load.initStates());assertTrue(port.initStates(bus));
        var originalPower=load.effectivePower();int row=bus.getSortNumber();
        var originalStamp=net.getYMatrix().getA(row,row);
        assertTrue(relay.afterStep(.002));assertEquals(.25,relay.getShedFraction(),0.);
        assertEquals(0.,load.effectivePower().subtract(originalPower.multiply(.75)).abs(),1e-12);
        assertEquals(originalStamp,net.getYMatrix().getA(row,row),
                "The dynamic current port owns shedding; no former static stamp may also be removed");
        bus.setVoltage(bus.getVoltage().multiply(.8));
        assertEquals(Complex.ZERO,port.getOutputObject(),"No stale static current correction");
    }

    private static com.interpss.dstab.algo.DynamicSimuAlgorithm setup(boolean staggered) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString(),CASE.resolve("tiocr1-0.dyr").toString());
        var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.12);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        return a;
    }
}
