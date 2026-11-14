package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.relay.*;
import com.interpss.dstab.relay.model.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.core.aclf.AclfLoadCode;

/** Typed whole-bus actions keep owned composite demand separate from static residuals. */
public class CollapsedWildcardLoadRelayTest {
    private static final Complex OUTSIDE=new Complex(.2,.04), SHUNT=new Complex(.002,.003);
    @ParameterizedTest @ValueSource(booleans={false,true})
    void partialActionsPreserveAllocatedPowerAndVoltageLaw(boolean staggered)throws Exception {
        checkActions(staggered,false);
    }
    @org.junit.jupiter.api.Test void stagedRelaySeparatesOwnedAndIndependentShunt()throws Exception {
        checkActions(false,true);
    }
    private void checkActions(boolean staggered,boolean shedShunt)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve("cmldblu1-electronic.dyr").toString());
        var a=context.getDynSimuAlgorithm();var net=a.getNetwork();var bus=net.getDStabBus("Bus2");
        var original=(Cmldblu1Model)bus.getInfoOnlyDynModel();var constants=original.getLegacyConstants();
        constants[2]=constants[3]=constants[5]=constants[8]=0;
        var model=new Cmldblu1Model("collapsed",bus,original.getTargetLoadIds(),new Complex(bus.getLoadP(),bus.getLoadQ()),constants);
        var outside=DStabObjectFactory.createDStabLoad("outside");outside.setCode(AclfLoadCode.CONST_P);
        outside.setLoadCP(OUTSIDE);outside.setStatus(true);bus.getContributeLoadList().add(outside);bus.setShuntY(SHUNT);
        assertTrue(bus.initContributeLoad(false));assertTrue(a.getAclfAlgorithm().loadflow());
        var gross=bus.calNetLoadResults();assertTrue(gross.getReal()>OUTSIDE.getReal());
        var curve=new Tiocr1Data(new double[]{.00001,0,1,.001,2,.001,3,.001,4,.001,5,.001,0,.25});
        var branch=net.getBranchList().getFirst();
        var staged=shedShunt?new Lvs3blRelayModel(bus,"*",new StagedLoadSheddingRelayData(List.of(
                new LoadSheddingStage(2,.01,0,.25),new LoadSheddingStage(2,.02,0,.25),
                new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),
                new LoadSheddingStage(0,0,0,0)),true),null,0,null,0):null;
        var relays=shedShunt?new Tiocr1RelayModel[0]:new Tiocr1RelayModel[]{
                new Tiocr1RelayModel(branch,true,1,true,bus,"*",new com.interpss.core.aclf.AclfBranch[3],curve),
                new Tiocr1RelayModel(branch,true,2,true,bus,"#",new com.interpss.core.aclf.AclfBranch[3],curve)};
        var ports=bus.getDynamicBusDeviceList().stream().filter(AbstractStagedLoadSheddingRelayModel.class::isInstance)
                .map(AbstractStagedLoadSheddingRelayModel.class::cast).toList();
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(StaticLoadModel.CONST_I);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.01);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a));
        assertTrue(a.initialization());assertSame(bus,model.getLoadBus());
        assertEquals(0,model.getCollapsedParentAllocation().staticPower().subtract(OUTSIDE).abs(),1e-12);
        assertEquals(SHUNT,model.getCollapsedParentAllocation().shunt());
        var allocated=Complex.ZERO;
        for(var leaf:bus.getDynLoadModelList())if(model.ownsCapacityLoad(leaf))allocated=allocated.add(leaf.getInitLoadPQ());
        assertEquals(0,bus.calNetLoadResults().subtract(OUTSIDE).abs(),1e-10);
        assertEquals(0,new Complex(bus.getLoadP(),bus.getLoadQ()).subtract(allocated.add(OUTSIDE)).abs(),1e-10);
        int row=bus.getSortNumber();double v0=bus.getInitVoltMag();var initial=bus.getVoltage();
        var fixedCompositeShunt=new Complex(0,constants[1]*model.getDistEquivalent().getMva()/net.getBaseMva());
        var ownedCompensation=bus.getShuntY().subtract(SHUNT).subtract(fixedCompositeShunt);
        var before=net.getYMatrix().getA(row,row);
        if(!shedShunt) {
        outside.setLoadCP(OUTSIDE.multiply(2));
        assertFalse(relays[0].afterStep(.01));assertEquals(0,relays[0].getPosition(),0);
        assertEquals(1,model.getRemainingCapacity(),0);assertEquals(before,net.getYMatrix().getA(row,row));
        outside.setLoadCP(OUTSIDE);
        var foreign=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl(bus,"unattributed");
        assertFalse(ports.getFirst().initStates(bus),"Unprepared external dynamic remainder is unsupported");
        assertFalse(relays[0].afterStep(.01));assertEquals(1,model.getRemainingCapacity(),0);
        bus.getDynLoadModelList().remove(foreign);bus.getDynamicBusDeviceList().remove(foreign);
        assertTrue(ports.getFirst().initStates(bus));
        }
        for(int stage=0;stage<2;stage++) {
            assertTrue(shedShunt?staged.afterStep(.01):relays[stage].afterStep(.01));double shed=(stage+1)*.25;
            assertEquals(1-shed,model.getRemainingCapacity(),1e-12);assertEquals(shed,shedShunt?staged.getShedFraction():relays[0].getShedFraction()+relays[1].getShedFraction(),0);
            bus.setVoltage(initial.multiply(.8));
            var expected=new Complex(OUTSIDE.getReal()*.8,OUTSIDE.getImaginary()*.64).divide(bus.getVoltage()).conjugate()
                    .subtract(OUTSIDE.conjugate().multiply(bus.getVoltage()).divide(v0*v0)).multiply(shed);
            assertEquals(0,ports.stream().map(port -> (Complex)port.getOutputObject()).reduce(Complex.ZERO,Complex::add).subtract(expected).abs(),1e-12);
            bus.setVoltage(initial);
            assertEquals(0,bus.getShuntY().subtract(SHUNT.add(fixedCompositeShunt).add(ownedCompensation.multiply(1-shed))).abs(),1e-12,
                    "Owned compensation scales once; fixed and independent shunts remain intact");
            var diagonal=net.getYMatrix().getA(row,row);net.formYMatrix4DStab();
            var rawDiagonal=net.getYMatrix().getA(row,row);
            AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(net);
            var ledger=OUTSIDE.conjugate().divide(v0*v0).add(shedShunt?SHUNT:Complex.ZERO).multiply(-shed);
            assertEquals(0,net.getYMatrix().getA(row,row).subtract(rawDiagonal).subtract(ledger).abs(),1e-10);
            assertEquals(0,net.getYMatrix().getA(row,row).subtract(diagonal).abs(),1e-10);
            AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(net);
            assertEquals(0,net.getYMatrix().getA(row,row).subtract(diagonal).abs(),1e-10);
        }
        for(int i=0;i<20;i++)assertTrue(a.solveDEqnStep(true));
        assertTrue(bus.isActive());assertTrue(outside.isActive());assertEquals(OUTSIDE,outside.getLoadCP());
    }
}
